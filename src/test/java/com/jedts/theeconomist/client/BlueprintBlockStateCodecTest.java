package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintBlockStateCodecTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rotates_a_directional_block_with_its_preview_position() {
        BlockState eastFacing = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        BlueprintPlacement placement = new BlueprintPlacement("minecraft:overworld", BlockPos.ZERO,
                1, false, false);

        BlockState rotated = BlueprintBlockStateCodec.transform(eastFacing, placement);

        assertEquals(Direction.SOUTH, rotated.getValue(HorizontalDirectionalBlock.FACING));
    }
}
