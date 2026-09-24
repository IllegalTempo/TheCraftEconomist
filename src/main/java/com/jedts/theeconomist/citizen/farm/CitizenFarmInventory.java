package com.jedts.theeconomist.citizen.farm;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Finite, saved storage for goods, equipment, and physical Crown items. */
public final class CitizenFarmInventory {
    public static final int SIZE = 27;
    private final List<ItemStack> slots = new ArrayList<>(SIZE);

    public CitizenFarmInventory() {
        for (int i = 0; i < SIZE; i++) slots.add(ItemStack.EMPTY);
    }

    public int size() { return SIZE; }

    public ItemStack slot(int index) { return slots.get(index).copy(); }

    public void set(int index, ItemStack stack) { slots.set(index, stack.copy()); }

    public List<ItemStack> stacks() { return slots.stream().map(ItemStack::copy).toList(); }

    public int count(Item item) {
        int total = 0;
        for (ItemStack stack : slots) if (stack.is(item)) total += stack.getCount();
        return total;
    }

    public int count(ItemStack exemplar) {
        int total = 0;
        for (ItemStack stack : slots)
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, exemplar)) total += stack.getCount();
        return total;
    }

    public int sellableWheat() { return Math.max(0, count(Items.WHEAT) - 4); }

    /** Inserts as much as possible and returns a copy of the remainder. */
    public ItemStack insert(ItemStack supplied) {
        Objects.requireNonNull(supplied, "supplied");
        ItemStack remaining = supplied.copy();
        if (remaining.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < SIZE && !remaining.isEmpty(); i++) {
            ItemStack current = slots.get(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remaining)) continue;
            int moved = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
            if (moved <= 0) continue;
            current.grow(moved);
            remaining.shrink(moved);
        }
        for (int i = 0; i < SIZE && !remaining.isEmpty(); i++) {
            if (!slots.get(i).isEmpty()) continue;
            int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            slots.set(i, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        return remaining;
    }

    public boolean canInsert(ItemStack supplied) {
        CitizenFarmInventory copy = copy();
        return copy.insert(supplied).isEmpty();
    }

    public ItemStack remove(Item item, int quantity) {
        Objects.requireNonNull(item, "item");
        if (quantity <= 0 || count(item) < quantity) return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        int left = quantity;
        for (int i = 0; i < SIZE && left > 0; i++) {
            ItemStack stack = slots.get(i);
            if (!stack.is(item)) continue;
            int moved = Math.min(stack.getCount(), left);
            if (result.isEmpty()) result = stack.copyWithCount(0);
            result.grow(moved);
            stack.shrink(moved);
            if (stack.isEmpty()) slots.set(i, ItemStack.EMPTY);
            left -= moved;
        }
        return result;
    }

    /** Removes an exact component-matching stack quantity, or nothing when it is unavailable. */
    public ItemStack removeMatching(ItemStack exemplar, int quantity) {
        Objects.requireNonNull(exemplar, "exemplar");
        if (exemplar.isEmpty() || quantity <= 0 || count(exemplar) < quantity) return ItemStack.EMPTY;
        ItemStack result = exemplar.copyWithCount(quantity);
        int left = quantity;
        for (int i = 0; i < SIZE && left > 0; i++) {
            ItemStack stack = slots.get(i);
            if (!ItemStack.isSameItemSameComponents(stack, exemplar)) continue;
            int moved = Math.min(stack.getCount(), left);
            stack.shrink(moved);
            if (stack.isEmpty()) slots.set(i, ItemStack.EMPTY);
            left -= moved;
        }
        return result;
    }

    public CitizenFarmInventory copy() {
        CitizenFarmInventory copy = new CitizenFarmInventory();
        for (int i = 0; i < SIZE; i++) copy.set(i, slots.get(i));
        return copy;
    }

    public void write(ValueOutput output) {
        var list = output.list("Items", ItemStack.OPTIONAL_CODEC);
        for (ItemStack stack : slots) list.add(stack);
    }

    public static CitizenFarmInventory read(ValueInput input) {
        CitizenFarmInventory inventory = new CitizenFarmInventory();
        int index = 0;
        for (ItemStack stack : input.listOrEmpty("Items", ItemStack.OPTIONAL_CODEC)) {
            if (index >= SIZE) break;
            inventory.set(index++, stack);
        }
        return inventory;
    }
}
