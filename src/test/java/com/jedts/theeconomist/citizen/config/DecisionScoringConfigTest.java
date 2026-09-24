package com.jedts.theeconomist.citizen.config;

import org.junit.jupiter.api.Test;
import com.jedts.theeconomist.citizen.behavior.decision.RoutineDecisionRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DecisionScoringConfigTest {
    @Test
    void existingConfigGetsCompleteScoringDefaultsAndEncodesThem() {
        CitizenConfig config = CitizenConfigCodec.decode("""
                {"profileUsernames": [], "givenNames": ["Amina"], "familyNames": ["Patel"]}
                """);
        assertEquals(20, config.decisionScoring().costPenalty());
        assertEquals(20, config.decisionScoring().riskPenalty());
        assertEquals(5, config.decisionScoring().switchingMargin());
        assertEquals(32, config.decisionScoring().value("farmer", "workRadius"));
        assertTrue(CitizenConfigCodec.encode(config).contains("\"decisionScoring\""));
    }

    @Test
    void partialDecisionOverridesMergeWithDefaultsAndRoundTrip() {
        String json = """
                {"profileUsernames": [], "givenNames": ["Amina"], "familyNames": ["Patel"],
                 "decisionScoring": {"costPenalty": 31, "switchingMargin": 8,
                   "actions": {"farmer": {"urgencyAmbition": 0.7}}}}
                """;
        var config = CitizenConfigCodec.decode(json);
        assertEquals(31, config.decisionScoring().costPenalty());
        assertEquals(8, config.decisionScoring().switchingMargin());
        assertEquals(.7, config.decisionScoring().value("farmer", "urgencyAmbition"));
        assertEquals(config, CitizenConfigCodec.decode(CitizenConfigCodec.encode(config)));
        assertEquals(32, config.decisionScoring().value("farmer", "workRadius"));
    }

    @Test
    void rejectsUnknownAndOutOfRangeScoringValues() {
        String prefix = "{\"profileUsernames\":[],\"givenNames\":[\"A\"],\"familyNames\":[\"B\"],\"decisionScoring\":%s}";
        assertThrows(IllegalArgumentException.class, () -> CitizenConfigCodec.decode(prefix.formatted(
                "{\"actions\":{\"farmer\":{\"notASetting\":1}}}")));
        assertThrows(IllegalArgumentException.class, () -> CitizenConfigCodec.decode(prefix.formatted(
                "{\"costPenalty\":-1}")));
        assertThrows(IllegalArgumentException.class, () -> CitizenConfigCodec.decode(prefix.formatted(
                "{\"switchingMargin\":101}")));
    }

    @Test
    void actionSettingsChangeLiveDecisionScores() {
        var baseline = DecisionScoringConfig.defaults();
        var tuned = CitizenConfigCodec.decode("""
                {"profileUsernames": [], "givenNames": ["Amina"], "familyNames": ["Patel"],
                 "decisionScoring": {"actions": {"farmer": {"urgencyAmbition": 0.9}}}}
                """).decisionScoring();
        var before = RoutineDecisionRules.farming(true, true, true, true, 100, 65,
                true, true, true, 2, baseline);
        var after = RoutineDecisionRules.farming(true, true, true, true, 100, 65,
                true, true, true, 2, tuned);
        assertTrue(after.score() > before.score());
    }
}
