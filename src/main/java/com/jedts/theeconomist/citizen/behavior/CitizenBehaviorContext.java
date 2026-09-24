package com.jedts.theeconomist.citizen.behavior;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

import java.util.Optional;
import java.util.UUID;

public interface CitizenBehaviorContext {
    CitizenEntity citizen();
    ServerLevel level();
    long gameTime();
    PathNavigation navigation();
    Optional<BlockPos> home();
    Optional<UUID> householdId();
}
