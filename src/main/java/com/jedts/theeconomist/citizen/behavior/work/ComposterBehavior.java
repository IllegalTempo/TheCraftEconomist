package com.jedts.theeconomist.citizen.behavior.work;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.action.CitizenItemActionResult;
import com.jedts.theeconomist.citizen.action.CraftItemAction;
import com.jedts.theeconomist.citizen.action.PlaceItemAction;
import com.jedts.theeconomist.citizen.action.resource.CitizenResourceProvider;
import com.jedts.theeconomist.citizen.action.resource.LogResourceProvider;
import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorStopReason;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.RoutineDecisionRules;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetFinder;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import com.jedts.theeconomist.citizen.farm.NaturalWater;
import com.jedts.theeconomist.citizen.house.HouseholdStorage;
import com.jedts.theeconomist.citizen.house.HouseholdWorkClaims;
import com.jedts.theeconomist.citizen.info.CitizenCraftingPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Supplies one household composter through the shared find/craft/place actions. */
public final class ComposterBehavior implements CitizenBehavior {
    private static final String REQUEST_ID = "farm.composter";
    private static final String MEMORY_ID = "farm.composter.location";
    private static final int FARM_SITE_SEARCH_RADIUS = 8;
    private static final List<CitizenResourceProvider> PROVIDERS = List.of(new LogResourceProvider());

    private final CitizenTargetMemoryStore memory = new CitizenTargetMemoryStore();
    private final CraftItemAction craftAction = new CraftItemAction(Items.COMPOSTER, 1, PROVIDERS);
    private final PlaceItemAction placeAction = new PlaceItemAction(Items.COMPOSTER, 1, new ComposterSiteFinder());
    private boolean hasClaim;
    private String status = "Preparing household composter";
    private long nextExistingScanTick;
    private long nextActionTick;
    private boolean cachedExistingComposter;
    private boolean composterReadyForPlacement;

