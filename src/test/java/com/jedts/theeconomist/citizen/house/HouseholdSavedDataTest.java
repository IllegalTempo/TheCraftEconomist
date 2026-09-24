package com.jedts.theeconomist.citizen.house;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HouseholdSavedDataTest {
    @Test
    void legacyEntryWithoutNamesRetainsIssuedSlotsAndAcceptsRecoveredName() {
        var encoded = com.google.gson.JsonParser.parseString("""
                {"dimension":"minecraft:overworld","households":[
                  {"x":12,"y":70,"z":-4,"surname":"River","residentCount":3,"issuedSlots":1}
                ]}
                """);
        var restored = HouseholdSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var household = restored.ledger().get(new BlockPos(12, 70, -4)).orElseThrow();
        assertEquals(List.of(1, 2), household.unissuedSlots());
        assertTrue(household.usedGivenNames().isEmpty());
        restored.setDirty(false);
        household.rememberGivenName(0, "Alex");
        assertTrue(restored.isDirty());
        var saved = HouseholdSavedData.CODEC.encodeStart(JsonOps.INSTANCE, restored).getOrThrow();
        var reloaded = HouseholdSavedData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(java.util.Set.of("Alex"), reloaded.ledger().get(new BlockPos(12, 70, -4)).orElseThrow().usedGivenNames());
    }

    @Test
    void codecPreservesIssuedSlotsAndHouseholdIdentity() {
        HouseholdSavedData original = new HouseholdSavedData("minecraft:overworld");
        BlockPos anchor = new BlockPos(12, 70, -4);
        Household first = original.ledger().getOrCreate(anchor, List.of("River", "Stone"), new Random(23));
        for (int slot : first.unissuedSlots()) assertTrue(first.issue(slot));

        JsonElement encoded = HouseholdSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        HouseholdSavedData restored = HouseholdSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        Household loaded = restored.ledger().get(anchor).orElseThrow();

        assertEquals("minecraft:overworld", restored.dimensionId());
        assertEquals(first.surname(), loaded.surname());
        assertEquals(first.residentCount(), loaded.residentCount());
        assertTrue(loaded.unissuedSlots().isEmpty());
        assertFalse(loaded.issue(0));
    }
}

