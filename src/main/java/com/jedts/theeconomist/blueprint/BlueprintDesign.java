package com.jedts.theeconomist.blueprint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record BlueprintDesign(int width, int height, int depth, List<BlueprintBlock> blocks) {
    public BlueprintDesign {
        if (width <= 0 || height <= 0 || depth <= 0 || width > BlueprintLimits.MAX_DIMENSION
                || height > BlueprintLimits.MAX_DIMENSION || depth > BlueprintLimits.MAX_DIMENSION) {
            throw new IllegalArgumentException("blueprint dimensions exceed limits");
        }
        Objects.requireNonNull(blocks);
        if (blocks.size() > BlueprintLimits.MAX_BLOCKS) throw new IllegalArgumentException("too many blueprint blocks");
        Set<String> positions = new HashSet<>();
        for (BlueprintBlock block : blocks) {
            if (block.x() >= width || block.y() >= height || block.z() >= depth) throw new IllegalArgumentException("block outside dimensions");
            if (!positions.add(block.x() + ":" + block.y() + ":" + block.z())) throw new IllegalArgumentException("duplicate block position");
        }
        blocks = List.copyOf(blocks);
    }

    public BlueprintDesign rotated(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        BlueprintDesign result = this;
        for (int i = 0; i < turns; i++) {
            BlueprintDesign current = result;
            List<BlueprintBlock> rotated = current.blocks.stream()
                    .map(block -> new BlueprintBlock(current.depth - 1 - block.z(), block.y(), block.x(), block.blockId()))
                    .toList();
            result = new BlueprintDesign(current.depth, current.height, current.width, rotated);
        }
        return result;
    }
}
