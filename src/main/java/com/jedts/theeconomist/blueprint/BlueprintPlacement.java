package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record BlueprintPlacement(BlockPos origin, int rotation, boolean mirrorX, boolean mirrorZ) {
    public BlueprintPlacement {
        Objects.requireNonNull(origin);
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("rotation must be 0..3");
    }

    public BlockPos worldPosition(BlueprintDesign design, BlueprintBlock block) {
        BlueprintDesign transformed = design.rotated(rotation);
        int x = block.x();
        int z = block.z();
        if (mirrorX) x = transformed.width() - 1 - x;
        if (mirrorZ) z = transformed.depth() - 1 - z;
        return origin.offset(x, block.y(), z);
    }
}
