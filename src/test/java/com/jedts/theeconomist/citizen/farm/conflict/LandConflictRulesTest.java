package com.jedts.theeconomist.citizen.farm.conflict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LandConflictRulesTest {
    @Test void angerValueAndScarcityEachIncreaseFightChance() {
        double baseline = LandConflictRules.fightChance(10, 8, 64, 20, 20, 0, 0, 4);
        assertTrue(LandConflictRules.fightChance(90, 8, 64, 20, 20, 0, 0, 4) > baseline);
        assertTrue(LandConflictRules.fightChance(10, 32, 64, 20, 20, 0, 0, 4) > baseline);
        assertTrue(LandConflictRules.fightChance(10, 8, 0, 20, 20, 0, 0, 4) > baseline);
    }

    @Test void chanceStaysWithinFiveAndNinetyFivePercent() {
        assertEquals(0.05, LandConflictRules.fightChance(0, 0, 64, 1, 100, 0, 20, 2));
        assertTrue(LandConflictRules.fightChance(100, 1000, 0, 20, 1, 20, 0, 6) <= 0.95);
    }
}
