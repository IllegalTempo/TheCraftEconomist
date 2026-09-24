package com.jedts.theeconomist.citizen.farm.claim;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Server-owned farmland cell ownership; plots are connected cells with the same owner. */
public final class PlotClaims {
    public static final int MAX_CLAIM_COMPONENT = 4096;
    private final Map<BlockPos, PlotOwner> owners = new HashMap<>();

    public Optional<PlotOwner> ownerAt(BlockPos pos) {
        return Optional.ofNullable(owners.get(pos));
    }

    public Map<BlockPos, PlotOwner> entries() {
        return Map.copyOf(owners);
    }

    public boolean claimNewFarmland(BlockPos pos, PlotOwner owner) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(owner, "owner");
        return owners.putIfAbsent(pos.immutable(), owner) == null;
    }

    public int claimUnclaimedComponent(BlockPos start, PlotOwner owner, Predicate<BlockPos> farmland) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(farmland, "farmland");
        if (owners.containsKey(start) || !farmland.test(start)) return 0;
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (found.contains(pos) || owners.containsKey(pos) || !farmland.test(pos)) continue;
            found.add(pos);
            if (found.size() > MAX_CLAIM_COMPONENT) return 0;
            neighbors(pos, queue);
        }
        for (BlockPos pos : found) owners.put(pos, owner);
        return found.size();
    }

    public Set<BlockPos> component(BlockPos start) {
        PlotOwner owner = owners.get(start);
        if (owner == null) return Set.of();
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!owner.equals(owners.get(pos)) || !found.add(pos)) continue;
            neighbors(pos, queue);
        }
        return Set.copyOf(found);
    }

    public void remove(BlockPos pos) {
        owners.remove(pos);
    }

    public int release(PlotOwner owner) {
        int before = owners.size();
        owners.values().removeIf(owner::equals);
        return before - owners.size();
    }

    public int reassign(PlotOwner from, PlotOwner to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) return 0;
        int reassigned = 0;
        for (var entry : owners.entrySet()) {
            if (entry.getValue().equals(from)) {
                entry.setValue(to);
                reassigned++;
            }
        }
        return reassigned;
    }

    private static void neighbors(BlockPos pos, ArrayDeque<BlockPos> queue) {
        queue.add(pos.north());
        queue.add(pos.south());
        queue.add(pos.east());
        queue.add(pos.west());
    }
}
