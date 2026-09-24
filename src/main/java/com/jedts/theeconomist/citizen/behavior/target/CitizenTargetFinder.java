package com.jedts.theeconomist.citizen.behavior.target;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/** Action-specific validation and bounded local discovery. */
public interface CitizenTargetFinder {
    Optional<BlockPos> findAt(CitizenBehaviorContext context, BlockPos rememberedLocation);
    Optional<BlockPos> findNear(CitizenBehaviorContext context, BlockPos waypoint, int localRadius);
}
