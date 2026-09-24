package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.identity.CitizenAppearance;
import com.jedts.theeconomist.citizen.identity.CitizenIdentity;
import com.jedts.theeconomist.citizen.identity.CitizenLifeStage;
import com.jedts.theeconomist.citizen.identity.CitizenModelType;
import com.jedts.theeconomist.citizen.identity.CitizenGender;
import com.jedts.theeconomist.citizen.identity.CitizenFamilyRole;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenIdentityNbtTest {
    @Test
    void roundTripsLongSignedTextureValues() {
        String texture = "x".repeat(2_000);
        CitizenIdentity identity = new CitizenIdentity(
                1,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "Amina",
                "Patel",
                CitizenLifeStage.CHILD,
                Optional.of("SkinAccount"),
                new CitizenAppearance(CitizenModelType.SLIM, Optional.of(UUID.randomUUID()), Optional.of(texture), Optional.of("signature")),
                CitizenGender.FEMALE,
                CitizenFamilyRole.CHILD
        );

        CompoundTag tag = CitizenIdentityNbt.write(identity);
        CitizenIdentity restored = CitizenIdentityNbt.read(tag);

        assertEquals(identity, restored);
        assertEquals(identity.appearance().textureValue(), restored.appearance().textureValue());
        assertEquals(identity.appearance().textureSignature(), restored.appearance().textureSignature());
        assertEquals(CitizenGender.FEMALE, restored.gender());
        assertEquals(CitizenFamilyRole.CHILD, restored.familyRole());
        assertTrue(tag.contains("TheEconomistCitizen"));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        CompoundTag tag = new CompoundTag();
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 2);
        tag.put("TheEconomistCitizen", root);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CitizenIdentityNbt.read(tag));

        assertTrue(error.getMessage().contains("2"));
    }
}
