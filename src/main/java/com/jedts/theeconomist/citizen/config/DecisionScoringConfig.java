package com.jedts.theeconomist.citizen.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable score-tuning snapshot loaded from the citizen JSON configuration. */
public final class DecisionScoringConfig {
    private static final Set<String> POSITIVE_DENOMINATORS = Set.of(
            "attackerDistanceScale", "monsterDistanceScale", "strengthDivisor", "braveryDivisor",
            "differenceDivisor", "targetStrengthDivisor", "targetDistanceScale",
            "sleepCostDistanceScale", "workRadius", "deadlineScale", "homeDistanceScale",
            "returnHomeRadius", "creatureRange", "creatureDistanceScale", "costDistanceScale",
            "threatSearchRange", "retryTicks", "reviewIntervalTicks");

    private final double costPenalty;
    private final double riskPenalty;
    private final double switchingMargin;
    private final double statScale;
    private final Map<String, Map<String, Double>> actions;

    private DecisionScoringConfig(double costPenalty, double riskPenalty, double switchingMargin, double statScale,
                                   Map<String, Map<String, Double>> actions) {
        this.costPenalty = bounded("costPenalty", costPenalty, 0, 1000);
        this.riskPenalty = bounded("riskPenalty", riskPenalty, 0, 1000);
        this.switchingMargin = bounded("switchingMargin", switchingMargin, 0, 100);
        this.statScale = bounded("statScale", statScale, 1, 1000);
        if (statScale != Math.rint(statScale)) throw new IllegalArgumentException("decisionScoring.statScale must be an integer");
        this.actions = actions.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        for (var action : this.actions.entrySet()) for (String key : action.getValue().keySet())
            if (POSITIVE_DENOMINATORS.contains(key) && action.getValue().get(key) <= 0)
                throw new IllegalArgumentException("decisionScoring.actions." + action.getKey() + "." + key + " must be greater than zero");
        for (var action : this.actions.entrySet()) for (String key : Set.of("retryTicks", "reviewIntervalTicks")) {
            Double value = action.getValue().get(key);
            if (value != null && value != Math.rint(value))
                throw new IllegalArgumentException("decisionScoring.actions." + action.getKey() + "." + key + " must be an integer");
        }
    }

    public static DecisionScoringConfig defaults() {
        return new DecisionScoringConfig(20, 20, 5, 100, defaultActions());
    }

    public static DecisionScoringConfig decode(JsonObject object) {
        DecisionScoringConfig defaults = defaults();
        rejectUnknown(object, Set.of("costPenalty", "riskPenalty", "switchingMargin", "statScale", "actions"), "decisionScoring");
        double costPenalty = readNumber(object, "costPenalty", defaults.costPenalty);
        double riskPenalty = readNumber(object, "riskPenalty", defaults.riskPenalty);
        double switchingMargin = readNumber(object, "switchingMargin", defaults.switchingMargin);
        double statScale = readNumber(object, "statScale", defaults.statScale);
        Map<String, Map<String, Double>> actions = new LinkedHashMap<>(defaults.actions);
        JsonElement actionsElement = object.get("actions");
        if (actionsElement != null) {
            if (!actionsElement.isJsonObject()) throw new IllegalArgumentException("decisionScoring.actions must be an object");
            JsonObject actionObject = actionsElement.getAsJsonObject();
            var actionIds = new java.util.ArrayList<>(actionObject.keySet());
            actionIds.sort(java.util.Comparator.comparingInt(id -> id.equals("look_at_player") ? 0 : 1));
            for (String actionId : actionIds) {
                boolean legacyLookAtPlayer = actionId.equals("look_at_player");
                String targetActionId = legacyLookAtPlayer ? "look_at_creature" : actionId;
                Map<String, Double> current = defaults.actions.get(targetActionId);
                if (current == null) throw new IllegalArgumentException("Unknown decision action: " + actionId);
                JsonElement paramsElement = actionObject.get(actionId);
                if (!paramsElement.isJsonObject()) throw new IllegalArgumentException("decisionScoring.actions." + actionId + " must be an object");
                JsonObject params = paramsElement.getAsJsonObject();
                Map<String, Double> merged = new LinkedHashMap<>(actions.get(targetActionId));
                for (String key : params.keySet()) {
                    String targetKey = legacyLookAtPlayer ? legacyCreatureKey(key) : key;
                    if (targetKey == null) continue;
                    if (!current.containsKey(targetKey))
                        throw new IllegalArgumentException("Unknown decisionScoring.actions." + actionId + " field: " + key);
                    JsonElement element = params.get(key);
                    if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
                        throw new IllegalArgumentException("decisionScoring.actions." + actionId + "." + key + " must be a number");
                    merged.put(targetKey, bounded("decisionScoring.actions." + actionId + "." + key,
                            element.getAsDouble(), 0, 1000));
                }
                actions.put(targetActionId, merged);
            }
        }
        return new DecisionScoringConfig(costPenalty, riskPenalty, switchingMargin, statScale, actions);
    }

