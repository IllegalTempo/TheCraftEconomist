package com.jedts.theeconomist.citizen.info;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenInfoScreenDataTest {
    @Test
    void preservesTheTwoPanelDisplayValues() {
        CitizenInfoScreenData data = new CitizenInfoScreenData("Amina Stone", "ADULT", "WIDE",
                "Example", "17.5/20.0", "0.3", "24.0", "10, 64, -3",
                "100", "100", "50", "50", "50", "0", "0");
        assertEquals("Amina Stone", data.name());
        assertEquals("10, 64, -3", data.position());
    }
}