    @Override public String id() { return "composter"; }

    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var citizen = context.citizen();
        boolean daytime = !com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime());
        boolean household = context.householdId().isPresent() && context.home().isPresent();
        boolean storage = HouseholdStorage.resolve(context).isPresent();
        boolean existing = existingComposterRemembered(context);
        if (context.gameTime() >= nextExistingScanTick) {
            cachedExistingComposter = hasComposterNearHome(context);
            nextExistingScanTick = context.gameTime() + 40;
        }
        return RoutineDecisionRules.preparingComposter(citizen.identity().lifeStage().canWork(), daytime, household,
                storage, existing || cachedExistingComposter, citizen.stats().ambition(), citizen.skills().farming(),
                CitizenRuntime.decisionScoring());
    }

    @Override public boolean canStart(CitizenBehaviorContext context) { return evaluate(context).eligible(); }

    @Override public void tick(CitizenBehaviorContext context) {
        if (checkRememberedComposter(context)) return;
        if (context.householdId().isEmpty()) {
            status = "No household home chest";
            return;
        }
        UUID householdId = context.householdId().orElseThrow();
        if (!HouseholdWorkClaims.tryClaim(householdId, REQUEST_ID, context.citizen().getUUID(), context.gameTime())) {
            if (context.gameTime() < nextActionTick) return;
            nextActionTick = context.gameTime() + 5;
            if (composterReadyForPlacement) {
                status = "Waiting to place family composter";
                return;
            }
            status = "Gathering for family composter: " + craftAction.tickGatherOnly(context).status();
            return;
        }
        hasClaim = true;
        if (context.gameTime() >= nextExistingScanTick) {
            cachedExistingComposter = hasComposterNearHome(context);
            nextExistingScanTick = context.gameTime() + 40;
        }
        if (cachedExistingComposter) {
            memory.get(context, MEMORY_ID).ifPresent(pos -> memory.forget(context, MEMORY_ID));
            composterReadyForPlacement = false;
            status = "A composter is already near the farm";
            release(context);
            return;
        }

        if (context.gameTime() < nextActionTick) return;
        nextActionTick = context.gameTime() + 5;
        if (!composterReadyForPlacement) {
            CitizenItemActionResult crafted = craftAction.tick(context);
            if (crafted.state() == CitizenItemActionResult.State.UNAVAILABLE
                    || crafted.state() == CitizenItemActionResult.State.FAILED) {
                status = crafted.status();
                release(context);
                return;
            }
            if (crafted.state() != CitizenItemActionResult.State.COMPLETE) {
                status = crafted.status();
                return;
            }
            // Placement may temporarily withdraw the output from the shared chest.
            // Do not re-enter CraftItemAction and store that carried composter again.
            composterReadyForPlacement = true;
        }

        CitizenItemActionResult placed = placeAction.tick(context);
        status = placed.status();
        if (placed.state() == CitizenItemActionResult.State.COMPLETE) {
            placeAction.targetPosition().ifPresent(pos -> memory.remember(context, MEMORY_ID, pos));
            composterReadyForPlacement = false;
            release(context);
        } else if (placed.state() == CitizenItemActionResult.State.UNAVAILABLE
                || placed.state() == CitizenItemActionResult.State.FAILED) {
            release(context);
        }
    }

    @Override public void stop(CitizenBehaviorContext context, CitizenBehaviorStopReason reason) {
        craftAction.stop(context);
        placeAction.stop(context);
        release(context);
    }

    @Override public String status(CitizenBehaviorContext context) { return status; }
    @Override public java.util.Optional<CitizenCraftingPreview> craftingPreview() { return craftAction.craftingPreview(); }

    private boolean checkRememberedComposter(CitizenBehaviorContext context) {
        BlockPos remembered = memory.get(context, MEMORY_ID).orElse(null);
        if (remembered == null) return false;
        BlockPos home = context.home().orElse(context.citizen().blockPosition());
        long dx = (long) remembered.getX() - home.getX();
        long dz = (long) remembered.getZ() - home.getZ();
        if (dx * dx + dz * dz > 256L * 256L) {
            memory.forget(context, MEMORY_ID);
            return false;
        }
        if (!context.level().hasChunkAt(remembered)) {
            double moveX = context.citizen().getX() - (remembered.getX() + 0.5);
            double moveZ = context.citizen().getZ() - (remembered.getZ() + 0.5);
            if (moveX * moveX + moveZ * moveZ > 4.0
                    && (!context.navigation().isInProgress() || context.navigation().isDone())
                    && !context.navigation().moveTo(remembered.getX() + 0.5, remembered.getY(), remembered.getZ() + 0.5, 1.0)) {
                memory.forget(context, MEMORY_ID);
                context.navigation().stop();
                return false;
            }
            status = "Checking remembered household composter";
            return true;
        }
        if (context.level().getBlockState(remembered).is(Blocks.COMPOSTER)) {
            status = "Household composter is ready";
            context.navigation().stop();
            return true;
        }
        memory.forget(context, MEMORY_ID);
        context.navigation().stop();
        return false;
    }

    private boolean existingComposterRemembered(CitizenBehaviorContext context) {
        BlockPos remembered = memory.get(context, MEMORY_ID).orElse(null);
        if (remembered == null) return false;
        if (!context.level().hasChunkAt(remembered)) return false;
        if (context.level().getBlockState(remembered).is(Blocks.COMPOSTER)) return true;
        memory.forget(context, MEMORY_ID);
        return false;
    }

    private boolean hasComposterNearHome(CitizenBehaviorContext context) {
        BlockPos home = context.home().orElse(context.citizen().blockPosition());
        for (int dy = -3; dy <= 4; dy++) for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            BlockPos pos = home.offset(dx, dy, dz);
            if (context.level().hasChunkAt(pos) && context.level().getBlockState(pos).is(Blocks.COMPOSTER)) return true;
        }
        return false;
    }

    private void release(CitizenBehaviorContext context) {
        if (!hasClaim || context.householdId().isEmpty()) return;
        HouseholdWorkClaims.release(context.householdId().orElseThrow(), REQUEST_ID, context.citizen().getUUID());
        hasClaim = false;
    }

    private static final class ComposterSiteFinder implements CitizenTargetFinder {
        @Override public Optional<BlockPos> findAt(CitizenBehaviorContext context, BlockPos location) {
            if (!context.level().hasChunkAt(location) || !context.level().hasChunkAt(location.below())) return Optional.empty();
            return isSite(context, location) && PlaceItemAction.findReachableApproach(context, location) != null
                    ? Optional.of(location.immutable()) : Optional.empty();
        }

        @Override public Optional<BlockPos> findNear(CitizenBehaviorContext context, BlockPos waypoint, int radius) {
            List<BlockPos> candidates = new ArrayList<>();
            for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                int x = waypoint.getX() + dx;
                int z = waypoint.getZ() + dz;
                if (!context.level().hasChunkAt(new BlockPos(x, waypoint.getY(), z))) continue;
                int groundY = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos site = new BlockPos(x, groundY + 1, z);
                if (isSite(context, site)) candidates.add(site);
            }
            candidates.sort(Comparator.comparingDouble(context.citizen().blockPosition()::distSqr));
            for (BlockPos candidate : candidates)
                if (PlaceItemAction.findReachableApproach(context, candidate) != null) return Optional.of(candidate);
            return Optional.empty();
        }

        private boolean isSite(CitizenBehaviorContext context, BlockPos site) {
            var level = context.level();
            if (!level.hasChunkAt(site) || !level.hasChunkAt(site.above()) || !level.hasChunkAt(site.below())) return false;
            var ground = level.getBlockState(site.below());
            boolean farmGround = ground.is(Blocks.FARMLAND) || ground.is(Blocks.GRASS_BLOCK)
                    || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.ROOTED_DIRT);
            return farmGround && NaturalWater.hydrates(level, site.below())
                    && level.getBlockState(site).isAir() && level.getBlockState(site.above()).isAir()
                    && ground.isFaceSturdy(level, site.below(), Direction.UP);
        }
    }
}
