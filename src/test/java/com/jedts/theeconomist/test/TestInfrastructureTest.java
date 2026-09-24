package com.jedts.theeconomist.test;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestInfrastructureTest {
    @Test
    void fixedClockAdvancesOnlyWhenRequested() {
        FixedClock clock = new FixedClock(10L);

        assertEquals(10L, clock.now());
        clock.advance(5L);
        assertEquals(15L, clock.now());
    }

    @Test
    void stackFixturesCreateIndependentCopies() {
        ItemStack original = ItemStack.EMPTY;
        ItemStack copy = ItemStackFixtures.copy(original);

        assertEquals(original, copy);
    }
}
