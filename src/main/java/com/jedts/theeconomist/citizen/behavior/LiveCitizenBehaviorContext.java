package com.jedts.theeconomist.citizen.behavior;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record LiveCitizenBehaviorContext(CitizenEntity citizen, ServerLevel level) implements CitizenBehaviorContext {
    public LiveCitizenBehaviorContext {
        Objects.requireNonNull(citizen, "citizen");
        Objects.requireNonNull(level, "level");
        if (citizen.level() != level) throw new IllegalArgumentException("Citizen belongs to a different level");
    }

    @Override public long gameTime() { return level.getGameTime(); }
    @Override public PathNavigation navigation() { return citizen.getNavigation(); }
    @Override public Optional<BlockPos> home() { return citizen.homeSpot(); }
    @Override public Optional<UUID> householdId() { return citizen.householdId(); }
}
