package com.jedts.theeconomist.citizen.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Mutable copy of one main inventory, used to preflight an entire transaction. */
public final class TradeInventory {
    private final List<ItemStack> slots;

    public TradeInventory(List<ItemStack> source) {
        slots = new ArrayList<>(source.size());
        for (ItemStack stack : source) slots.add(stack.copy());
    }

    public TradeInventory copy() { return new TradeInventory(slots); }

    public List<ItemStack> slots() { return slots.stream().map(ItemStack::copy).toList(); }

    public int count(Item item) {
        int count = 0;
        for (ItemStack stack : slots) if (stack.is(item)) count += stack.getCount();
        return count;
    }

    public ItemStack remove(Item item, int quantity) {
        if (quantity <= 0 || count(item) < quantity) return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        int left = quantity;
        for (int i = 0; i < slots.size() && left > 0; i++) {
            ItemStack stack = slots.get(i);
            if (!stack.is(item)) continue;
            int moved = Math.min(left, stack.getCount());
            if (result.isEmpty()) result = stack.copyWithCount(0);
            result.grow(moved);
            stack.shrink(moved);
            if (stack.isEmpty()) slots.set(i, ItemStack.EMPTY);
            left -= moved;
        }
        return result;
    }

    public boolean insert(ItemStack supplied) {
        if (supplied.isEmpty()) return true;
        int capacity = 0;
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) capacity += supplied.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, supplied))
                capacity += stack.getMaxStackSize() - stack.getCount();
        }
        if (capacity < supplied.getCount()) return false;
        ItemStack remaining = supplied.copy();
        for (ItemStack stack : slots) {
            if (remaining.isEmpty()) break;
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, remaining)) continue;
            int moved = Math.min(remaining.getCount(), stack.getMaxStackSize() - stack.getCount());
            stack.grow(moved);
            remaining.shrink(moved);
        }
        for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
            if (!slots.get(i).isEmpty()) continue;
            int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            slots.set(i, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        return true;
    }

    public CoinStock coins(CoinDenominations denominations) {
        return new CoinStock(count(denominations.copper()),
                count(denominations.silver()), count(denominations.gold()));
    }

    public boolean removeCoins(CoinDenominations denominations, CoinStock coins) {
        if (!coins(denominations).contains(coins)) return false;
        remove(denominations.copper(), coins.copper());
        remove(denominations.silver(), coins.silver());
        remove(denominations.gold(), coins.gold());
        return true;
    }

    public boolean insertCoins(CoinDenominations denominations, CoinStock coins) {
        TradeInventory trial = copy();
        if (!trial.insertDenomination(denominations.copper(), coins.copper())
                || !trial.insertDenomination(denominations.silver(), coins.silver())
                || !trial.insertDenomination(denominations.gold(), coins.gold())) return false;
        for (int i = 0; i < slots.size(); i++) slots.set(i, trial.slots.get(i));
        return true;
    }

    private boolean insertDenomination(Item item, int count) {
        if (count == 0) return true;
        return insert(new ItemStack(item, count));
    }
}
