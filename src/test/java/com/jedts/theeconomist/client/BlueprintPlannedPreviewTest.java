package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintBlock;
import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import com.jedts.theeconomist.blueprint.BlueprintStackData;
import com.jedts.theeconomist.blueprint.BlueprintState;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintPlannedPreviewTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void planned_blocks_render_at_saved_rotated_positions() {
        BlueprintDesign design = new BlueprintDesign(2, 1, 1,
                List.of(new BlueprintBlock(1, 0, 0, "minecraft:stone")));
        BlueprintPlacement placement = new BlueprintPlacement("minecraft:overworld",
                new BlockPos(10, 64, 20), 1, false, false);
        BlueprintStackData data = new BlueprintStackData(BlueprintState.PLANNED, design, placement);

        assertEquals(Blocks.STONE.defaultBlockState(),
                BlueprintPlannedPreview.blocksFor(data, "minecraft:overworld")
                        .get(new BlockPos(10, 64, 21)));
    }

    @Test
    void preview_is_hidden_in_other_dimensions_and_for_unplanned_items() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));
        BlueprintPlacement placement = new BlueprintPlacement("minecraft:overworld",
                new BlockPos(10, 64, 20), 0, false, false);

        assertTrue(BlueprintPlannedPreview.blocksFor(
                new BlueprintStackData(BlueprintState.PLANNED, design, placement),
                "minecraft:the_nether").isEmpty());
        assertTrue(BlueprintPlannedPreview.blocksFor(
                new BlueprintStackData(BlueprintState.DESIGNED, design, null),
                "minecraft:overworld").isEmpty());
    }
}
