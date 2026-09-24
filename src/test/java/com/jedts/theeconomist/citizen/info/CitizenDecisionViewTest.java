package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenDecisionFactor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenDecisionViewTest {
    @Test
    void viewCopiesEvaluationAndBoundsNetworkText() {
        var source = new CitizenActionEvaluation("a".repeat(40), "Action".repeat(20), true, 73.5,
                false, "why".repeat(60), List.of(new CitizenDecisionFactor("urgency", .8, 12)), true, true);
        var view = CitizenDecisionView.from(source);
        assertEquals(32, view.actionId().length());
        assertEquals(64, view.displayName().length());
        assertEquals(128, view.explanation().length());
        assertEquals(73.5, view.score());
        assertTrue(view.selected() && view.active());
    }

    @Test
    void rejectsInvalidNetworkScoresAndOversizedFactorLists() {
        assertThrows(IllegalArgumentException.class, () -> new CitizenDecisionView(
                "farmer", "Farm", true, Double.NaN, false, "why", List.of(), false, false));
        assertThrows(IllegalArgumentException.class, () -> new CitizenDecisionView(
                "farmer", "Farm", true, 101, false, "why", List.of(), false, false));
        var factors = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> new CitizenDecisionFactor("f" + index, .5, 1)).toList();
        assertThrows(IllegalArgumentException.class, () -> new CitizenDecisionView(
                "farmer", "Farm", true, 50, false, "why", factors, false, false));
    }
}
