package com.jedts.theeconomist.citizen.behavior.decision;

import com.jedts.theeconomist.citizen.config.DecisionScoringConfig;

import java.util.Locale;

/** Pure live-state mappings used by social, leisure, and fallback actions. */
public final class AmbientDecisionRules {
    private AmbientDecisionRules() { }

    public static CitizenActionEvaluation lookAtCreature(boolean daytime, boolean creaturePresent, double distance,
                                                          DecisionScoringConfig config) {
        if (!daytime) return CitizenBehaviorEvaluation.unavailable("look_at_creature", "Look at creature", "It is sleeping time");
        if (!creaturePresent) return CitizenBehaviorEvaluation.unavailable("look_at_creature", "Look at creature", "No nearby creature");
        double closeness = 1.0 - unit(distance / v(config, "look_at_creature", "creatureDistanceScale"));
        return CitizenBehaviorEvaluation.score("look_at_creature", "Look at creature", true,
                new CitizenScoreInputs(v(config, "look_at_creature", "urgencyBase")
                                + v(config, "look_at_creature", "urgencyCloseness") * closeness,
                        v(config, "look_at_creature", "benefitBase"),
                        v(config, "look_at_creature", "capabilityBase"),
                        v(config, "look_at_creature", "opportunityCloseness") * closeness,
                        v(config, "look_at_creature", "costBase") + v(config, "look_at_creature", "costDistance")
                                * unit(distance / v(config, "look_at_creature", "costDistanceScale")),
                        v(config, "look_at_creature", "risk")),
                String.format(Locale.ROOT, "Nearby creature %.1f blocks away", distance), false, config);
    }

    public static CitizenActionEvaluation wander(boolean daytime, boolean retryReady, int morale,
                                                  int safety, boolean movementSpace) {
        return wander(daytime, retryReady, morale, safety, movementSpace, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation wander(boolean daytime, boolean retryReady, int morale,
                                                  int safety, boolean movementSpace, DecisionScoringConfig config) {
        if (!daytime) return CitizenBehaviorEvaluation.unavailable("wander", "Walk nearby", "It is sleeping time");
        if (!retryReady) return CitizenBehaviorEvaluation.unavailable("wander", "Walk nearby", "Wander retry interval has not elapsed");
        double moraleDeficit = 1.0 - unit(morale / config.statScale());
        double safetyDeficit = 1.0 - unit(safety / config.statScale());
        return CitizenBehaviorEvaluation.score("wander", "Walk nearby", true,
                new CitizenScoreInputs(v(config,"wander","urgencyBase")
                        + v(config,"wander","urgencyMoraleDeficit") * moraleDeficit
                        + v(config,"wander","urgencySafetyDeficit") * safetyDeficit,
                        v(config,"wander","benefit"), v(config,"wander","capability"),
                        movementSpace ? v(config,"wander","movementOpportunity") : 0.0,
                        v(config,"wander","cost"), v(config,"wander","risk")),
                movementSpace ? String.format(Locale.ROOT, "Morale %d/%d; safety %d/%d", morale,
                        (int)config.statScale(), safety, (int)config.statScale())
                        : "No movement space is available", false, config);
    }

    public static CitizenActionEvaluation idle(boolean daytime) {
        return idle(daytime, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation idle(boolean daytime, DecisionScoringConfig config) {
        return CitizenBehaviorEvaluation.score("idle", "Idle", true,
                new CitizenScoreInputs(v(config,"idle","urgency"), v(config,"idle","benefit"),
                        v(config,"idle","capability"), v(config,"idle","opportunity"),
                        v(config,"idle","cost"), v(config,"idle","risk")),
                daytime ? "No higher-value action is currently available" : "Waiting through the night", false, config);
    }

    private static double v(DecisionScoringConfig config, String action, String key) { return config.value(action, key); }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
