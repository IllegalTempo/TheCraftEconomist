package com.jedts.theeconomist.citizen.farm.claim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.UUID;

/** Dimension-local persistent farmland ownership. */
final class PlotClaimSavedData extends SavedData {
    private static final Codec<PlotOwner.Kind> KIND_CODEC = Codec.STRING.xmap(PlotOwner.Kind::valueOf, Enum::name);
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(entry -> entry.pos().getX()),
            Codec.INT.fieldOf("y").forGetter(entry -> entry.pos().getY()),
            Codec.INT.fieldOf("z").forGetter(entry -> entry.pos().getZ()),
            KIND_CODEC.fieldOf("kind").forGetter(entry -> entry.owner().kind()),
            UUID_CODEC.fieldOf("owner").forGetter(entry -> entry.owner().id())
    ).apply(instance, (x, y, z, kind, id) -> new Entry(new BlockPos(x, y, z), new PlotOwner(kind, id))));
    static final Codec<PlotClaimSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRY_CODEC.listOf().fieldOf("claims").forGetter(PlotClaimSavedData::entries)
    ).apply(instance, PlotClaimSavedData::new));
    static final SavedDataType<PlotClaimSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("theeconomist", "farm_claims"), PlotClaimSavedData::new,
            CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final PlotClaims claims = new PlotClaims();
    private List<BlockPos> scanPositions = List.of();
    private int scanCursor;
    private final Set<UUID> migratedHouseholds = new HashSet<>();

    PlotClaimSavedData() {
        this(List.of());
    }

    PlotClaimSavedData(List<Entry> entries) {
        for (Entry entry : entries) claims.claimNewFarmland(entry.pos(), entry.owner());
        refreshScan();
    }

    PlotClaims claims() { return claims; }

    boolean markHouseholdMigrated(UUID householdId) { return migratedHouseholds.add(householdId); }

    List<Entry> entries() {
        return claims.entries().entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
    }

    void changed() {
        refreshScan();
        setDirty();
    }

    List<BlockPos> nextScan(int budget) {
        if (scanPositions.isEmpty() || budget <= 0) return List.of();
        List<BlockPos> result = new ArrayList<>(Math.min(budget, scanPositions.size()));
        for (int i = 0; i < budget && i < scanPositions.size(); i++) {
            result.add(scanPositions.get(scanCursor));
            scanCursor = (scanCursor + 1) % scanPositions.size();
        }
        return result;
    }

    private void refreshScan() {
        scanPositions = List.copyOf(claims.entries().keySet());
        scanCursor = 0;
    }

    record Entry(BlockPos pos, PlotOwner owner) { }
}
