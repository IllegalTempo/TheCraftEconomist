package com.jedts.theeconomist.citizen.farm.claim;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotClaimSavedDataTest {
    @Test void codecPreservesDifferentOwnersOnTouchingFarmland() {
        PlotOwner alice = new PlotOwner(PlotOwner.Kind.PLAYER,
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        PlotOwner citizen = new PlotOwner(PlotOwner.Kind.CITIZEN,
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        PlotClaimSavedData original = new PlotClaimSavedData(List.of(
                new PlotClaimSavedData.Entry(new BlockPos(0, 64, 0), alice),
                new PlotClaimSavedData.Entry(new BlockPos(1, 64, 0), citizen)));

        JsonElement encoded = PlotClaimSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        PlotClaimSavedData restored = PlotClaimSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(alice, restored.claims().ownerAt(new BlockPos(0, 64, 0)).orElseThrow());
        assertEquals(citizen, restored.claims().ownerAt(new BlockPos(1, 64, 0)).orElseThrow());
        assertEquals(1, restored.claims().component(new BlockPos(0, 64, 0)).size());
    }
}
