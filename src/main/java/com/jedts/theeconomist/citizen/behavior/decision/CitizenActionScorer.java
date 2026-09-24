package com.jedts.theeconomist.citizen.behavior.decision;

import java.util.List;

/** Pure implementation of the shared Citizen action utility formula. */
public final class CitizenActionScorer {
    public record Result(double score, List<CitizenDecisionFactor> factors) {
        public Result {
            score = Double.isFinite(score) ? Math.max(0.0, Math.min(100.0, score)) : 0.0;
            factors = List.copyOf(factors);
        }
    }

    private CitizenActionScorer() { }

    public static Result evaluate(CitizenScoreInputs inputs) {
        return evaluate(inputs, 20.0, 20.0);
    }

    public static Result evaluate(CitizenScoreInputs inputs, double costPenaltyWeight, double riskPenaltyWeight) {
        double base = 100.0 * inputs.urgency() * inputs.benefit()
                * inputs.capability() * inputs.opportunity();
        double costPenalty = costPenaltyWeight * inputs.cost();
        double riskPenalty = riskPenaltyWeight * inputs.risk();
        double score = Math.max(0.0, Math.min(100.0, base - costPenalty - riskPenalty));
        double eachPositive = base / 4.0;
        return new Result(score, List.of(
                new CitizenDecisionFactor("urgency", inputs.urgency(), eachPositive),
                new CitizenDecisionFactor("benefit", inputs.benefit(), eachPositive),
                new CitizenDecisionFactor("capability", inputs.capability(), eachPositive),
                new CitizenDecisionFactor("opportunity", inputs.opportunity(), eachPositive),
                new CitizenDecisionFactor("cost", inputs.cost(), -costPenalty),
                new CitizenDecisionFactor("risk", inputs.risk(), -riskPenalty)));
    }
}
