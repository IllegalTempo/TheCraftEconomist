package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlueprintDraft {
    private final Map<BlockPos, BlueprintBlockSnapshot> blocks = new LinkedHashMap<>();

    public BlueprintBlockSnapshot put(BlockPos position, BlueprintBlockSnapshot block) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(block);
        if (!blocks.containsKey(position) && blocks.size() >= BlueprintLimits.MAX_BLOCKS) {
            throw new IllegalArgumentException("blueprint has reached the block limit");
        }
        int minX = position.getX(), maxX = minX;
        int minY = position.getY(), maxY = minY;
        int minZ = position.getZ(), maxZ = minZ;
        for (BlockPos existing : blocks.keySet()) {
            minX = Math.min(minX, existing.getX());
            maxX = Math.max(maxX, existing.getX());
            minY = Math.min(minY, existing.getY());
            maxY = Math.max(maxY, existing.getY());
            minZ = Math.min(minZ, existing.getZ());
            maxZ = Math.max(maxZ, existing.getZ());
        }
        if ((long) maxX - minX >= BlueprintLimits.MAX_DIMENSION
                || (long) maxY - minY >= BlueprintLimits.MAX_DIMENSION
                || (long) maxZ - minZ >= BlueprintLimits.MAX_DIMENSION) {
            throw new IllegalArgumentException("blueprint exceeds the " + BlueprintLimits.MAX_DIMENSION + "-block dimension limit");
        }
        return blocks.put(position, block);
    }

    public BlueprintBlockSnapshot remove(BlockPos position) {
        return blocks.remove(position);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public Map<BlockPos, BlueprintBlockSnapshot> blocks() {
        return Map.copyOf(blocks);
    }

    public void clear() {
        blocks.clear();
    }

    public BlueprintDesign normalize() {
        if (blocks.isEmpty()) throw new IllegalStateException("cannot normalize an empty blueprint draft");
        int minX = blocks.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minY = blocks.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int minZ = blocks.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX = blocks.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int maxY = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
        int maxZ = blocks.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        List<BlueprintBlock> normalized = blocks.entrySet().stream()
                .map(entry -> new BlueprintBlock(entry.getKey().getX() - minX,
                        entry.getKey().getY() - minY,
                        entry.getKey().getZ() - minZ,
                        entry.getValue().blockId(), entry.getValue().stateProperties()))
                .toList();
        return new BlueprintDesign(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, normalized);
    }
}
