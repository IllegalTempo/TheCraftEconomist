package com.jedts.theeconomist.citizen.behavior.decision;

import com.jedts.theeconomist.citizen.config.DecisionScoringConfig;

import java.util.Locale;

/** Pure live-state mappings used by survival and combat behavior evaluations. */
public final class SurvivalDecisionRules {
    private SurvivalDecisionRules() { }

    public static CitizenActionEvaluation escapeLiquid(boolean inLiquid, double healthRatio) {
        return escapeLiquid(inLiquid, healthRatio, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation escapeLiquid(boolean inLiquid, double healthRatio, DecisionScoringConfig config) {
        if (!inLiquid) return CitizenBehaviorEvaluation.unavailable("escape_water", "Escape liquid", "Not in water or lava");
        double health = unit(healthRatio);
        return CitizenBehaviorEvaluation.score("escape_water", "Escape liquid", true,
                new CitizenScoreInputs(v(config,"escape_water","urgency"), v(config,"escape_water","benefit"),
                        v(config,"escape_water","capabilityBase") + v(config,"escape_water","capabilityHealth") * health,
                        v(config,"escape_water","opportunity"), v(config,"escape_water","cost"), v(config,"escape_water","risk")),
                "Immediate danger: immersed in water or lava", true, config);
    }

    public static CitizenActionEvaluation panic(boolean injured, double healthRatio, boolean attackerPresent,
                                                double attackerDistance, int bravery, int safety) {
        return panic(injured, healthRatio, attackerPresent, attackerDistance, bravery, safety, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation panic(boolean injured, double healthRatio, boolean attackerPresent,
                                                double attackerDistance, int bravery, int safety, DecisionScoringConfig config) {
        if (!injured) return CitizenBehaviorEvaluation.unavailable("panic", "Flee after injury", "No recent injury");
        double health = unit(healthRatio);
        double danger = unit(1.0 - health);
        double urgency = v(config,"panic","urgencyBase") + v(config,"panic","urgencyDanger") * danger;
        double opportunity = attackerPresent ? 1.0 - v(config,"panic","attackerOpportunityDrop")
                * unit(attackerDistance / v(config,"panic","attackerDistanceScale")) : v(config,"panic","noAttackerOpportunity");
        double capability = v(config,"panic","capabilityBase") + v(config,"panic","capabilityBraveryDrop")
                * (1.0 - unit(bravery / config.statScale()));
        double risk = v(config,"panic","riskBase") + v(config,"panic","riskDanger") * danger
                + v(config,"panic","riskSafety") * (1.0 - unit(safety / config.statScale()));
        String reason = attackerPresent
                ? String.format(Locale.ROOT, "Injured; attacker %.1f blocks away", attackerDistance)
                : "Injured; no nearby attacker found";
        return CitizenBehaviorEvaluation.score("panic", "Flee after injury", true,
                new CitizenScoreInputs(urgency, v(config,"panic","benefit"), capability, opportunity,
                        v(config,"panic","cost"), risk), reason, false, config);
    }

    public static CitizenActionEvaluation avoidMonster(boolean threatFound, double distance,
                                                       int safety, int bravery, double healthRatio) {
        return avoidMonster(threatFound, distance, safety, bravery, healthRatio, DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation avoidMonster(boolean threatFound, double distance,
                                                       int safety, int bravery, double healthRatio, DecisionScoringConfig config) {
        if (!threatFound) return CitizenBehaviorEvaluation.unavailable("avoid_monster", "Avoid monster", "No nearby monster");
        double proximity = 1.0 - unit(distance / v(config,"avoid_monster","monsterDistanceScale"));
        double safetyDeficit = 1.0 - unit(safety / config.statScale());
        double urgency = v(config,"avoid_monster","urgencyProximity") * proximity
                + v(config,"avoid_monster","urgencySafety") * safetyDeficit;
        double capability = v(config,"avoid_monster","capabilityBase")
                + v(config,"avoid_monster","capabilityBravery") * unit(bravery / config.statScale());
        double risk = v(config,"avoid_monster","riskHealth") * (1.0 - unit(healthRatio));
        return CitizenBehaviorEvaluation.score("avoid_monster", "Avoid monster", true,
                new CitizenScoreInputs(urgency, v(config,"avoid_monster","benefit"), capability,
                        v(config,"avoid_monster","opportunity"), v(config,"avoid_monster","cost"), risk),
                String.format(Locale.ROOT, "Monster %.1f blocks away; safety %d/%d", distance, safety, (int)config.statScale()), false, config);
    }

    public static CitizenActionEvaluation combat(boolean targetPresent, double distance, int anger, int bravery,
                                                  double healthRatio, double armor, double targetHealth,
                                                  double targetArmor) {
        return combat(targetPresent, distance, anger, bravery, healthRatio, armor, targetHealth, targetArmor,
                DecisionScoringConfig.defaults());
    }

    public static CitizenActionEvaluation combat(boolean targetPresent, double distance, int anger, int bravery,
                                                  double healthRatio, double armor, double targetHealth,
                                                  double targetArmor, DecisionScoringConfig config) {
        if (!targetPresent) return CitizenBehaviorEvaluation.unavailable("combat", "Defend self", "No valid target");
        double health = unit(healthRatio);
        double strength = unit((health * v(config,"combat","strengthHealth") + Math.max(0, armor)
                + bravery / v(config,"combat","braveryDivisor")) / v(config,"combat","strengthDivisor"));
        double targetStrength = unit((Math.max(0, targetHealth) + Math.max(0, targetArmor)) / v(config,"combat","targetStrengthDivisor"));
        double angerDrive = unit(anger / config.statScale());
        double advantage = unit(v(config,"combat","advantageBase")
                + (strength - targetStrength) / v(config,"combat","differenceDivisor"));
        double risk = unit((1.0 - health) * v(config,"combat","riskHealth")
                + Math.max(0, targetStrength - strength) * v(config,"combat","riskDisadvantage"));
        double opportunity = 1.0 - v(config,"combat","opportunityDistanceDrop")
                * unit(distance / v(config,"combat","targetDistanceScale"));
        return CitizenBehaviorEvaluation.score("combat", "Defend self", true,
                new CitizenScoreInputs(v(config,"combat","urgencyBase") + v(config,"combat","urgencyAnger") * angerDrive
                        + v(config,"combat","urgencyLowHealth") * (1.0 - health),
                        v(config,"combat","benefitBase") + v(config,"combat","benefitAnger") * angerDrive
                                + v(config,"combat","benefitAdvantage") * advantage, strength, opportunity,
                        v(config,"combat","costBase") + v(config,"combat","costDistance")
                                * unit(distance / v(config,"combat","targetDistanceScale")), risk),
                String.format(Locale.ROOT, "Target %.1f blocks away; combat advantage %.0f%%",
                        distance, advantage * 100.0), false, config);
    }

    private static double v(DecisionScoringConfig config, String action, String key) { return config.value(action, key); }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
