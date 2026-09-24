package com.jedts.theeconomist.citizen.info;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenOverviewTest {
    @Test void longConfiguredNameAndProfileAreBoundedForPacket() {
        var overview = new CitizenOverview("N".repeat(300), "ADULT", "ALEX", "P".repeat(300),
                "20/20", "0.2", "24", "0, 64, 0", 50, 50, 50, 50, 50, 50, 50);
        assertEquals(128, overview.name().length());
        assertEquals(128, overview.profile().length());
    }

    @Test void behaviorPacketValuesAreBounded() {
        var overview = new CitizenOverview("Amina", "ADULT", "FEMALE", "MOM", "family",
                "B".repeat(80), "S".repeat(200), "WIDE", "none", "20/20", "0.2", "24", "0, 64, 0",
                50, 50, 50, 50, 50, 50, 50);
        assertEquals(32, overview.activeBehavior().length());
        assertEquals(128, overview.behaviorStatus().length());
    }
}
