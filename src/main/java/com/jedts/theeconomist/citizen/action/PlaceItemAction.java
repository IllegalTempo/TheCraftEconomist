package com.jedts.theeconomist.citizen.action;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetFinder;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetSearchResult;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetSearchService;
import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.house.HouseholdStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

/** Places a carried item at a caller-defined, incrementally found block position. */
public final class PlaceItemAction implements CitizenItemAction {
    private final Item item;
    private final int quantity;
    private final CitizenTargetFinder siteFinder;
    private final CitizenTargetSearchService targetSearch = new CitizenTargetSearchService();
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    private BlockPos target;
    private BlockPos approach;
    private BlockPos lastPlaced;
    private String status;

    public PlaceItemAction(Item item, int quantity, CitizenTargetFinder siteFinder) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.item = java.util.Objects.requireNonNull(item, "item");
        this.quantity = quantity;
        this.siteFinder = java.util.Objects.requireNonNull(siteFinder, "siteFinder");
        status = "Finding a place for " + displayName();
    }

    @Override public CitizenItemActionResult tick(CitizenBehaviorContext context) {
        Block block = Block.byItem(item);
        if (block == net.minecraft.world.level.block.Blocks.AIR)
            return set(CitizenItemActionResult.unavailable(displayName() + " cannot be placed as a block"));
        if (target == null) {
            CitizenTargetSearchResult search = targetSearch.tick(context, targetId(), siteFinder, 1.0);
            if (search.state() != CitizenTargetSearchResult.State.FOUND || search.location().isEmpty())
                return set(CitizenItemActionResult.running("Searching for a place for " + displayName()));
            target = search.location().orElseThrow().immutable();
        }

        if (!validSite(context, target)) {
            invalidate(context);
            return set(CitizenItemActionResult.running("Placement site changed; searching again"));
        }

        if (approach == null) approach = findReachableApproach(context, target);
        if (approach == null) {
            invalidate(context);
            return set(CitizenItemActionResult.running("Placement site unreachable; searching again"));
        }

        CitizenFarmInventory inventory = context.citizen().farmInventory();
        int carried = inventory.count(item);
        if (carried < quantity) {
            if (context.householdId().isEmpty())
                return set(CitizenItemActionResult.unavailable("Citizen does not carry " + displayName()));
            Container chest = HouseholdStorage.resolve(context).orElse(null);
            if (chest == null) return set(CitizenItemActionResult.unavailable("Home chest unavailable"));
            if (HouseholdStorage.count(chest, item) < quantity - carried)
                return set(CitizenItemActionResult.unavailable("Home chest is missing " + displayName()));
            HouseholdStorage.Access access = HouseholdStorage.approach(context, 1.0);
            if (access == HouseholdStorage.Access.WALKING)
                return set(CitizenItemActionResult.running("Collecting " + displayName() + " from home chest"));
            if (access != HouseholdStorage.Access.READY)
                return set(CitizenItemActionResult.unavailable("Home chest unreachable"));
            chest = HouseholdStorage.resolve(context).orElse(null);
            if (chest == null) return set(CitizenItemActionResult.unavailable("Home chest unavailable"));
            ItemStack stack = HouseholdStorage.extract(chest, item, quantity - carried);
            ItemStack remainder = inventory.insert(stack);
            if (!remainder.isEmpty()) {
                HouseholdStorage.insert(chest, remainder);
                return set(CitizenItemActionResult.unavailable("Citizen inventory is full"));
            }
            return set(CitizenItemActionResult.running("Carrying " + displayName() + " to placement site"));
        }

        double dx = context.citizen().getX() - (target.getX() + 0.5);
        double dy = context.citizen().getY() - target.getY();
        double dz = context.citizen().getZ() - (target.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > 4.0) {
            status = "Walking to " + displayName() + " placement site";
            if ((!context.navigation().isInProgress() || context.navigation().isDone())
                    && !context.navigation().moveTo(approach.getX() + 0.5, approach.getY(), approach.getZ() + 0.5, 1.0)) {
                invalidate(context);
                return set(CitizenItemActionResult.running("Placement path blocked; searching again"));
            }
            return set(CitizenItemActionResult.running(status));
        }
        if (!validSite(context, target)) {
            invalidate(context);
            return set(CitizenItemActionResult.running("Placement site changed; searching again"));
        }
        ItemStack held = inventory.remove(item, quantity);
        if (held.isEmpty()) return set(CitizenItemActionResult.running("Placement item changed; checking home chest"));
        if (!context.level().setBlock(target, block.defaultBlockState(), 3)) {
            inventory.insert(held);
            invalidate(context);
            return set(CitizenItemActionResult.running("Could not place " + displayName() + "; searching again"));
        }
        context.citizen().swing(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        targetMemory.remember(context, targetId(), target);
        lastPlaced = target.immutable();
        context.navigation().stop();
        status = "Placed " + displayName();
        target = null;
        approach = null;
        return set(CitizenItemActionResult.complete(status));
    }

    public Optional<BlockPos> targetPosition() { return Optional.ofNullable(target == null ? lastPlaced : target); }
    @Override public String status() { return status; }

    @Override public void stop(CitizenBehaviorContext context) {
        target = null;
        approach = null;
        lastPlaced = null;
        context.navigation().stop();
    }

    private boolean validSite(CitizenBehaviorContext context, BlockPos pos) {
        if (!context.level().hasChunkAt(pos) || !context.level().hasChunkAt(pos.above())
                || !context.level().hasChunkAt(pos.below())) return false;
        return siteFinder.findAt(context, pos).filter(pos::equals).isPresent()
                && context.level().getBlockState(pos).canBeReplaced()
                && context.level().getBlockState(pos.below()).isFaceSturdy(context.level(), pos.below(), net.minecraft.core.Direction.UP);
    }

    public static BlockPos findReachableApproach(CitizenBehaviorContext context, BlockPos site) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            if ((long) dx * dx + (long) dz * dz > 4 || dx == 0 && dz == 0) continue;
            BlockPos candidate = site.offset(dx, 0, dz);
            if (!context.level().hasChunkAt(candidate) || !context.level().hasChunkAt(candidate.above())
                    || !context.level().getBlockState(candidate).isAir()
                    || !context.level().getBlockState(candidate.above()).isAir()) continue;
            var path = context.navigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) continue;
            double distance = context.citizen().distanceToSqr(candidate.getX() + 0.5,
                    candidate.getY(), candidate.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private String targetId() {
        return "placement." + BuiltInRegistries.ITEM.getKey(item);
    }

    private void invalidate(CitizenBehaviorContext context) {
        targetMemory.forget(context, targetId());
        context.navigation().stop();
        target = null;
        approach = null;
    }

    private String displayName() { return new ItemStack(item).getHoverName().getString(); }

    private CitizenItemActionResult set(CitizenItemActionResult result) {
        status = result.status();
        return result;
    }
}
