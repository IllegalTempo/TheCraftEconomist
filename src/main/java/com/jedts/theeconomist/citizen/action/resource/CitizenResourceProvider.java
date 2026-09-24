package com.jedts.theeconomist.citizen.action.resource;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Optional;

/** A world source for a family of requested items. */
public interface CitizenResourceProvider {
    enum GatherResult { GATHERED, TARGET_INVALID, INVENTORY_FULL }

    String id();
    boolean canProvide(Item item);
    CitizenTargetFinder targetFinder(Item item);
    Optional<BlockPos> interactionPosition(CitizenBehaviorContext context, BlockPos target);
    GatherResult gather(CitizenBehaviorContext context, BlockPos target);
}
