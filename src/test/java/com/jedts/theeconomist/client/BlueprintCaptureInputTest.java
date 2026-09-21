package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintState;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintCaptureInputTest {
    @Test
    void only_main_hand_empty_blueprint_consumes_real_block_use() {
        assertTrue(BlueprintCaptureInput.consumeBlockUse(BlueprintState.EMPTY, InteractionHand.MAIN_HAND));
        assertFalse(BlueprintCaptureInput.consumeBlockUse(BlueprintState.EMPTY, InteractionHand.OFF_HAND));
        assertFalse(BlueprintCaptureInput.consumeBlockUse(BlueprintState.DESIGNED, InteractionHand.MAIN_HAND));
        assertFalse(BlueprintCaptureInput.consumeBlockUse(BlueprintState.PLANNED, InteractionHand.MAIN_HAND));
    }
}
