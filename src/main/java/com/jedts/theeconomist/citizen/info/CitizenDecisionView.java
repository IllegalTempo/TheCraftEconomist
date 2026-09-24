package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenDecisionFactor;

import java.util.List;
import java.util.Objects;

/** Bounded, network-safe snapshot of one live citizen action decision. */
public record CitizenDecisionView(String actionId, String displayName, boolean eligible, double score,
                                  boolean emergencyOverride, String explanation,
                                  List<CitizenDecisionFactor> factors, boolean selected, boolean active) {
    public CitizenDecisionView {
        actionId = bounded(actionId, 32);
        displayName = bounded(displayName, 64);
        explanation = bounded(explanation, 128);
        Objects.requireNonNull(factors, "factors");
        if (factors.size() > 6) throw new IllegalArgumentException("at most six score factors are supported");
        factors = factors.stream().map(factor -> new CitizenDecisionFactor(bounded(factor.id(), 32),
                factor.value(), factor.scoreContribution())).toList();
        if (!Double.isFinite(score) || score < 0 || score > 100)
            throw new IllegalArgumentException("score must be finite and between 0 and 100");
        if (!eligible && selected) throw new IllegalArgumentException("an ineligible action cannot be selected");
    }

    public static CitizenDecisionView from(CitizenActionEvaluation evaluation) {
        return new CitizenDecisionView(evaluation.actionId(), evaluation.displayName(), evaluation.eligible(),
                evaluation.score(), evaluation.emergencyOverride(), evaluation.explanation(), evaluation.factors(),
                evaluation.selected(), evaluation.active());
    }

    private static String bounded(String value, int limit) {
        Objects.requireNonNull(value, "text");
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
