package com.jedts.theeconomist.citizen.behavior.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SurvivalDecisionRulesTest {
    @Test
    void liquidEscapeIsAnImmediateOverrideOnlyWhileInLiquid() {
        var escape = SurvivalDecisionRules.escapeLiquid(true, 0.25);
        assertTrue(escape.eligible());
        assertTrue(escape.emergencyOverride());
        assertTrue(escape.score() > 0.0);
        assertFalse(SurvivalDecisionRules.escapeLiquid(false, 1.0).eligible());
    }

    @Test
    void moreSevereInjuryRaisesPanicScore() {
        var hurt = SurvivalDecisionRules.panic(true, 0.2, true, 3.0, 30, 20);
        var barelyHurt = SurvivalDecisionRules.panic(true, 0.9, true, 3.0, 30, 20);
        assertTrue(hurt.score() > barelyHurt.score());
        assertTrue(hurt.explanation().contains("attacker"));
    }

    @Test
    void closerMonsterAndLowerSafetyRaiseAvoidanceScore() {
        var close = SurvivalDecisionRules.avoidMonster(true, 2.0, 15, 20, 0.8);
        var far = SurvivalDecisionRules.avoidMonster(true, 7.0, 80, 20, 0.8);
        assertTrue(close.score() > far.score());
        assertFalse(SurvivalDecisionRules.avoidMonster(false, 0, 50, 50, 1).eligible());
    }

    @Test
    void combatNeedsTargetAndScoresCitizenCapabilityAgainstIt() {
        var advantage = SurvivalDecisionRules.combat(true, 2, 80, 75, .9, 8, .3, 1);
        var disadvantage = SurvivalDecisionRules.combat(true, 2, 10, 20, .3, 1, .9, 8);
        assertTrue(advantage.score() > disadvantage.score());
        assertFalse(SurvivalDecisionRules.combat(false, 0, 0, 0, 1, 0, 0, 0).eligible());
    }
}
