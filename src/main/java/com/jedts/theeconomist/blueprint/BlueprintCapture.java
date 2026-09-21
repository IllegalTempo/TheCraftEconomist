package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.client.BlueprintBlockStateCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.state.BlockState;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Converts an inclusive server-world cuboid into an immutable, size-bounded design. */
public final class BlueprintCapture {
    private BlueprintCapture() { }

    public record Result(boolean accepted, BlueprintDesign design, String reason) {
        static Result reject(String reason) { return new Result(false, null, reason); }
        static Result accept(BlueprintDesign design) { return new Result(true, design, ""); }
    }

    public static Result capture(BlockPos first, BlockPos second, BlueprintCaptureSource source) {
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int width = Math.abs(first.getX() - second.getX()) + 1;
        int height = Math.abs(first.getY() - second.getY()) + 1;
        int depth = Math.abs(first.getZ() - second.getZ()) + 1;
        if (width > BlueprintLimits.MAX_DIMENSION || height > BlueprintLimits.MAX_DIMENSION
                || depth > BlueprintLimits.MAX_DIMENSION
                || (long) width * height * depth > BlueprintLimits.MAX_CAPTURE_VOLUME) {
            return Result.reject("selection exceeds blueprint capture limits");
        }
        List<BlueprintBlock> blocks = new ArrayList<>();
        int approximateBytes = 256;
        try {
            for (int y = minY; y < minY + height; y++) {
                for (int z = minZ; z < minZ + depth; z++) {
                    for (int x = minX; x < minX + width; x++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!source.inBounds(pos)) return Result.reject("selection is outside world bounds");
                        if (!source.loaded(pos)) return Result.reject("selection includes unloaded chunks");
                        if (!source.accessible(pos)) return Result.reject("selection includes inaccessible blocks");
                        BlockState state = source.state(pos);
                        if (state.isAir()) continue;
                        BlueprintBlockSnapshot snapshot = BlueprintBlockStateCodec.encode(state);
                        CompoundTag entityData = source.blockEntityData(pos);
                        int entityBytes = 0;
                        if (entityData != null) {
                            entityBytes = encodedBytes(entityData, BlueprintLimits.MAX_BLOCK_ENTITY_BYTES);
                            if (entityBytes > BlueprintLimits.MAX_BLOCK_ENTITY_BYTES) {
                                return Result.reject("block entity data exceeds the size limit");
                            }
                            entityData = entityData.copy();
                            entityData.remove("x");
                            entityData.remove("y");
                            entityData.remove("z");
                        }
                        BlueprintBlock block = new BlueprintBlock(x - minX, y - minY, z - minZ,
                                snapshot.blockId(), snapshot.stateProperties(), entityData);
                        blocks.add(block);
                        if (blocks.size() > BlueprintLimits.MAX_BLOCKS) return Result.reject("too many blocks");
                        approximateBytes += 48 + block.blockId().length() * 3 + block.stateProperties().length() * 3
                                + entityBytes;
                        if (approximateBytes > BlueprintLimits.MAX_DESIGN_BYTES) {
                            return Result.reject("blueprint data exceeds the size limit");
                        }
                    }
                }
            }
            if (blocks.isEmpty()) return Result.reject("selection contains no blocks");
            BlueprintDesign design = new BlueprintDesign(width, height, depth, blocks);
            BlueprintValidationResult validation = BlueprintValidator.validateDesign(design);
            if (!validation.valid()) return Result.reject(validation.reason());
            if (encodedBytes(BlueprintStackData.writeTag(
                    new BlueprintStackData(BlueprintState.DESIGNED, design, null)),
                    BlueprintLimits.MAX_DESIGN_BYTES)
                    > BlueprintLimits.MAX_DESIGN_BYTES) {
                return Result.reject("blueprint data exceeds the size limit");
            }
            return Result.accept(design);
        } catch (IOException | RuntimeException exception) {
            return Result.reject("could not capture region: " + exception.getClass().getSimpleName());
        }
    }

    private static int encodedBytes(CompoundTag tag, int limit) throws IOException {
        CountingOutputStream counter = new CountingOutputStream(limit);
        try {
            NbtIo.write(tag, new DataOutputStream(counter));
            return counter.bytes;
        } catch (LimitExceededException exception) {
            return limit + 1;
        }
    }

    private static final class CountingOutputStream extends OutputStream {
        private final int limit;
        private int bytes;

        private CountingOutputStream(int limit) { this.limit = limit; }

        @Override public void write(int value) throws IOException { count(1); }

        @Override public void write(byte[] data, int offset, int length) throws IOException { count(length); }

        private void count(int amount) throws IOException {
            if (amount > limit - bytes) throw new LimitExceededException();
            bytes += amount;
        }
    }

    private static final class LimitExceededException extends IOException { }
}
