package com.jedts.theeconomist.client;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Supplies visible geometry for blocks drawn only by block-entity renderers. */
final class BlueprintGhostFallback {
    private BlueprintGhostFallback() { }

    static BlockState renderState(BlockState savedState, boolean modelHasNoParts) {
        return modelHasNoParts ? Blocks.GLASS.defaultBlockState() : savedState;
    }
}
