package com.jedts.theeconomist.citizen.behavior.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AmbientDecisionRulesTest {
    @Test
    void nearbyCreatureRaisesLookUtilityWithAConservativeReward() {
        var nearby = AmbientDecisionRules.lookAtCreature(true, true, 2,
                com.jedts.theeconomist.citizen.config.DecisionScoringConfig.defaults());
        var distant = AmbientDecisionRules.lookAtCreature(true, true, 7,
                com.jedts.theeconomist.citizen.config.DecisionScoringConfig.defaults());
        assertTrue(nearby.score() > distant.score());
        assertTrue(nearby.score() < 10);
        assertFalse(AmbientDecisionRules.lookAtCreature(false, true, 1,
                com.jedts.theeconomist.citizen.config.DecisionScoringConfig.defaults()).eligible());
        assertFalse(AmbientDecisionRules.lookAtCreature(true, false, 1,
                com.jedts.theeconomist.citizen.config.DecisionScoringConfig.defaults()).eligible());
    }

    @Test
    void lowMoraleAndSafetyRaiseWanderUtilityAndIdleRemainsFallback() {
        var needsLeisure = AmbientDecisionRules.wander(true, true, 15, 20, true);
        var contentCitizen = AmbientDecisionRules.wander(true, true, 90, 90, true);
        var idle = AmbientDecisionRules.idle(false);
        assertTrue(needsLeisure.score() > contentCitizen.score());
        assertTrue(needsLeisure.score() > idle.score());
        assertTrue(idle.eligible());
        assertFalse(AmbientDecisionRules.wander(true, false, 15, 20, true).eligible());
    }
}
