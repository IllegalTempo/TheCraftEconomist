package com.jedts.theeconomist.citizen.action.resource;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetFinder;
import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Finds and harvests log blocks, carrying their real block drops in the Citizen inventory. */
public final class LogResourceProvider implements CitizenResourceProvider {
    @Override public String id() { return "logs"; }

    @Override public boolean canProvide(Item item) {
        return item != null && Block.byItem(item).defaultBlockState().is(BlockTags.LOGS);
    }

    @Override public CitizenTargetFinder targetFinder(Item item) {
        return new LogFinder(item);
    }

    @Override public Optional<BlockPos> interactionPosition(CitizenBehaviorContext context, BlockPos target) {
        var level = context.level();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos feet = target.relative(direction);
            if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.above()) || !level.hasChunkAt(feet.below())) continue;
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP))
                return Optional.of(feet);
        }
        return Optional.empty();
    }

    @Override public GatherResult gather(CitizenBehaviorContext context, BlockPos target) {
        if (!(context.level() instanceof ServerLevel level) || !level.hasChunkAt(target)) return GatherResult.TARGET_INVALID;
        var state = level.getBlockState(target);
        if (!state.is(BlockTags.LOGS)) return GatherResult.TARGET_INVALID;
        BlockEntity blockEntity = level.getBlockEntity(target);
        List<ItemStack> drops = Block.getDrops(state, level, target, blockEntity);
        CitizenFarmInventory inventory = context.citizen().farmInventory();
        CitizenFarmInventory trial = inventory.copy();
        for (ItemStack stack : drops) if (!trial.insert(stack).isEmpty()) return GatherResult.INVENTORY_FULL;
        if (!level.destroyBlock(target, false, context.citizen(), 512)) return GatherResult.TARGET_INVALID;
        for (ItemStack stack : drops) inventory.insert(stack);
        context.citizen().swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        level.playSound(null, target, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
        return GatherResult.GATHERED;
    }

    private final class LogFinder implements CitizenTargetFinder {
        private final Item item;

        private LogFinder(Item item) { this.item = item; }

        @Override public Optional<BlockPos> findAt(CitizenBehaviorContext context, BlockPos location) {
            if (!context.level().hasChunkAt(location)) return Optional.empty();
            return eligible(context, location) ? Optional.of(location.immutable()) : Optional.empty();
        }

        @Override public Optional<BlockPos> findNear(CitizenBehaviorContext context, BlockPos waypoint, int radius) {
            var level = context.level();
            List<BlockPos> candidates = new ArrayList<>();
            for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                int x = waypoint.getX() + dx;
                int z = waypoint.getZ() + dz;
                if (!level.hasChunkAt(new BlockPos(x, waypoint.getY(), z))) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int bottom = Math.max(level.getMinY() + 1, top - 48);
                for (int y = top; y >= bottom; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (eligible(context, pos) && interactionPosition(context, pos).isPresent()) {
                        candidates.add(pos);
                        break;
                    }
                }
            }
            return candidates.stream().min(Comparator.comparingDouble(context.citizen().blockPosition()::distSqr));
        }

        private boolean eligible(CitizenBehaviorContext context, BlockPos pos) {
            var level = context.level();
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(BlockTags.LOGS)
                    || level.getBlockState(pos).getBlock().asItem() != item) return false;
            var below = level.getBlockState(pos.below());
            return !below.is(BlockTags.LOGS) && below.isFaceSturdy(level, pos.below(), Direction.UP);
        }
    }
}
