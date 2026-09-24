package com.jedts.theeconomist.citizen.house;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

/** Codec-backed, dimension-scoped persistence for generated house households. */
public final class HouseholdSavedData extends SavedData {
    private record TargetEntry(String id, int x, int y, int z) { }
    private record StorageEntry(int x, int y, int z) { }
    private static final Codec<TargetEntry> TARGET_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TargetEntry::id), Codec.INT.fieldOf("x").forGetter(TargetEntry::x),
            Codec.INT.fieldOf("y").forGetter(TargetEntry::y), Codec.INT.fieldOf("z").forGetter(TargetEntry::z)
    ).apply(instance, TargetEntry::new));
    private static final Codec<StorageEntry> STORAGE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(StorageEntry::x), Codec.INT.fieldOf("y").forGetter(StorageEntry::y),
            Codec.INT.fieldOf("z").forGetter(StorageEntry::z)
    ).apply(instance, StorageEntry::new));
    private static final Codec<HouseholdLedger.Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(entry -> entry.anchor().getX()),
            Codec.INT.fieldOf("y").forGetter(entry -> entry.anchor().getY()),
            Codec.INT.fieldOf("z").forGetter(entry -> entry.anchor().getZ()),
            Codec.STRING.fieldOf("surname").forGetter(HouseholdLedger.Entry::surname),
            Codec.INT.fieldOf("residentCount").forGetter(HouseholdLedger.Entry::residentCount),
            Codec.INT.fieldOf("issuedSlots").forGetter(HouseholdLedger.Entry::issuedSlots),
            Codec.STRING.listOf().optionalFieldOf("givenNames", List.of()).forGetter(HouseholdLedger.Entry::givenNames),
            TARGET_CODEC.listOf().optionalFieldOf("targetLocations", List.of()).forGetter(entry -> entry.targetLocations().entrySet().stream().map(e -> new TargetEntry(e.getKey(), e.getValue().getX(), e.getValue().getY(), e.getValue().getZ())).toList()),
            STORAGE_CODEC.optionalFieldOf("storagePosition").forGetter(entry -> java.util.Optional.ofNullable(entry.storagePosition())
                    .map(pos -> new StorageEntry(pos.getX(), pos.getY(), pos.getZ())))
    ).apply(instance, (x, y, z, surname, residentCount, issuedSlots, givenNames, targets, storage) ->
            new HouseholdLedger.Entry(new BlockPos(x, y, z), surname, residentCount, issuedSlots, givenNames,
                    targets.stream().filter(target -> !target.id().isBlank()).collect(java.util.stream.Collectors.toMap(TargetEntry::id, target -> new BlockPos(target.x(), target.y(), target.z()), (a, b) -> b)),
                    storage.map(value -> new BlockPos(value.x(), value.y(), value.z())).orElse(null))));
    static final Codec<HouseholdSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(HouseholdSavedData::dimensionId),
            ENTRY_CODEC.listOf().fieldOf("households").forGetter(data -> data.ledger.entries())
    ).apply(instance, HouseholdSavedData::new));
    public static final SavedDataType<HouseholdSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("theeconomist", "households"), HouseholdSavedData::new,
            CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final HouseholdLedger ledger;

    HouseholdSavedData() {
        this("minecraft:overworld");
    }

    HouseholdSavedData(String dimensionId) {
        this(dimensionId, List.of());
    }

    private HouseholdSavedData(String dimensionId, List<HouseholdLedger.Entry> entries) {
        ledger = new HouseholdLedger(dimensionId, entries, this::setDirty);
    }

    public static HouseholdSavedData forLevel(ServerLevel level) {
        HouseholdSavedData data = level.getDataStorage().computeIfAbsent(TYPE);
        String dimensionId = level.dimension().identifier().toString();
        if (!data.dimensionId().equals(dimensionId))
            throw new IllegalStateException("household data belongs to " + data.dimensionId() + ", not " + dimensionId);
        return data;
    }

    public HouseholdLedger ledger() {
        return ledger;
    }

    String dimensionId() {
        return ledger.dimensionId();
    }
}

