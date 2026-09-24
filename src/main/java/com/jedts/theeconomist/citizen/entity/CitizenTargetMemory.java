package com.jedts.theeconomist.citizen.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Per-citizen remembered world targets and incremental search cursors. */
final class CitizenTargetMemory {
    private record TargetEntry(String id, int x, int y, int z, String dimension) { }
    private record CursorEntry(String id, int cursor) { }
    private record DimensionEntry(String id, String dimension) { }

    private static final Codec<TargetEntry> TARGET_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TargetEntry::id),
            Codec.INT.fieldOf("x").forGetter(TargetEntry::x),
            Codec.INT.fieldOf("y").forGetter(TargetEntry::y),
            Codec.INT.fieldOf("z").forGetter(TargetEntry::z),
            Codec.STRING.fieldOf("dimension").forGetter(TargetEntry::dimension)
    ).apply(instance, TargetEntry::new));
    private static final Codec<CursorEntry> CURSOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(CursorEntry::id),
            Codec.INT.fieldOf("cursor").forGetter(CursorEntry::cursor)
    ).apply(instance, CursorEntry::new));
    private static final Codec<DimensionEntry> DIMENSION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(DimensionEntry::id),
            Codec.STRING.fieldOf("dimension").forGetter(DimensionEntry::dimension)
    ).apply(instance, DimensionEntry::new));

    private final Map<String, BlockPos> targetLocations = new HashMap<>();
    private final Map<String, String> targetDimensions = new HashMap<>();
    private final Map<String, Integer> searchCursors = new HashMap<>();
    private final Map<String, String> searchDimensions = new HashMap<>();

    Optional<BlockPos> localTargetLocation(String id, String dimension) {
        String storedDimension = targetDimensions.get(id);
        if (storedDimension != null && !storedDimension.equals(dimension)) return Optional.empty();
        return Optional.ofNullable(targetLocations.get(id));
    }

    void rememberLocalTarget(String id, BlockPos position, String dimension) {
        validateId(id);
        targetLocations.put(id, position.immutable());
        targetDimensions.put(id, Objects.requireNonNull(dimension, "dimension"));
    }

    void forgetLocalTarget(String id) {
        targetLocations.remove(id);
        targetDimensions.remove(id);
    }

    int targetSearchCursor(String id) {
        return Math.max(0, searchCursors.getOrDefault(id, 0));
    }

    void targetSearchCursor(String id, int value) {
        validateId(id);
        searchCursors.put(id, Math.max(0, value));
    }

    Optional<String> targetSearchDimension(String id) {
        return Optional.ofNullable(searchDimensions.get(id));
    }

    void targetSearchDimension(String id, String dimension) {
        validateId(id);
        searchDimensions.put(id, Objects.requireNonNull(dimension, "dimension"));
    }

    void write(ValueOutput root) {
        var targetList = root.list("TargetLocations", TARGET_CODEC);
        targetLocations.forEach((id, pos) -> targetList.add(new TargetEntry(id, pos.getX(), pos.getY(), pos.getZ(),
                targetDimensions.getOrDefault(id, ""))));
        var cursorList = root.list("TargetSearchCursors", CURSOR_CODEC);
        searchCursors.forEach((id, cursor) -> cursorList.add(new CursorEntry(id, Math.max(0, cursor))));
        var dimensionList = root.list("TargetSearchDimensions", DIMENSION_CODEC);
        searchDimensions.forEach((id, dimension) -> dimensionList.add(new DimensionEntry(id, dimension)));
    }

    void read(ValueInput root) {
        targetLocations.clear();
        targetDimensions.clear();
        for (TargetEntry entry : root.listOrEmpty("TargetLocations", TARGET_CODEC)) {
            if (!entry.id().isBlank() && !entry.dimension().isBlank()) {
                targetLocations.put(entry.id(), new BlockPos(entry.x(), entry.y(), entry.z()));
                targetDimensions.put(entry.id(), entry.dimension());
            }
        }

        searchCursors.clear();
        for (CursorEntry entry : root.listOrEmpty("TargetSearchCursors", CURSOR_CODEC)) {
            if (!entry.id().isBlank()) searchCursors.put(entry.id(), Math.max(0, entry.cursor()));
        }

        searchDimensions.clear();
        for (DimensionEntry entry : root.listOrEmpty("TargetSearchDimensions", DIMENSION_CODEC)) {
            if (!entry.id().isBlank() && !entry.dimension().isBlank()) {
                searchDimensions.put(entry.id(), entry.dimension());
            }
        }
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    }
}
