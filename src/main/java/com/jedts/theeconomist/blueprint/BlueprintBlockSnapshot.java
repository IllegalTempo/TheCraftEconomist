package com.jedts.theeconomist.blueprint;

import java.util.Objects;

public record BlueprintBlockSnapshot(String blockId, String stateProperties) {
    public BlueprintBlockSnapshot {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(stateProperties, "stateProperties");
        if (blockId.isBlank()) throw new IllegalArgumentException("block id must not be blank");
    }
}