    public double costPenalty() { return costPenalty; }
    public double riskPenalty() { return riskPenalty; }
    public double switchingMargin() { return switchingMargin; }
    public double statScale() { return statScale; }
    public Map<String, Map<String, Double>> actions() { return actions; }

    public double value(String actionId, String key) {
        Map<String, Double> values = actions.get(actionId);
        if (values == null || !values.containsKey(key)) throw new IllegalArgumentException("Unknown decision setting: " + actionId + "." + key);
        return values.get(key);
    }

    public JsonObject encode() {
        JsonObject object = new JsonObject();
        object.addProperty("costPenalty", costPenalty);
        object.addProperty("riskPenalty", riskPenalty);
        object.addProperty("switchingMargin", switchingMargin);
        object.addProperty("statScale", statScale);
        JsonObject actionsObject = new JsonObject();
        for (var action : actions.entrySet()) {
            JsonObject params = new JsonObject();
            action.getValue().forEach(params::addProperty);
            actionsObject.add(action.getKey(), params);
        }
        object.add("actions", actionsObject);
        return object;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DecisionScoringConfig that)) return false;
        return Double.compare(costPenalty, that.costPenalty) == 0
                && Double.compare(riskPenalty, that.riskPenalty) == 0
                && Double.compare(switchingMargin, that.switchingMargin) == 0
                && Double.compare(statScale, that.statScale) == 0 && actions.equals(that.actions);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(costPenalty, riskPenalty, switchingMargin, statScale, actions);
    }

    @Override
    public String toString() {
        return "DecisionScoringConfig[costPenalty=" + costPenalty + ", riskPenalty=" + riskPenalty
                + ", switchingMargin=" + switchingMargin + ", statScale=" + statScale + ", actions=" + actions + "]";
    }

    private static double readNumber(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null) return fallback;
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("decisionScoring." + key + " must be a number");
        return bounded("decisionScoring." + key, element.getAsDouble(), 0,
                key.equals("switchingMargin") ? 100 : 1000);
    }

    private static double bounded(String label, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(label + " must be finite and between " + min + " and " + max);
        return value;
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed, String prefix) {
        for (String key : object.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unknown " + prefix + " field: " + key);
    }

    private static Map<String, Map<String, Double>> defaultActions() {
        Map<String, Map<String, Double>> actions = new LinkedHashMap<>();
        put(actions, "escape_water", "urgency", 1, "benefit", 1, "capabilityBase", .7, "capabilityHealth", .3,
                "opportunity", 1, "cost", 0, "risk", 0);
        put(actions, "panic", "urgencyBase", .55, "urgencyDanger", .45, "benefit", .9,
                "attackerDistanceScale", 24, "attackerOpportunityDrop", .4, "noAttackerOpportunity", .65,
                "capabilityBase", .65, "capabilityBraveryDrop", .35, "cost", .2,
                "riskBase", .15, "riskDanger", .3, "riskSafety", .15);
        put(actions, "avoid_monster", "monsterDistanceScale", 16, "urgencyProximity", .6, "urgencySafety", .4,
                "benefit", .9, "capabilityBase", .65, "capabilityBravery", .35, "opportunity", 1,
                "cost", .25, "riskHealth", .5, "threatSearchRange", 8);
        put(actions, "combat", "strengthHealth", 10, "braveryDivisor", 20, "strengthDivisor", 20,
                "targetStrengthDivisor", 20, "advantageBase", .5, "differenceDivisor", 2,
                "riskHealth", .6, "riskDisadvantage", .4, "targetDistanceScale", 32,
                "opportunityDistanceDrop", .5, "urgencyBase", .45, "urgencyAnger", .35,
                "urgencyLowHealth", .2, "benefitBase", .45, "benefitAnger", .35,
                "benefitAdvantage", .2, "costBase", .2, "costDistance", .3);
        put(actions, "sleep", "urgencyBase", .35, "urgencyTiredness", .65, "benefit", .9,
                "capability", .85, "bedOpportunity", 1, "costBase", .1, "costDistance", .3,
                "sleepCostDistanceScale", 32, "risk", .05);
        put(actions, "confused", "urgency", .8, "benefit", .65, "capability", 1,
                "opportunity", 1, "cost", .1, "risk", 0);
        put(actions, "farmer", "urgencyBase", .4, "urgencyAmbition", .35, "benefit", .85,
                "skillBase", .55, "skillShare", .45, "opportunity", 1,
                "cost", .15, "risk", .1, "workRadius", 256);
        put(actions, "contract", "urgencyBase", .3, "urgencyAmbition", .4, "urgencyDeadline", .3,
                "deadlineScale", 200, "benefit", .7, "capabilityBase", .75,
                "capabilityAmbition", .25, "capabilityCannotWork", .6, "offerOpportunity", 1,
                "routineOpportunity", .45, "cost", .1, "risk", .05, "reviewIntervalTicks", 200);
        put(actions, "return_home", "returnHomeRadius", 16, "homeDistanceScale", 64,
                "urgencyBase", .2, "urgencyTravel", .4, "urgencyDanger", .25, "urgencyNight", .15,
                "dayBenefit", .75, "nightBenefit", .85, "capability", .85, "opportunity", 1,
                "costBase", .15, "costTravel", .45, "riskDanger", .1);
        put(actions, "look_at_creature", "creatureRange", 8, "creatureDistanceScale", 16,
                "urgencyBase", .1, "urgencyCloseness", .25, "benefitBase", .4,
                "capabilityBase", .7, "opportunityCloseness", .85,
                "costBase", .01, "costDistance", .04, "costDistanceScale", 8, "risk", 0);
        put(actions, "wander", "urgencyBase", .15, "urgencyMoraleDeficit", .5, "urgencySafetyDeficit", .35,
                "benefit", .6, "capability", .85, "movementOpportunity", .75,
                "cost", .25, "risk", .15, "retryTicks", 100);
        put(actions, "idle", "urgency", .1, "benefit", .5, "capability", 1,
                "opportunity", 1, "cost", .15, "risk", 0);
        for (Map<String, Double> values : actions.values())
            for (String key : values.keySet()) if (POSITIVE_DENOMINATORS.contains(key) && values.get(key) <= 0)
                throw new IllegalStateException("Default denominator must be positive: " + key);
        return Map.copyOf(actions);
    }

    private static void put(Map<String, Map<String, Double>> target, String action, Object... pairs) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        target.put(action, Map.copyOf(values));
    }

    private static String legacyCreatureKey(String key) {
        return switch (key) {
            case "playerRange" -> "creatureRange";
            case "playerDistanceScale" -> "creatureDistanceScale";
            case "urgencySociability", "benefitSociability", "capabilitySociability" -> null;
            case "urgencyBase", "urgencyCloseness", "benefitBase", "capabilityBase",
                    "opportunityCloseness", "costBase", "costDistance", "costDistanceScale", "risk" -> key;
            default -> throw new IllegalArgumentException("Unknown decisionScoring.actions.look_at_player field: " + key);
        };
    }
}
