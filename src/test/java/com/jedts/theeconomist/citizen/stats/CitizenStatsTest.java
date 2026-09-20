package com.jedts.theeconomist.citizen.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenStatsTest {
    @Test
    void defaultsAreStableAndUseful() {
        CitizenStats stats = CitizenStats.defaults();
        assertEquals(100, stats.hunger());
        assertEquals(100, stats.energy());
        assertEquals(50, stats.safety());
        assertEquals(50, stats.morale());
        assertEquals(50, stats.intelligence());
        assertEquals(0, stats.anger());
    }

    @Test
    void valuesAreClampedToZeroThroughOneHundred() {
        CitizenStats stats = new CitizenStats(-1, 101, -2, 200, -3, 150, -4, 120, -5, 101, -6, 500);
        assertEquals(0, stats.hunger());
        assertEquals(100, stats.energy());
        assertEquals(0, stats.safety());
        assertEquals(100, stats.morale());
        assertEquals(0, stats.intelligence());
        assertEquals(100, stats.anger());
    }
}
