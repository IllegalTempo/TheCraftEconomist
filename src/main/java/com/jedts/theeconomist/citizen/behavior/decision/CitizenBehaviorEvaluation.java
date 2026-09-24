package com.jedts.theeconomist.citizen.behavior.decision;

import com.jedts.theeconomist.citizen.config.DecisionScoringConfig;

/** Factory methods shared by behavior-specific live evaluation code. */
public final class CitizenBehaviorEvaluation {
    private CitizenBehaviorEvaluation() { }

    public static CitizenActionEvaluation score(String id, String name, boolean eligible,
                                                CitizenScoreInputs inputs, String explanation,
                                                boolean emergencyOverride) {
        return score(id, name, eligible, inputs, explanation, emergencyOverride, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation score(String id, String name, boolean eligible,
                                                CitizenScoreInputs inputs, String explanation,
                                                boolean emergencyOverride, DecisionScoringConfig config) {
        var result = CitizenActionScorer.evaluate(inputs, config.costPenalty(), config.riskPenalty());
        return new CitizenActionEvaluation(id, name, eligible, eligible ? result.score() : 0.0,
                eligible && emergencyOverride, explanation, result.factors(), false, false);
    }

    public static CitizenActionEvaluation unavailable(String id, String name, String reason) {
        return score(id, name, false, new CitizenScoreInputs(0, 0, 0, 0, 0, 0), reason, false);
    }
}
