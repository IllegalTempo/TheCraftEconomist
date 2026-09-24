package com.jedts.theeconomist.citizen.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CitizenConfigCodec {
    private static final Set<String> FIELDS = Set.of("profileUsernames", "givenNames", "familyNames", "decisionScoring");

    private CitizenConfigCodec() {
    }

    public static boolean hasDecisionScoringSection(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        return parsed.isJsonObject() && parsed.getAsJsonObject().has("decisionScoring");
    }

    public static boolean hasLegacyLookAtPlayerAction(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) return false;
        JsonElement scoring = parsed.getAsJsonObject().get("decisionScoring");
        if (scoring == null || !scoring.isJsonObject()) return false;
        JsonElement actions = scoring.getAsJsonObject().get("actions");
        return actions != null && actions.isJsonObject() && actions.getAsJsonObject().has("look_at_player");
    }

    public static CitizenConfig decode(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Citizen config root must be an object");
            }
            JsonObject object = parsed.getAsJsonObject();
            for (String field : object.keySet()) {
                if (!FIELDS.contains(field)) {
                    throw new IllegalArgumentException("Unknown Citizen config field: " + field);
                }
            }
            List<String> profiles = normalizeProfiles(readStrings(object, "profileUsernames"));
            List<String> given = normalizeNames(readStrings(object, "givenNames"), "givenNames");
            List<String> family = normalizeNames(readStrings(object, "familyNames"), "familyNames");
            JsonElement scoringElement = object.get("decisionScoring");
            if (scoringElement != null && !scoringElement.isJsonObject())
                throw new IllegalArgumentException("decisionScoring must be an object");
            var scoring = scoringElement == null ? DecisionScoringConfig.defaults()
                    : DecisionScoringConfig.decode(scoringElement.getAsJsonObject());
            return new CitizenConfig(profiles, given, family, scoring);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Citizen configuration: " + exception.getMessage(), exception);
        }
    }

    public static String encode(CitizenConfig config) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"profileUsernames\": ").append(array(config.profileUsernames())).append(",\n");
        json.append("  \"givenNames\": ").append(array(config.givenNames())).append(",\n");
        json.append("  \"familyNames\": ").append(array(config.familyNames())).append(",\n");
        String scoringJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                .toJson(config.decisionScoring().encode());
        json.append("  \"decisionScoring\": ").append(scoringJson.replace("\n", "\n  ")).append("\n}\n");
        return json.toString();
    }

    private static List<String> readStrings(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(field + " entries must be strings");
            }
            values.add(value.getAsString());
        }
        return values;
    }

    private static List<String> normalizeProfiles(List<String> raw) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : raw) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<String> normalizeNames(List<String> raw, String field) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : raw) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(field + " must contain at least one usable name");
        }
        return List.copyOf(unique);
    }

    private static String array(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }
}
