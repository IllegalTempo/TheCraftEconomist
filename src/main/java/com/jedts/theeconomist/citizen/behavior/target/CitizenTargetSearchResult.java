package com.jedts.theeconomist.citizen.behavior.target;

import net.minecraft.core.BlockPos;

import java.util.Optional;

public record CitizenTargetSearchResult(State state, Optional<BlockPos> location) {
    public enum State { FOUND, WALKING_TO_MEMORY, WALKING_SEARCH_ROUTE, SEARCHING }
    public static CitizenTargetSearchResult of(State state) { return new CitizenTargetSearchResult(state, Optional.empty()); }
    public static CitizenTargetSearchResult found(BlockPos pos) { return new CitizenTargetSearchResult(State.FOUND, Optional.of(pos.immutable())); }
}
