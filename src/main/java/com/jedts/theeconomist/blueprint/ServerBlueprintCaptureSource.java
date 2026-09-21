package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Applies server-world loading, region protection, and container checks. */
public final class ServerBlueprintCaptureSource implements BlueprintCaptureSource {
    private final ServerLevel level;
    private final ServerPlayer player;

    public ServerBlueprintCaptureSource(ServerLevel level, ServerPlayer player) {
        this.level = level;
        this.player = player;
    }

    @Override public boolean inBounds(BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos);
    }

    @Override public boolean loaded(BlockPos pos) { return level.isLoaded(pos); }

    @Override public boolean accessible(BlockPos pos) {
        if (!level.mayInteract(player, pos) || !player.mayInteract(level, pos)) return false;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BaseContainerBlockEntity container && !container.canOpen(player)) return false;
        return !(entity instanceof CommandBlockEntity) || player.canUseGameMasterBlocks();
    }

    @Override public BlockState state(BlockPos pos) { return level.getBlockState(pos); }

    @Override public CompoundTag blockEntityData(BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : BlueprintBlockEntitySerialization.save(level.registryAccess(),
                entity::saveWithFullMetadata);
    }
}
