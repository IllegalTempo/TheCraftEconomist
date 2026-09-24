package com.jedts.theeconomist.blueprint;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlueprintStackDataTest {
    @Test
    void chest_data_survives_item_round_trip_without_exposing_mutable_tag() {
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putString("CustomName", "Supplies");
        BlueprintBlock block = new BlueprintBlock(0, 0, 0, "minecraft:chest", "facing=north", chest);
        chest.putString("CustomName", "Changed after capture");

        BlueprintDesign design = new BlueprintDesign(1, 1, 1, List.of(block));
        BlueprintDesign loaded = BlueprintStackData.readTag(BlueprintStackData.writeTag(
                new BlueprintStackData(BlueprintState.DESIGNED, design, null))).design();
        assertEquals("Supplies", loaded.blocks().getFirst().blockEntityData().getStringOr("CustomName", ""));
        loaded.blocks().getFirst().blockEntityData().putString("CustomName", "Mutated copy");
        assertEquals("Supplies", loaded.blocks().getFirst().blockEntityData().getStringOr("CustomName", ""));
    }

    @Test
    void legacy_design_without_entity_payload_remains_readable() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));
        BlueprintStackData loaded = BlueprintStackData.readTag(BlueprintStackData.writeTag(
                new BlueprintStackData(BlueprintState.DESIGNED, design, null)));
        assertEquals(design, loaded.design());
        assertNull(loaded.design().blocks().getFirst().blockEntityData());
    }

    @Test
    void designed_and_planned_data_round_trip_on_an_item_stack() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:oak_planks", "facing=north")));
        BlueprintStackData designed = new BlueprintStackData(BlueprintState.DESIGNED, design, null);
        CompoundTag tag = BlueprintStackData.writeTag(designed);
        BlueprintStackData planned = new BlueprintStackData(BlueprintState.PLANNED, design,
                new BlueprintPlacement(new net.minecraft.core.BlockPos(5, 70, -2), 2, true, false));
        BlueprintStackData loaded = BlueprintStackData.readTag(BlueprintStackData.writeTag(planned));

        assertEquals(BlueprintState.PLANNED, loaded.state());
        assertEquals(design, loaded.design());
        assertEquals(new net.minecraft.core.BlockPos(5, 70, -2), loaded.placement().origin());
        assertEquals(2, loaded.placement().rotation());
        assertEquals(true, loaded.placement().mirrorX());
        assertEquals("facing=north", loaded.design().blocks().getFirst().stateProperties());
    }
}
