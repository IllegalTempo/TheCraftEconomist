package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.identity.CitizenAppearance;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenLifeStage;
import com.jedts.theeconomist.citizen.identity.CitizenModelType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenInfoFormatterTest {
    @Test
    void includesIdentityAndLiveStats() {
        CitizenIdentity identity = new CitizenIdentity(1, UUID.fromString("00000000-0000-0000-0000-000000000401"),
                "Amina", "Stone", CitizenLifeStage.ADULT, Optional.of("Example"),
                new CitizenAppearance(CitizenModelType.WIDE, Optional.empty(), Optional.empty(), Optional.empty()));
        String info = CitizenInfoFormatter.format(identity, 17.5f, 20.0f, 0.25, 24.0, 10, 64, -3);
        assertTrue(info.contains("Amina Stone"));
        assertTrue(info.contains("ADULT"));
        assertTrue(info.contains("Health: 17.5/20.0"));
        assertTrue(info.contains("Profile: Example"));
        assertTrue(info.contains("Position: 10, 64, -3"));
    }
}
