package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintBlock;
import com.jedts.theeconomist.blueprint.BlueprintBlockSnapshot;
import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import com.jedts.theeconomist.blueprint.BlueprintStackData;
import com.jedts.theeconomist.blueprint.BlueprintState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Projects saved blueprint blocks into a client-only world-space render map. */
public final class BlueprintPlannedPreview {
    private BlueprintPlannedPreview() { }

    public static Map<BlockPos, BlockState> blocksFor(BlueprintStackData data, String dimension) {
        if (data.state() != BlueprintState.PLANNED || data.design() == null
                || data.placement() == null || !data.placement().dimension().equals(dimension)) {
            return Map.of();
        }
        return project(data.design(), data.placement());
    }

    static Map<BlockPos, BlockState> project(BlueprintDesign design, BlueprintPlacement placement) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlueprintBlock block : design.blocks()) {
            BlueprintBlockStateCodec.decode(new BlueprintBlockSnapshot(block.blockId(), block.stateProperties()))
                    .ifPresent(state -> blocks.put(placement.worldPosition(design, block),
                            BlueprintBlockStateCodec.transform(state, placement)));
        }
        return blocks;
    }
}
