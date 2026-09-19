package com.jedts.theeconomist.citizen.identity;

import com.jedts.theeconomist.citizen.skin.ResolvedProfile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenIdentityProfileTest {
    @Test
    void appliesOnlyToTheProfileUsernameThatWasAssigned() {
        UUID citizenId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        CitizenIdentity identity = new CitizenIdentity(1, citizenId, "Ada", "Stone", CitizenLifeStage.ADULT,
                Optional.of("Example"), CitizenAppearance.fallback(citizenId));
        ResolvedProfile profile = new ResolvedProfile(UUID.fromString("00000000-0000-0000-0000-000000000302"),
                "texture", "signature", CitizenModelType.SLIM);

        assertTrue(identity.withResolvedProfile("example", profile).isEmpty());
        CitizenIdentity updated = identity.withResolvedProfile("Example", profile).orElseThrow();
        assertEquals(profile.profileId(), updated.appearance().profileId().orElseThrow());
        assertEquals(CitizenModelType.SLIM, updated.appearance().modelType());
    }
}
