package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only view used by the bounded server capture operation. */
public interface BlueprintCaptureSource {
    boolean inBounds(BlockPos pos);
    boolean loaded(BlockPos pos);
    boolean accessible(BlockPos pos);
    BlockState state(BlockPos pos);
    CompoundTag blockEntityData(BlockPos pos);
}
