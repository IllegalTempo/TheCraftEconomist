package com.jedts.theeconomist.citizen.behavior.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RoutineDecisionRulesTest {
    @Test
    void lowEnergyAtNightAndAvailableBedIncreaseSleepUtility() {
        var tired = RoutineDecisionRules.sleep(true, true, 15, true, 2);
        var rested = RoutineDecisionRules.sleep(true, true, 95, true, 2);
        assertTrue(tired.score() > rested.score());
        assertTrue(RoutineDecisionRules.sleep(true, true, 15, false, 2).score() == 0);
        assertFalse(RoutineDecisionRules.sleep(false, true, 15, true, 2).eligible());
    }

    @Test
    void farmingScoresUseAmbitionToolsSeedsCapacityAndWorkOpportunity() {
        var ready = RoutineDecisionRules.farming(true, true, true, true, 70, 65,
                true, true, true, 2);
        var noSeeds = RoutineDecisionRules.farming(true, true, true, true, 70, 65,
                true, true, false, 2);
        assertTrue(ready.score() > noSeeds.score());
        assertTrue(noSeeds.explanation().contains("seed"));
        assertFalse(RoutineDecisionRules.farming(false, true, true, true, 70, 65,
                true, true, true, 2).eligible());
        var awayFromHome = RoutineDecisionRules.farming(true, true, true, false, 70, 65,
                true, true, true, 2);
        assertFalse(awayFromHome.eligible());
        assertTrue(awayFromHome.explanation().contains("16-block"));
    }

    @Test
    void contractReviewFavorsAReadyUnemployedWorker() {
        var available = RoutineDecisionRules.contractReview(true, true, true, true, false, 80, 10);
        var noOffer = RoutineDecisionRules.contractReview(true, true, true, false, false, 80, 10);
        assertTrue(available.score() > noOffer.score());
        assertFalse(RoutineDecisionRules.contractReview(false, true, true, true, false, 80, 10).eligible());
    }

    @Test
    void returnHomeUtilityRisesWithDistanceAndMissingHouseholdIsExplained() {
        var far = RoutineDecisionRules.returnHome(true, 64, 75, 70, true);
        var near = RoutineDecisionRules.returnHome(true, 18, 75, 70, true);
        assertTrue(far.score() > near.score());
        assertTrue(far.score() > RoutineDecisionRules.returnHome(true, 64, 75, 70, false).score());
        var missing = RoutineDecisionRules.confused(true, true, false);
        assertTrue(missing.eligible());
        assertTrue(missing.explanation().contains("household"));
        assertTrue(RoutineDecisionRules.confused(true, false, true).eligible());
    }
}
