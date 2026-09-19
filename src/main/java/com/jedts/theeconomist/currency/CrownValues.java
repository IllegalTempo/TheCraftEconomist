package com.jedts.theeconomist.currency;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class CrownValues {
    private CrownValues() {
    }

    public static int valueOf(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return 0;
        }
        for (CrownDenomination denomination : CrownDenomination.values()) {
            if (stack.is(CrownItems.item(denomination))) {
                return denomination.unitValue();
            }
        }
        return 0;
    }

    public static long total(Iterable<ItemStack> stacks) {
        Objects.requireNonNull(stacks, "stacks");
        long total = 0L;
        for (ItemStack stack : stacks) {
            Objects.requireNonNull(stack, "stack");
            total = Math.addExact(total, Math.multiplyExact((long) valueOf(stack), stack.getCount()));
        }
        return total;
    }
}
