package com.jedts.theeconomist.citizen.identity;

import com.jedts.theeconomist.citizen.config.CitizenConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenIdentityFactoryTest {
    @Test
    void generatedIdentityUsesNamesButNeverUsesProfileAsDisplayName() {
        CitizenConfig config = new CitizenConfig(
                List.of("SkinAccount"), List.of("Amina"), List.of("Patel")
        );
        UUID citizenId = UUID.fromString("00000000-0000-0000-0000-000000000011");

        CitizenIdentity identity = new CitizenIdentityFactory(new Random(7)).create(citizenId, config);

        assertEquals(1, identity.schemaVersion());
        assertEquals(citizenId, identity.citizenId());
        assertEquals("Amina", identity.givenName());
        assertEquals("Patel", identity.familyName());
        assertEquals("Amina Patel", identity.displayName());
        assertEquals(CitizenLifeStage.ADULT, identity.lifeStage());
        assertEquals(Optional.of("SkinAccount"), identity.profileUsername());
        assertNotEquals("SkinAccount", identity.displayName());
        assertTrue(identity.gender() == CitizenGender.MALE || identity.gender() == CitizenGender.FEMALE);
    }

    @Test
    void emptyProfilePoolUsesFallbackAppearance() {
        CitizenIdentity identity = new CitizenIdentityFactory(new Random(1)).create(
                UUID.randomUUID(), new CitizenConfig(List.of(), List.of("Amina"), List.of("Patel")));

        assertEquals(Optional.empty(), identity.profileUsername());
        assertEquals(Optional.empty(), identity.appearance().textureValue());
    }

    @Test
    void fallbackModelIsStableAndSupportsBothPlayerShapes() {
        UUID slimId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID wideId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals(CitizenModelType.SLIM, CitizenModelType.forFallback(slimId));
        assertEquals(CitizenModelType.WIDE, CitizenModelType.forFallback(wideId));
        assertEquals(CitizenModelType.forFallback(slimId), CitizenModelType.forFallback(slimId));
    }

    @Test
    void identityRejectsUnsupportedOrBlankPersistedValues() {
        assertThrows(IllegalArgumentException.class, () -> new CitizenIdentity(
                2, UUID.randomUUID(), "Amina", "Patel", CitizenLifeStage.ADULT,
                Optional.empty(), CitizenAppearance.fallback(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> new CitizenIdentity(
                1, UUID.randomUUID(), " ", "Patel", CitizenLifeStage.ADULT,
                Optional.empty(), CitizenAppearance.fallback(UUID.randomUUID())));
    }

    @Test
    void houseIdentityUsesHouseSurnameAndPrefersUnusedGivenNames() {
        UUID citizenId = UUID.randomUUID();
        CitizenConfig config = new CitizenConfig(List.of("SkinAccount"),
                List.of("Amina", "Noor"), List.of("Patel", "River"));

        CitizenIdentity identity = new CitizenIdentityFactory(new Random(4))
                .createForHouse(citizenId, config, "River", Set.of("Amina"));

        assertEquals(citizenId, identity.citizenId());
        assertEquals("Noor", identity.givenName());
        assertEquals("River", identity.familyName());
        assertEquals(CitizenLifeStage.ADULT, identity.lifeStage());
        assertEquals(Optional.of("SkinAccount"), identity.profileUsername());
        assertEquals(CitizenAppearance.fallback(citizenId), identity.appearance());
        assertTrue(identity.gender() == CitizenGender.MALE || identity.gender() == CitizenGender.FEMALE);
    }

    @Test
    void houseIdentityFallsBackToFullGivenNamePoolWhenAllNamesUsed() {
        CitizenConfig config = new CitizenConfig(List.of(), List.of("Amina"), List.of("Patel"));

        CitizenIdentity identity = new CitizenIdentityFactory(new Random(4))
                .createForHouse(UUID.randomUUID(), config, "River", Set.of("Amina"));

        assertEquals("Amina", identity.givenName());
        assertEquals("River", identity.familyName());
    }
}
