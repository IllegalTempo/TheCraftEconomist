package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintDesignTest {
    @Test
    void rotates_relative_block_positions_clockwise() {
        BlueprintDesign design = new BlueprintDesign(2, 1, 3, List.of(
                new BlueprintBlock(1, 0, 2, "minecraft:oak_planks")));

        BlueprintDesign rotated = design.rotated(1);

        assertEquals(new BlueprintBlock(0, 0, 1, "minecraft:oak_planks"), rotated.blocks().getFirst());
        assertEquals(3, rotated.width());
        assertEquals(2, rotated.depth());
    }

    @Test
    void rejects_designs_over_the_limits() {
        assertThrows(IllegalArgumentException.class, () -> new BlueprintDesign(
                BlueprintLimits.MAX_DIMENSION + 1, 1, 1, List.of()));
    }

    @Test
    void placement_transforms_design_blocks_to_world_positions() {
        BlueprintDesign design = new BlueprintDesign(2, 1, 1,
                List.of(new BlueprintBlock(1, 0, 0, "minecraft:stone")));
        BlueprintPlacement placement = new BlueprintPlacement(new BlockPos(10, 64, 20), 0, false, false);

        assertEquals(new BlockPos(11, 64, 20), placement.worldPosition(design, design.blocks().getFirst()));
    }

    @Test
    void placement_applies_rotation_before_mirroring() {
        BlueprintDesign design = new BlueprintDesign(2, 1, 3,
                List.of(new BlueprintBlock(1, 0, 2, "minecraft:stone")));
        BlueprintPlacement placement = new BlueprintPlacement(new BlockPos(10, 64, 20), 1, false, false);

        assertEquals(new BlockPos(10, 64, 21), placement.worldPosition(design, design.blocks().getFirst()));
    }

    @Test
    void rotation_preserves_serialized_block_state() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:oak_stairs", "facing=east,half=bottom")));

        assertEquals("facing=east,half=bottom", design.rotated(1).blocks().getFirst().stateProperties());
    }
}
