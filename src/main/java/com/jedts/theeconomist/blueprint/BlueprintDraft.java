package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlueprintDraft {
    private final Map<BlockPos, BlueprintBlockSnapshot> blocks = new LinkedHashMap<>();

    public BlueprintBlockSnapshot put(BlockPos position, BlueprintBlockSnapshot block) {
        return blocks.put(Objects.requireNonNull(position), Objects.requireNonNull(block));
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
