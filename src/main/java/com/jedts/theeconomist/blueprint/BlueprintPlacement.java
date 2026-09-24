package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record BlueprintPlacement(String dimension, BlockPos origin, int rotation, boolean mirrorX, boolean mirrorZ) {
    public BlueprintPlacement(BlockPos origin, int rotation, boolean mirrorX, boolean mirrorZ) {
        this("minecraft:overworld", origin, rotation, mirrorX, mirrorZ);
    }

    public BlueprintPlacement {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(origin);
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("rotation must be 0..3");
    }

    public BlockPos worldPosition(BlueprintDesign design, BlueprintBlock block) {
        int x = block.x();
        int z = block.z();
        int transformedWidth = design.width();
        int transformedDepth = design.depth();
        switch (rotation) {
            case 1 -> { x = design.depth() - 1 - block.z(); z = block.x(); transformedWidth = design.depth(); transformedDepth = design.width(); }
            case 2 -> { x = design.width() - 1 - block.x(); z = design.depth() - 1 - block.z(); }
            case 3 -> { x = block.z(); z = design.width() - 1 - block.x(); transformedWidth = design.depth(); transformedDepth = design.width(); }
            default -> { }
        }
        if (mirrorX) x = transformedWidth - 1 - x;
        if (mirrorZ) z = transformedDepth - 1 - z;
        return origin.offset(x, block.y(), z);
    }
}
