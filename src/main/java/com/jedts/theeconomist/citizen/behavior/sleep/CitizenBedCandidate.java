package com.jedts.theeconomist.citizen.behavior.sleep;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record CitizenBedCandidate(BlockPos position, boolean occupied, boolean reachable) {
    public CitizenBedCandidate {
        position = Objects.requireNonNull(position, "position").immutable();
    }
}
