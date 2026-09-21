package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.client.BlueprintBlockStateCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
                        if (entityData != null && encodedBytes(entityData) > BlueprintLimits.MAX_BLOCK_ENTITY_BYTES) {
                            return Result.reject("block entity data exceeds the size limit");
                        }
                        BlueprintBlock block = new BlueprintBlock(x - minX, y - minY, z - minZ,
                                snapshot.blockId(), snapshot.stateProperties(), entityData);
                        blocks.add(block);
                        if (blocks.size() > BlueprintLimits.MAX_BLOCKS) return Result.reject("too many blocks");
                        approximateBytes += 48 + block.blockId().length() * 3 + block.stateProperties().length() * 3
                                + (entityData == null ? 0 : encodedBytes(entityData));
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
                    new BlueprintStackData(BlueprintState.DESIGNED, design, null)))
                    > BlueprintLimits.MAX_DESIGN_BYTES) {
                return Result.reject("blueprint data exceeds the size limit");
            }
            return Result.accept(design);
        } catch (IOException | RuntimeException exception) {
            return Result.reject("could not capture region: " + exception.getClass().getSimpleName());
        }
    }

    private static int encodedBytes(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        return bytes.size();
    }
}
