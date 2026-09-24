package com.jedts.theeconomist.citizen.behavior.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenActionScorerTest {
    @Test
    void computesSharedScoreAndExposesFactorContributions() {
        var result = CitizenActionScorer.evaluate(new CitizenScoreInputs(1, 1, 1, 1, .25, .5));
        assertEquals(85.0, result.score());
        assertEquals(6, result.factors().size());
    }

    @Test
    void noUrgencyProducesZeroBeforePenalties() {
        var result = CitizenActionScorer.evaluate(new CitizenScoreInputs(0, 1, 1, 1, 0, 0));
        assertEquals(0.0, result.score());
    }

    @Test
    void clampsInputsAndNormalizesNonFiniteValues() {
        var result = CitizenActionScorer.evaluate(new CitizenScoreInputs(2, -1, Double.NaN,
                Double.POSITIVE_INFINITY, -3, Double.NEGATIVE_INFINITY));
        assertEquals(0.0, result.score());
        assertTrue(result.factors().stream().allMatch(factor -> Double.isFinite(factor.value())
                && factor.value() >= 0.0 && factor.value() <= 1.0));
    }

    @Test
    void scoreIsClampedAtZeroAndOneHundred() {
        assertEquals(100.0, CitizenActionScorer.evaluate(new CitizenScoreInputs(1, 1, 1, 1, 0, 0)).score());
        assertEquals(0.0, CitizenActionScorer.evaluate(new CitizenScoreInputs(0, 0, 0, 0, 1, 1)).score());
    }

    @Test
    void configuredCostAndRiskPenaltyWeightsChangeTheScore() {
        var result = CitizenActionScorer.evaluate(new CitizenScoreInputs(1, 1, 1, 1, .25, .5), 30, 40);
        assertEquals(72.5, result.score());
    }
}
