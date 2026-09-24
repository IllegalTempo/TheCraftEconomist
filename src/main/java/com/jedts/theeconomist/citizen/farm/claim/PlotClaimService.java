package com.jedts.theeconomist.citizen.farm.claim;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.trade.CoinDenominations;
import com.jedts.theeconomist.citizen.info.CitizenInfoDetails;
import com.jedts.theeconomist.citizen.house.HouseholdSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlotClaimService {
    private final ServerLevel level;
    private final PlotClaimSavedData data;

    private PlotClaimService(ServerLevel level) {
        this.level = level;
        this.data = level.getDataStorage().computeIfAbsent(PlotClaimSavedData.TYPE);
    }

    public static PlotClaimService forLevel(ServerLevel level) {
        return new PlotClaimService(level);
    }

    public Optional<PlotOwner> ownerAt(BlockPos pos) {
        return data.claims().ownerAt(pos);
    }

    public Set<BlockPos> claimedBy(PlotOwner owner) {
        return data.claims().entries().entrySet().stream()
                .filter(entry -> entry.getValue().equals(owner))
                .map(entry -> entry.getKey().immutable())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<BlockPos> component(BlockPos pos) {
        return data.claims().component(pos);
    }

    public CitizenInfoDetails infoDetails(CitizenEntity citizen) {
        PlotOwner owner = ensureCitizenClaimsMigrated(citizen);
        return CitizenInfoDetails.from(citizen.farmInventory(), data.claims(), owner,
                CoinDenominations.registered(), citizen.priceSnapshot(), citizen.farmStatus(),
                citizen.homeSpot().map(pos -> pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                        .orElse("unsettled"));
    }

    public int claimPlayerUse(ServerPlayer player, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) return 0;
        int count = data.claims().claimUnclaimedComponent(pos,
                new PlotOwner(PlotOwner.Kind.PLAYER, player.getUUID()),
                candidate -> level.hasChunkAt(candidate) && level.getBlockState(candidate).is(Blocks.FARMLAND));
        if (count > 0) data.changed();
        return count;
    }

    public boolean claimNewPlayerFarmland(ServerPlayer player, BlockPos pos) {
        return claimNewFarmland(pos, new PlotOwner(PlotOwner.Kind.PLAYER, player.getUUID()));
    }

    public boolean claimCitizen(CitizenEntity citizen, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) return false;
        PlotOwner owner = ensureCitizenClaimsMigrated(citizen);
        int count = data.claims().claimUnclaimedComponent(pos, owner,
                candidate -> level.hasChunkAt(candidate) && level.getBlockState(candidate).is(Blocks.FARMLAND));
        if (count > 0) data.changed();
        return count > 0;
    }

    public boolean claimNewCitizenFarmland(CitizenEntity citizen, BlockPos pos) {
        return claimNewFarmland(pos, ensureCitizenClaimsMigrated(citizen));
    }

    public void remove(BlockPos pos) {
        if (data.claims().ownerAt(pos).isEmpty()) return;
        data.claims().remove(pos);
        data.changed();
    }

    public int releaseCitizen(CitizenEntity citizen) {
        PlotOwner owner = ensureCitizenClaimsMigrated(citizen);
        // Household plots survive an individual member's death and remain usable by relatives.
        if (owner.kind() == PlotOwner.Kind.HOUSEHOLD) return 0;
        int count = data.claims().release(owner);
        if (count > 0) data.changed();
        return count;
    }

    public PlotOwner ownerFor(CitizenEntity citizen) {
        return citizen.householdId().map(id -> new PlotOwner(PlotOwner.Kind.HOUSEHOLD, id))
                .orElseGet(() -> new PlotOwner(PlotOwner.Kind.CITIZEN, citizen.getUUID()));
    }

    /** Migrates saved per-Citizen claims into the household owner the first time a member uses the farm. */
    public PlotOwner ensureCitizenClaimsMigrated(CitizenEntity citizen) {
        PlotOwner owner = ownerFor(citizen);
        if (owner.kind() != PlotOwner.Kind.HOUSEHOLD || !data.markHouseholdMigrated(owner.id())) return owner;
        boolean changed = false;
        for (UUID residentId : HouseholdSavedData.forLevel(level).ledger().residentIds(owner.id()))
            changed |= data.claims().reassign(new PlotOwner(PlotOwner.Kind.CITIZEN, residentId), owner) > 0;
        if (changed) data.changed();
        return owner;
    }

    public void reconcileLoaded(int budget) {
        for (BlockPos pos : data.nextScan(budget)) {
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).is(Blocks.FARMLAND)) remove(pos);
        }
    }

    private boolean claimNewFarmland(BlockPos pos, PlotOwner owner) {
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) return false;
        boolean claimed = data.claims().claimNewFarmland(pos, owner);
        if (claimed) data.changed();
        return claimed;
    }
}
