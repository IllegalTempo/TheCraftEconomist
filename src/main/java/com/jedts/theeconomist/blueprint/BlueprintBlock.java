package com.jedts.theeconomist.blueprint;

import java.util.Objects;

public record BlueprintBlock(int x, int y, int z, String blockId, String stateProperties) {
    public BlueprintBlock(int x, int y, int z, String blockId) {
        this(x, y, z, blockId, "");
    }

    public BlueprintBlock {
        Objects.requireNonNull(blockId);
        Objects.requireNonNull(stateProperties);
        if (blockId.isBlank()) throw new IllegalArgumentException("block id must not be blank");
        if (x < 0 || y < 0 || z < 0) throw new IllegalArgumentException("block coordinates must be non-negative");
    }
}
