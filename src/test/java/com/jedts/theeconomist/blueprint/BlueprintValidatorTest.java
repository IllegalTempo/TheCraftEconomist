package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintValidatorTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void accepts_a_bounded_design() {
        BlueprintDesign design = new BlueprintDesign(2, 2, 2,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));
        assertTrue(BlueprintValidator.validateDesign(design).valid());
    }

    @Test
    void rejects_placement_when_a_position_is_protected() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));
        BlueprintPlacement placement = new BlueprintPlacement(new BlockPos(4, 70, 4), 0, false, false);
        assertFalse(BlueprintValidator.validatePlacement(design, placement, position -> true).valid());
    }

    @Test
    void rejects_empty_design_but_accepts_previously_restricted_block_types() {
        assertFalse(BlueprintValidator.validateDesign(new BlueprintDesign(1, 1, 1, List.of())).valid());
        assertTrue(BlueprintValidator.validateDesign(new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:command_block")))).valid());
    }

    @Test
    void rejects_block_entity_payload_with_incompatible_type() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:furnace");
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:chest", "", tag)));
        assertFalse(BlueprintValidator.validateDesign(design).valid());
    }

    @Test
    void accepts_sign_entity_type_different_from_block_id() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:sign");
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:oak_sign", "", tag)));
        assertTrue(BlueprintValidator.validateDesign(design).valid());
    }

    @Test
    void rejects_unknown_block_state_property() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:oak_stairs", "not_a_property=east")));

        BlueprintValidationResult result = BlueprintValidator.validateDesign(design);

        assertFalse(result.valid());
        assertTrue(result.reason().contains("block state"));
    }
}
