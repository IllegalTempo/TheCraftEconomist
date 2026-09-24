package com.jedts.theeconomist.citizen.behavior.sleep;

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CitizenBedSelector {
    private CitizenBedSelector() { }

    public static Optional<BlockPos> select(BlockPos home, List<CitizenBedCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> !candidate.occupied() && candidate.reachable())
                .map(CitizenBedCandidate::position)
                .sorted(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(home))
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ))
                .findFirst();
    }
}
