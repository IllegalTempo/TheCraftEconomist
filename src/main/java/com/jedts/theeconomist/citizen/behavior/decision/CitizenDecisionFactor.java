package com.jedts.theeconomist.citizen.behavior.decision;

import java.util.Objects;

/** One live score input and its signed contribution to the displayed score. */
public record CitizenDecisionFactor(String id, double value, double scoreContribution) {
    public CitizenDecisionFactor {
        Objects.requireNonNull(id, "id");
        value = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
        scoreContribution = Double.isFinite(scoreContribution) ? scoreContribution : 0.0;
    }
}
