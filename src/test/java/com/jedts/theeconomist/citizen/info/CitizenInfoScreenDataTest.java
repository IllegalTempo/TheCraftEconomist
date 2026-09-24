package com.jedts.theeconomist.citizen.info;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenInfoScreenDataTest {
    @Test
    void preservesTheTwoPanelDisplayValues() {
        CitizenInfoScreenData data = new CitizenInfoScreenData("Amina Stone", "ADULT", "FEMALE", "MOM",
                "Amina Stone, Tariq Stone", "WIDE",
                "Example", "17.5/20.0", "0.3", "24.0", "10, 64, -3",
                "100", "100", "50", "50", "50", "0", "0");
        assertEquals("Amina Stone", data.name());
        assertEquals("10, 64, -3", data.position());
        assertEquals("MOM", data.familyRole());
        assertEquals("Amina Stone, Tariq Stone", data.familyMembers());
    }

    @Test
    void behaviorDisplayValuesSurviveScreenDataConstruction() {
        CitizenInfoScreenData data = new CitizenInfoScreenData("Amina Stone", "ADULT", "FEMALE", "MOM",
                "Amina Stone, Tariq Stone", "sleep", "Walking to bed", "WIDE",
                "Example", "17.5/20.0", "0.3", "24.0", "10, 64, -3",
                "100", "100", "50", "50", "50", "0", "0");
        assertEquals("sleep", data.activeBehavior());
        assertEquals("Walking to bed", data.behaviorStatus());
    }

    @Test
    void carriesAnImmutableLiveDecisionList() {
        var evaluations = new ArrayList<>(List.of(new CitizenDecisionView("sleep", "Sleep", true,
                81, false, "tired at night", List.of(), true, true)));
        CitizenInfoScreenData data = new CitizenInfoScreenData("Amina", "ADULT", "FEMALE", "NONE", "none",
                "sleep", "Resting", "WIDE", "none", "20/20", "0.3", "24", "0, 64, 0",
                "100", "20", "50", "50", "50", "0", "0", "NONE", "", "0", "", "NONE", "", "0", "",
                Optional.empty(), evaluations);
        evaluations.clear();
        assertEquals(1, data.decisions().size());
        assertEquals("sleep", data.decisions().getFirst().actionId());
    }
}
