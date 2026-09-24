package com.jedts.theeconomist.citizen.behavior.decision;

import java.util.List;
import java.util.Objects;

/** A single behavior's decision result for the current Citizen context. */
public record CitizenActionEvaluation(String actionId, String displayName, boolean eligible, double score,
                                      boolean emergencyOverride, String explanation,
                                      List<CitizenDecisionFactor> factors, boolean selected, boolean active) {
    public CitizenActionEvaluation {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(explanation, "explanation");
        factors = List.copyOf(Objects.requireNonNull(factors, "factors"));
        score = Double.isFinite(score) ? Math.max(0.0, Math.min(100.0, score)) : 0.0;
        if (factors.size() > 6) throw new IllegalArgumentException("at most six score factors are supported");
        if (!eligible && selected) throw new IllegalArgumentException("an ineligible action cannot be selected");
    }

    public CitizenActionEvaluation withSelection(boolean selected, boolean active) {
        return new CitizenActionEvaluation(actionId, displayName, eligible, score, emergencyOverride,
                explanation, factors, selected, active);
    }
}
