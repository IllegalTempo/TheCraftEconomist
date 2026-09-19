package com.jedts.theeconomist.currency;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CrownValuesTest {
    @Test
    void emptyStackHasNoCurrencyValue() {
        assertEquals(0, CrownValues.valueOf(ItemStack.EMPTY));
    }

    @Test
    void emptyStacksHaveZeroTotal() {
        assertEquals(0L, CrownValues.total(java.util.List.of(ItemStack.EMPTY)));
    }
}
