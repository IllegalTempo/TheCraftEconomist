package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintBlock;
import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintSessionMode;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        BlueprintSessionModel session = BlueprintSessionModel.selecting();
        session.firstCorner(new BlockPos(3, 70, 4));

        assertTrue(session.cancel());
        assertFalse(session.cancel());
        assertEquals(BlueprintSessionMode.NONE, session.mode());
        assertNull(session.firstCorner());
    }

    @Test
    void invalid_session_context_requests_cleanup() {
        BlueprintSessionModel session = BlueprintSessionModel.selecting();

        assertTrue(session.shouldCancel(false, true, true, false));
        assertTrue(session.shouldCancel(true, false, true, false));
        assertTrue(session.shouldCancel(true, true, false, false));
        assertTrue(session.shouldCancel(true, true, true, true));
    }

    @Test
    void rejected_capture_clears_outline_and_allows_new_first_corner() {
        BlueprintSessionModel session = BlueprintSessionModel.selecting();
        session.firstCorner(new BlockPos(3, 70, 4));
        assertEquals(new BlockPos(3, 70, 4), session.firstCorner());
        session.firstCorner(null);
        assertNull(session.firstCorner());
        assertEquals(BlueprintSessionMode.SELECTING, session.mode());
    }

    @Test
    void placement_request_can_be_settled_without_design_draft() {
        BlueprintDesign design = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));
        BlueprintSessionModel session = BlueprintSessionModel.placing(design);
        assertTrue(session.beginRequest(42));
        assertFalse(session.beginRequest(43));
        assertFalse(session.settleRequest(41));
        assertTrue(session.settleRequest(42));
        assertFalse(session.hasPendingRequest());
    }
}
