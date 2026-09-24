package com.jedts.theeconomist.client;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintGhostFallbackTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void model_without_geometry_uses_visible_fallback_for_chest() {
        assertEquals(Blocks.GLASS.defaultBlockState(),
                BlueprintGhostFallback.renderState(Blocks.CHEST.defaultBlockState(), true));
    }

    @Test
    void ordinary_block_keeps_its_own_model() {
        assertEquals(Blocks.STONE.defaultBlockState(),
                BlueprintGhostFallback.renderState(Blocks.STONE.defaultBlockState(), false));
    }
}
