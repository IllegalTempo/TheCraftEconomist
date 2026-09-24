package com.jedts.theeconomist.citizen.farm;

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Chooses one available action without touching the world. */
public final class FarmerWorkPlanner {
    public enum Action { HARVEST, PLANT, GATHER_SEEDS, HOE }

    public record WorkTarget(BlockPos pos, Action action, boolean loaded, boolean reachable) { }

    public Optional<WorkTarget> choose(List<WorkTarget> candidates) {
        return candidates.stream().filter(WorkTarget::loaded).filter(WorkTarget::reachable)
                .min(Comparator.comparingInt(target -> priority(target.action())));
    }

    public Optional<WorkTarget> choose(List<WorkTarget> candidates, BlockPos current,
                                       Predicate<BlockPos> ownedFarmland) {
        return choose(candidates, current, ownedFarmland, ignored -> false);
    }

    public Optional<WorkTarget> choose(List<WorkTarget> candidates, BlockPos current,
                                       Predicate<BlockPos> ownedFarmland,
                                       Predicate<BlockPos> hydratedFarmland) {
        return choose(candidates, current, current, List.of(), ownedFarmland, hydratedFarmland);
    }

    public Optional<WorkTarget> choose(List<WorkTarget> candidates, BlockPos current, BlockPos familyHome,
                                       List<BlockPos> familyFarmland, Predicate<BlockPos> ownedFarmland,
                                       Predicate<BlockPos> hydratedFarmland) {
        return candidates.stream().filter(WorkTarget::loaded).filter(WorkTarget::reachable)
                .min(Comparator.comparingInt((WorkTarget target) -> priority(target.action()))
                        .thenComparingInt(target -> target.action() == Action.HOE
                                && hydratedFarmland.test(target.pos()) ? 0 : 1)
                        .thenComparingInt(target -> target.action() == Action.HOE
                                && (familyFarmland.isEmpty()
                                ? adjacentToOwnedFarmland(target.pos(), ownedFarmland)
                                : adjacentToFamilyFarmland(target.pos(), familyFarmland)) ? 0 : 1)
                        .thenComparingLong(target -> target.action() == Action.HOE
                                ? familyFarmland.isEmpty() ? horizontalDistanceSquared(target.pos(), familyHome)
                                : nearestFamilyFarmlandDistanceSquared(target.pos(), familyFarmland) : 0L)
                        .thenComparingDouble(target -> target.pos().distSqr(current)));
    }

    private boolean adjacentToFamilyFarmland(BlockPos pos, List<BlockPos> familyFarmland) {
        return familyFarmland.stream().anyMatch(plot -> Math.abs(plot.getX() - pos.getX()) + Math.abs(plot.getZ() - pos.getZ()) == 1
                && plot.getY() == pos.getY());
    }

    private long nearestFamilyFarmlandDistanceSquared(BlockPos pos, List<BlockPos> familyFarmland) {
        return familyFarmland.stream().mapToLong(plot -> horizontalDistanceSquared(pos, plot)).min().orElse(Long.MAX_VALUE);
    }

    private long horizontalDistanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX(), dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private boolean adjacentToOwnedFarmland(BlockPos pos, Predicate<BlockPos> ownedFarmland) {
        return ownedFarmland.test(pos.north()) || ownedFarmland.test(pos.south())
                || ownedFarmland.test(pos.east()) || ownedFarmland.test(pos.west());
    }

    private static int priority(Action action) {
        return switch (action) {
            case HARVEST -> 0;
            case PLANT -> 1;
            case GATHER_SEEDS -> 2;
            case HOE -> 3;
        };
    }
}
