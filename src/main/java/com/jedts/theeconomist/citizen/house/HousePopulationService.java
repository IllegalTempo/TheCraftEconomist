package com.jedts.theeconomist.citizen.house;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.entity.CitizenEntities;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.identity.CitizenFamilyRole;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Issues saved resident slots only for loaded, registered structure starts. */
public final class HousePopulationService {
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, BlockPos>> PENDING = new WeakHashMap<>();
    private HousePopulationService() { }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, isNew) -> queueHouseStarts(level, chunk));
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            var pending = PENDING.get(level);
            if (pending == null) return;
            int workLimit = Math.min(4, pending.size());
            for (int work = 0; work < workLimit && !pending.isEmpty(); work++) {
                var next = pending.entrySet().iterator().next();
                BlockPos anchor = next.getKey();
                BlockPos storage = next.getValue();
                pending.remove(anchor);
                if (!populateLoadedHouse(level, anchor, storage)) pending.put(anchor, storage);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    /** Examines structure metadata only; ordinary player blocks never create a household. */
    public static void queueHouseStarts(ServerLevel level, LevelChunk chunk) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        chunk.getAllStarts().forEach((structure, start) -> {
            if (!registry.getResourceKey(structure).filter(CitizenHouseStructures.HOUSE_KEY::equals).isPresent()
                    || !start.isValid()) return;
            for (var piece : start.getPieces()) {
                if (piece instanceof CitizenHousePiece house && chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(house.entrance().getX() >> 4, house.entrance().getZ() >> 4)))
                    PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>())
                            .put(house.entrance().immutable(), house.storage().immutable());
            }
        });
    }

    public static void populateLoadedHouse(ServerLevel level, BlockPos anchor) {
        BlockPos storage = findHouseStorage(level, anchor).orElse(null);
        populateLoadedHouse(level, anchor, storage);
    }

    private static boolean populateLoadedHouse(ServerLevel level, BlockPos anchor, BlockPos storagePosition) {
        if (!level.dimension().equals(Level.OVERWORLD) || !level.hasChunkAt(anchor)) return false;
        var data = HouseholdSavedData.forLevel(level);
        var household = data.ledger().getOrCreate(anchor, CitizenRuntime.config().current().familyNames(),
                new java.util.Random(level.getRandom().nextLong()));
        if (household.storagePosition().isEmpty()) {
            if (storagePosition == null || !ensureChest(level, storagePosition)) return false;
            household.assignStoragePosition(storagePosition);
        } else {
            BlockPos savedStorage = household.storagePosition().orElseThrow();
            if (!level.hasChunkAt(savedStorage) || !ensureChest(level, savedStorage)) return false;
        }
        UUID houseId = household.householdId();
        var givenNames = new HashSet<>(household.usedGivenNames());
        for (int slot = 0; slot < household.residentCount(); slot++) {
            if (level.getEntity(household.slotId(slot)) instanceof CitizenEntity resident) {
                household.rememberGivenName(slot, resident.identity().givenName());
                givenNames.add(resident.identity().givenName());
            }
        }
        for (int slot : household.unissuedSlots()) {
            // A loaded entity can precede its ledger write after a interrupted save.
            if (level.getEntity(household.slotId(slot)) != null) {
                household.issue(slot);
                continue;
            }
            var citizen = new CitizenEntity(CitizenEntities.CITIZEN, level);
            citizen.setUUID(household.slotId(slot));
            citizen.assignHousehold(houseId, household.surname(), givenNames, anchor,
                    CitizenFamilyRole.forHouseholdSlot(slot));
            if (!placeSafely(level, citizen, anchor)) break;
            if (level.addFreshEntity(citizen)) {
                household.rememberGivenName(slot, citizen.identity().givenName());
                household.issue(slot);
                data.setDirty();
                givenNames.add(citizen.identity().givenName());
            }
        }
        return true;
    }

    private static java.util.Optional<BlockPos> findHouseStorage(ServerLevel level, BlockPos anchor) {
        if (!level.hasChunkAt(anchor)) return java.util.Optional.empty();
        LevelChunk chunk = level.getChunkSource().getChunkNow(anchor.getX() >> 4, anchor.getZ() >> 4);
        if (chunk == null) return java.util.Optional.empty();
        return chunk.getAllStarts().values().stream().filter(start -> start.isValid())
                .flatMap(start -> start.getPieces().stream())
                .filter(CitizenHousePiece.class::isInstance).map(CitizenHousePiece.class::cast)
                .filter(house -> house.entrance().equals(anchor))
                .map(CitizenHousePiece::storage).findFirst();
    }

    private static boolean ensureChest(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) return false;
        var state = level.getBlockState(pos);
        if (state.is(Blocks.CHEST)) return true;
        if (!state.isAir() || !level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        return level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3)
                && level.getBlockState(pos).is(Blocks.CHEST);
    }

    private static boolean placeSafely(ServerLevel level, CitizenEntity citizen, BlockPos anchor) {
        for (int radius = 0; radius <= 7; radius++) {
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                for (int y = 0; y >= -1; y--) {
                    if (x * x + y * y + z * z > 64) continue;
                    BlockPos feet = anchor.offset(x, y, z);
                    if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.above()) || !level.hasChunkAt(feet.below())) continue;
                    if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()
                            || !level.getFluidState(feet.below()).isEmpty()
                            || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) continue;
                    citizen.setPos(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
                    if (level.noCollision(citizen, citizen.getBoundingBox())
                            && level.getEntities(citizen, citizen.getBoundingBox()).isEmpty()) return true;
                }
            }
        }
        return false;
    }
}



