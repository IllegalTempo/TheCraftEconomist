package com.jedts.theeconomist.citizen.behavior.decision;

/** Normalized live inputs to the common action utility formula. */
public record CitizenScoreInputs(double urgency, double benefit, double capability, double opportunity,
                                 double cost, double risk) {
    public CitizenScoreInputs {
        urgency = normalize(urgency);
        benefit = normalize(benefit);
        capability = normalize(capability);
        opportunity = normalize(opportunity);
        cost = normalize(cost);
        risk = normalize(risk);
    }

    private static double normalize(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
