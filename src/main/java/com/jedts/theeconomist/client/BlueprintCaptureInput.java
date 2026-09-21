package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintState;
import net.minecraft.world.InteractionHand;

/** Routing decision shared by the block-use hook and its regression test. */
public final class BlueprintCaptureInput {
    private BlueprintCaptureInput() { }

    public static boolean consumeBlockUse(BlueprintState state, InteractionHand hand) {
        return state == BlueprintState.EMPTY && hand == InteractionHand.MAIN_HAND;
    }
}
