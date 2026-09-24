package com.jedts.theeconomist.citizen.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenStatsSimulatorTest {
    @Test
    void a_safe_fed_and_restful_interval_improves_needs() {
        CitizenStats before = new CitizenStats(40, 30, 50, 40, 50, 20, 0, 50, 50, 50, 50, 50);
        CitizenStats after = CitizenStatsSimulator.advance(before, true, true, true);

        assertEquals(60, after.hunger());
        assertEquals(45, after.energy());
        assertEquals(52, after.safety());
        assertEquals(42, after.morale());
        assertEquals(18, after.anger());
    }

    @Test
    void deprivation_and_danger_reduce_needs_and_raise_anger() {
        CitizenStats before = new CitizenStats(10, 10, 10, 50, 50, 20, 0, 50, 50, 50, 50, 50);
        CitizenStats after = CitizenStatsSimulator.advance(before, false, false, false);

        assertEquals(8, after.hunger());
        assertEquals(8, after.energy());
        assertEquals(5, after.safety());
        assertEquals(45, after.morale());
        assertEquals(23, after.anger());
    }
}
