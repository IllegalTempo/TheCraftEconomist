package com.jedts.theeconomist.citizen.farm;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.behavior.LiveCitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetFinder;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetSearchService;
import com.jedts.theeconomist.citizen.farm.conflict.LandConflictService;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimHooks;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimService;
import com.jedts.theeconomist.citizen.farm.claim.PlotOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Bounded, server-only physical wheat farming. */
public final class FarmerWorkService {
    private static final int RADIUS = CitizenTargetSearchService.SEARCH_RADIUS;
    public enum Mode { CROPS, SEEDS }

    private final Mode mode;
    private final FarmerWorkPlanner planner = new FarmerWorkPlanner();
    private FarmerWorkPlanner.WorkTarget target;
    private boolean facingTarget;
    private boolean pathRequested;
    private final CitizenTargetSearchService targetSearch = new CitizenTargetSearchService();
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    private final CitizenTargetFinder finder = new FarmTargetFinder();
    private int idleTicks;
    private String status = "Searching for farm work";

    public FarmerWorkService(Mode mode) { this.mode = mode; }

    public String status() { return status; }

    public void tick(CitizenEntity citizen) {
        if (!(citizen.level() instanceof ServerLevel level) || !citizen.isAlive()) return;
        BlockPos home = citizen.settleHome(level).orElse(null);
        if (home == null) {
            status = "Looking for a home site";
            return;
        }
        if (target == null) {
            if (shouldWalkAndSearch(citizen)) {
                var search = targetSearch.tick(new LiveCitizenBehaviorContext(citizen, level),
                        mode == Mode.SEEDS ? "farmer.seed_forage" : "farmer.crop_plot", finder, 0.8);
                target = search.location().flatMap(pos -> finder.findAt(
                        new LiveCitizenBehaviorContext(citizen, level), pos)
                        .flatMap(valid -> targetAt(citizen, level, mode == Mode.SEEDS ? valid.below() : valid))).orElse(null);
            }
            facingTarget = false;
            pathRequested = false;
        }
        if (target == null) {
            if (mode == Mode.SEEDS) status = citizen.farmInventory().count(Items.WHEAT_SEEDS) >= 8
                    ? "Seed supply is full" : "Searching for wheat seeds";
            else if (!hasHoe(citizen.farmInventory())) status = "Work paused: no usable hoe";
            else if (!citizen.farmInventory().canInsert(new ItemStack(Items.WHEAT)))
                status = "Work paused: inventory full";
            else if (citizen.farmInventory().count(Items.WHEAT_SEEDS) == 0)
                status = "Work paused: no wheat seeds nearby";
            else status = "Searching for soil near water or ripe wheat";
            if (!shouldWalkAndSearch(citizen)) citizen.getNavigation().stop();
            idleTicks += 5;
            if (mode == Mode.CROPS && idleTicks >= 6000) {
                idleTicks = 0;
                challengeNearbyCitizen(citizen, level);
            }
            return;
        }
        idleTicks = 0;
        Optional<FarmerWorkPlanner.WorkTarget> refreshed = targetAt(citizen, level,
                target.action() == FarmerWorkPlanner.Action.GATHER_SEEDS ? target.pos().below() : target.pos());
        if (refreshed.isEmpty() || refreshed.orElseThrow().action() != target.action()) {
            targetMemory.forget(new LiveCitizenBehaviorContext(citizen, level),
                    mode == Mode.SEEDS ? "farmer.seed_forage" : "farmer.crop_plot");
            citizen.getNavigation().stop();
            clearTarget();
            status = mode == Mode.SEEDS ? "Searching for wheat seeds" : "Searching for soil near water or ripe wheat";
            return;
        }
        target = refreshed.orElseThrow();
        BlockPos pos = target.pos();
        long homeDx = (long) pos.getX() - home.getX(), homeDz = (long) pos.getZ() - home.getZ();
        if (!level.hasChunkAt(pos) || homeDx * homeDx + homeDz * homeDz > (long) RADIUS * RADIUS) {
            citizen.getNavigation().stop();
            clearTarget();
            status = mode == Mode.SEEDS ? "Searching for wheat seeds" : "Searching for soil near water or ripe wheat";
            return;
        }
        if (citizen.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 6.25) {
            status = "Walking to farm work";
            if ((!pathRequested || citizen.getNavigation().isDone())
                    && !citizen.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.8))
                clearTarget();
            else pathRequested = true;
            return;
        }
        BlockPos interactionPos = interactionPos(target);
        citizen.faceBlock(interactionPos);
        if (!facingTarget) {
            facingTarget = true;
            status = "Preparing farm work";
            return;
        }
        perform(citizen, level, target);
        clearTarget();
    }

    private boolean shouldWalkAndSearch(CitizenEntity citizen) {
        if (mode == Mode.SEEDS)
            return citizen.farmInventory().count(Items.WHEAT_SEEDS) < 8
                    && citizen.farmInventory().canInsert(new ItemStack(Items.WHEAT_SEEDS));
        return hasHoe(citizen.farmInventory())
                && citizen.farmInventory().canInsert(new ItemStack(Items.WHEAT))
                && citizen.farmInventory().count(Items.WHEAT_SEEDS) > 0;
    }

    private Optional<FarmerWorkPlanner.WorkTarget> targetAt(CitizenEntity citizen, ServerLevel level, BlockPos ground) {
        if (!level.hasChunkAt(ground) || !level.hasChunkAt(ground.above())) return Optional.empty();
        PlotClaimService claims = PlotClaimService.forLevel(level);
        PlotOwner owner = claims.ensureCitizenClaimsMigrated(citizen);
        CitizenFarmInventory inventory = citizen.farmInventory();
        var state = level.getBlockState(ground);
        var above = level.getBlockState(ground.above());
        boolean mine = owner.equals(claims.ownerAt(ground).orElse(null));
        boolean unclaimed = claims.ownerAt(ground).isEmpty();
        if (mode == Mode.SEEDS) return isSeedForage(above) && inventory.count(Items.WHEAT_SEEDS) < 8
                ? Optional.of(new FarmerWorkPlanner.WorkTarget(ground.above(), FarmerWorkPlanner.Action.GATHER_SEEDS, true, true)) : Optional.empty();
        if (state.is(Blocks.FARMLAND) && mine) {
            if (above.is(Blocks.WHEAT) && above.getBlock() instanceof CropBlock crop && crop.isMaxAge(above))
                return Optional.of(new FarmerWorkPlanner.WorkTarget(ground, FarmerWorkPlanner.Action.HARVEST, true, true));
            if (above.isAir() && inventory.count(Items.WHEAT_SEEDS) > 0)
                return Optional.of(new FarmerWorkPlanner.WorkTarget(ground, FarmerWorkPlanner.Action.PLANT, true, true));
        }
        if (state.is(Blocks.FARMLAND) && unclaimed && NaturalWater.hydrates(level, ground))
            return Optional.of(new FarmerWorkPlanner.WorkTarget(ground, FarmerWorkPlanner.Action.HOE, true, true));
        if (unclaimed && isTillableGround(state) && above.isAir() && hasHoe(inventory)
                && inventory.count(Items.WHEAT_SEEDS) > 0 && NaturalWater.hydrates(level, ground))
            return Optional.of(new FarmerWorkPlanner.WorkTarget(ground, FarmerWorkPlanner.Action.HOE, true, true));
        return Optional.empty();
    }

    private final class FarmTargetFinder implements CitizenTargetFinder {
        @Override public Optional<BlockPos> findAt(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context, BlockPos location) {
            if (!context.level().hasChunkAt(location)) return Optional.empty();
            BlockPos ground = mode == Mode.SEEDS ? location.below() : location;
            return targetAt(context.citizen(), context.level(), ground).map(FarmerWorkPlanner.WorkTarget::pos);
        }
        @Override public Optional<BlockPos> findNear(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context, BlockPos waypoint, int radius) {
            var level = context.level();
            var citizen = context.citizen();
            List<FarmerWorkPlanner.WorkTarget> candidates = new ArrayList<>();
            for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                int x = waypoint.getX() + dx, z = waypoint.getZ() + dz;
                BlockPos probe = new BlockPos(x, waypoint.getY(), z);
                if (!level.hasChunkAt(probe)) continue;
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                targetAt(citizen, level, ground).ifPresent(candidates::add);
            }
            if (candidates.isEmpty()) return Optional.empty();
            PlotClaimService claims = PlotClaimService.forLevel(level);
            PlotOwner owner = claims.ensureCitizenClaimsMigrated(citizen);
            return planner.choose(candidates, citizen.blockPosition(), context.home().orElse(waypoint),
                    List.copyOf(claims.claimedBy(owner)),
                    pos -> owner.equals(claims.ownerAt(pos).orElse(null)),
                    pos -> NaturalWater.hydrates(level, pos)).map(FarmerWorkPlanner.WorkTarget::pos);
        }
    }

    private void perform(CitizenEntity citizen, ServerLevel level, FarmerWorkPlanner.WorkTarget target) {
        BlockPos pos = target.pos();
        PlotClaimService claims = PlotClaimService.forLevel(level);
        claims.ensureCitizenClaimsMigrated(citizen);
        CitizenFarmInventory inventory = citizen.farmInventory();
        switch (target.action()) {
            case HARVEST -> {
                if (!ownedBy(citizen, claims, pos)) return;
                var state = level.getBlockState(pos.above());
                if (!state.is(Blocks.WHEAT) || !(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
                if (!breakIntoInventory(citizen, level, pos.above(), inventory)) return;
                citizen.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                status = "Harvesting wheat";
            }
            case PLANT -> {
                if (!ownedBy(citizen, claims, pos) || !level.getBlockState(pos.above()).isAir()
                        || inventory.count(Items.WHEAT_SEEDS) < 1) return;
                ItemStack seed = inventory.remove(Items.WHEAT_SEEDS, 1);
                if (!level.setBlock(pos.above(), Blocks.WHEAT.defaultBlockState(), 3)) inventory.insert(seed);
                else {
                    citizen.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0f, 1.0f);
                    status = "Planting wheat";
                }
            }
            case GATHER_SEEDS -> {
                var state = level.getBlockState(pos);
                if (!isSeedForage(state)) return;
                if (!breakIntoInventory(citizen, level, pos, inventory)) return;
                citizen.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                targetMemory.forget(new LiveCitizenBehaviorContext(citizen, level), "farmer.seed_forage");
                status = "Gathering wheat seeds";
            }
            case HOE -> {
                if (!NaturalWater.hydrates(level, pos)) return;
                if (level.getBlockState(pos).is(Blocks.FARMLAND)) {
                    if (claims.ownerAt(pos).isEmpty()) claims.claimCitizen(citizen, pos);
                    status = "Claiming farmland";
                    return;
                }
                var state = level.getBlockState(pos);
                if (!isTillableGround(state) || !level.getBlockState(pos.above()).isAir()
                        || claims.ownerAt(pos).isPresent()) return;
                int hoeSlot = findHoe(inventory);
                if (hoeSlot < 0 || !level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3)) return;
                claims.claimNewCitizenFarmland(citizen, pos);
                ItemStack hoe = inventory.slot(hoeSlot);
                hoe.setDamageValue(hoe.getDamageValue() + 1);
                inventory.set(hoeSlot, hoe.isBroken() ? ItemStack.EMPTY : hoe);
                citizen.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                level.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                status = hoe.isBroken() ? "Work paused: no usable hoe" : "Hoeing farmland";
            }
        }
    }

    private boolean ownedBy(CitizenEntity citizen, PlotClaimService claims, BlockPos pos) {
        return claims.ownerAt(pos).filter(claims.ownerFor(citizen)::equals).isPresent();
    }

    private boolean hasHoe(CitizenFarmInventory inventory) { return findHoe(inventory) >= 0; }

    private boolean breakIntoInventory(CitizenEntity citizen, ServerLevel level, BlockPos pos,
                                       CitizenFarmInventory inventory) {
        var state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity);
        CitizenFarmInventory trial = inventory.copy();
        for (ItemStack stack : drops) if (!trial.insert(stack).isEmpty()) {
            status = "Work paused: inventory full";
            return false;
        }
        if (!level.destroyBlock(pos, false, citizen, 512)) return false;
        for (ItemStack stack : drops) inventory.insert(stack);
        return true;
    }

    private BlockPos interactionPos(FarmerWorkPlanner.WorkTarget work) {
        return work.action() == FarmerWorkPlanner.Action.HARVEST ? work.pos().above() : work.pos();
    }

    private void clearTarget() {
        target = null;
        facingTarget = false;
        pathRequested = false;
    }

    private boolean isSeedForage(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN);
    }

    private boolean isTillableGround(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    private void challengeNearbyCitizen(CitizenEntity citizen, ServerLevel level) {
        PlotClaimService claims = PlotClaimService.forLevel(level);
        for (CitizenEntity other : level.getEntitiesOfClass(CitizenEntity.class,
                citizen.getBoundingBox().inflate(16), candidate -> candidate != citizen && candidate.isAlive())) {
            for (BlockPos pos : BlockPos.betweenClosed(other.blockPosition().offset(-4, -1, -4),
                    other.blockPosition().offset(4, 1, 4))) {
                if (!level.hasChunkAt(pos)) continue;
                if (claims.ownerAt(pos).filter(claims.ownerFor(other)::equals).isPresent()
                        && (ownedBy(citizen, claims, pos.north()) || ownedBy(citizen, claims, pos.south())
                        || ownedBy(citizen, claims, pos.east()) || ownedBy(citizen, claims, pos.west()))) {
                    LandConflictService.challenge(other, citizen, pos);
                    return;
                }
            }
        }
    }

    private int findHoe(CitizenFarmInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) if (PlotClaimHooks.isHoe(inventory.slot(i))) return i;
        return -1;
    }
}
