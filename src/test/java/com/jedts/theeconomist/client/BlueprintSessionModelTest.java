package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintBlock;
import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintSessionMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintSessionModelTest {
    @Test
    void rotates_placement_in_quarter_turns() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));
        BlueprintSessionModel session = BlueprintSessionModel.placing(design);

        session.rotate();
        session.rotate();
        session.rotate();
        session.rotate();

        assertEquals(0, session.rotation());
    }

    @Test
    void cleanup_is_idempotent() {
        BlueprintSessionModel session = BlueprintSessionModel.designing();

        assertTrue(session.cancel());
        assertFalse(session.cancel());
        assertEquals(BlueprintSessionMode.NONE, session.mode());
    }

    @Test
    void invalid_session_context_requests_cleanup() {
        BlueprintSessionModel session = BlueprintSessionModel.designing();

        assertTrue(session.shouldCancel(false, true, true, false));
        assertTrue(session.shouldCancel(true, false, true, false));
        assertTrue(session.shouldCancel(true, true, false, false));
        assertTrue(session.shouldCancel(true, true, true, true));
    }

    @Test
    void rejected_save_keeps_the_draft_editable() {
        BlueprintSessionModel session = BlueprintSessionModel.designing();
        session.draft().put(net.minecraft.core.BlockPos.ZERO,
                new com.jedts.theeconomist.blueprint.BlueprintBlockSnapshot("minecraft:stone", ""));

        assertTrue(session.beginRequest(42));
        assertTrue(session.hasPendingRequest());
        assertFalse(session.canEditDraft());
        assertFalse(session.beginRequest(43));
        assertFalse(session.settleRequest(41));
        assertTrue(session.settleRequest(42));
        assertFalse(session.hasPendingRequest());
        assertTrue(session.canEditDraft());
        assertEquals(BlueprintSessionMode.DESIGN, session.mode());
        assertFalse(session.draft().isEmpty());
    }
}
