package com.jedts.theeconomist.citizen.house;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.util.List;

/** Access to the one chest assigned to a generated household. */
public final class HouseholdStorage {
    public enum Access { READY, WALKING, MISSING, UNREACHABLE }

    private HouseholdStorage() { }

    public static Optional<Container> resolve(CitizenBehaviorContext context) {
        if (context.householdId().isEmpty() || context.home().isEmpty()) return Optional.empty();
        Household household = HouseholdSavedData.forLevel(context.level()).ledger().get(context.home().orElseThrow())
                .filter(value -> value.householdId().equals(context.householdId().orElseThrow())).orElse(null);
        if (household == null) return Optional.empty();
        BlockPos pos = household.storagePosition().orElse(null);
        if (pos == null || !context.level().hasChunkAt(pos)
                || !context.level().getBlockState(pos).is(Blocks.CHEST)) return Optional.empty();
        return Optional.ofNullable(context.level().getBlockEntity(pos)).filter(Container.class::isInstance)
                .map(Container.class::cast);
    }

    public static Optional<BlockPos> position(CitizenBehaviorContext context) {
        if (context.householdId().isEmpty() || context.home().isEmpty()) return Optional.empty();
        return HouseholdSavedData.forLevel(context.level()).ledger().get(context.home().orElseThrow())
                .filter(value -> value.householdId().equals(context.householdId().orElseThrow()))
                .flatMap(Household::storagePosition);
    }

    public static Access approach(CitizenBehaviorContext context, double speed) {
        BlockPos pos = position(context).orElse(null);
        if (pos == null || !context.level().hasChunkAt(pos)
                || !context.level().getBlockState(pos).is(Blocks.CHEST)) return Access.MISSING;
        double dx = context.citizen().getX() - (pos.getX() + 0.5);
        double dz = context.citizen().getZ() - (pos.getZ() + 0.5);
        if (dx * dx + dz * dz <= 4.0) return Access.READY;
        if (!context.navigation().isInProgress() || context.navigation().isDone()) {
            if (!context.navigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed))
                return Access.UNREACHABLE;
        }
        return Access.WALKING;
    }

    public static int count(Container container, Item item) {
        return container.countItem(item);
    }

    public static int count(Container container, ItemStack exemplar) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, exemplar)) total += stack.getCount();
        }
        return total;
    }

    public static boolean canInsert(Container container, List<ItemStack> supplied) {
        List<ItemStack> slots = new java.util.ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) slots.add(container.getItem(slot).copy());
        for (ItemStack original : supplied) {
            ItemStack remaining = original.copy();
            for (int slot = 0; slot < slots.size() && !remaining.isEmpty(); slot++) {
                ItemStack current = slots.get(slot);
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remaining)) continue;
                int moved = Math.min(remaining.getCount(), container.getMaxStackSize(current) - current.getCount());
                if (moved <= 0) continue;
                current.grow(moved);
                remaining.shrink(moved);
            }
            for (int slot = 0; slot < slots.size() && !remaining.isEmpty(); slot++) {
                if (!slots.get(slot).isEmpty() || !container.canPlaceItem(slot, remaining)) continue;
                int moved = Math.min(remaining.getCount(), container.getMaxStackSize(remaining));
                slots.set(slot, remaining.copyWithCount(moved));
                remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    /** Inserts as much as possible and returns the remainder. */
    public static ItemStack insert(Container container, ItemStack supplied) {
        ItemStack remaining = supplied.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remaining)) continue;
            int moved = Math.min(remaining.getCount(), container.getMaxStackSize(current) - current.getCount());
            if (moved <= 0) continue;
            ItemStack updated = current.copy();
            updated.grow(moved);
            container.setItem(slot, updated);
            remaining.shrink(moved);
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remaining)) continue;
            int moved = Math.min(remaining.getCount(), container.getMaxStackSize(remaining));
            container.setItem(slot, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        if (!ItemStack.matches(supplied, remaining)) container.setChanged();
        return remaining;
    }

    /** Extracts the requested amount, or nothing when the chest cannot supply all of it. */
    public static ItemStack extract(Container container, Item item, int quantity) {
        if (quantity <= 0) return ItemStack.EMPTY;
        ItemStack exemplar = ItemStack.EMPTY;
        int available = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) continue;
            if (exemplar.isEmpty()) exemplar = stack.copyWithCount(1);
            if (ItemStack.isSameItemSameComponents(exemplar, stack)) available += stack.getCount();
        }
        if (available < quantity) return ItemStack.EMPTY;
        ItemStack result = exemplar.copyWithCount(quantity);
        int left = quantity;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack current = container.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(exemplar, current)) continue;
            int moved = Math.min(current.getCount(), left);
            ItemStack updated = current.copy();
            updated.shrink(moved);
            container.setItem(slot, updated);
            left -= moved;
        }
        container.setChanged();
        return result;
    }

    public static ItemStack extract(Container container, ItemStack exemplar, int quantity) {
        if (exemplar.isEmpty() || quantity <= 0 || count(container, exemplar) < quantity) return ItemStack.EMPTY;
        ItemStack result = exemplar.copyWithCount(quantity);
        int left = quantity;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack current = container.getItem(slot);
            if (!ItemStack.isSameItemSameComponents(exemplar, current)) continue;
            int moved = Math.min(current.getCount(), left);
            ItemStack updated = current.copy();
            updated.shrink(moved);
            container.setItem(slot, updated);
            left -= moved;
        }
        container.setChanged();
        return result;
    }
}
