package com.jedts.theeconomist.test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemStackFixtures {
    private ItemStackFixtures() {
    }

    public static ItemStack item(Item item, int count) {
        return new ItemStack(item, count);
    }

    public static ItemStack copy(ItemStack stack) {
        return stack.copy();
    }
}
