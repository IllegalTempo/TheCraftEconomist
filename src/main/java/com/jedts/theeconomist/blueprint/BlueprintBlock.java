package com.jedts.theeconomist.blueprint;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

public record BlueprintBlock(int x, int y, int z, String blockId, String stateProperties,
                             CompoundTag blockEntityData) {
    public BlueprintBlock(int x, int y, int z, String blockId) {
        this(x, y, z, blockId, "", null);
    }

    public BlueprintBlock(int x, int y, int z, String blockId, String stateProperties) {
        this(x, y, z, blockId, stateProperties, null);
    }

    public BlueprintBlock {
        Objects.requireNonNull(blockId);
        Objects.requireNonNull(stateProperties);
        if (blockId.isBlank()) throw new IllegalArgumentException("block id must not be blank");
        if (x < 0 || y < 0 || z < 0) throw new IllegalArgumentException("block coordinates must be non-negative");
        blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
    }

    @Override
    public CompoundTag blockEntityData() {
        return blockEntityData == null ? null : blockEntityData.copy();
    }
}
