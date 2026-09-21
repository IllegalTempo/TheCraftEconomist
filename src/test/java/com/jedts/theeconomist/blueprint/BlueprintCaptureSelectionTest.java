package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintCaptureSelectionTest {
    @Test
    void second_click_completes_same_players_selection() {
        BlueprintCaptureSelection selection = new BlueprintCaptureSelection();
        BlueprintCaptureSelection.Context context = context(UUID.randomUUID(), new Object(), 0, 100);
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(3, 66, 4);
        assertEquals(CaptureSelectionState.FIRST_CORNER, selection.click(context, first).state());
        BlueprintCaptureSelection.Result result = selection.click(context, second);
        assertEquals(CaptureSelectionState.COMPLETE, result.state());
        assertEquals(first, result.firstCorner());
        assertEquals(second, result.secondCorner());
        assertNull(selection.firstCorner(context.playerId()));
    }

    @Test
    void same_block_twice_is_a_one_block_selection() {
        BlueprintCaptureSelection selection = new BlueprintCaptureSelection();
        BlueprintCaptureSelection.Context context = context(UUID.randomUUID(), new Object(), 0, 100);
        BlockPos corner = new BlockPos(5, 70, 5);
        selection.click(context, corner);
        assertEquals(corner, selection.click(context, corner).secondCorner());
    }

    @Test
    void replacing_item_in_same_slot_starts_fresh_selection() {
        BlueprintCaptureSelection selection = new BlueprintCaptureSelection();
        UUID player = UUID.randomUUID();
        BlueprintCaptureSelection.Context firstItem = context(player, new Object(), 0, 100);
        selection.click(firstItem, new BlockPos(1, 64, 1));
        BlueprintCaptureSelection.Context replacement = context(player, new Object(), 0, 101);
        BlueprintCaptureSelection.Result result = selection.click(replacement, new BlockPos(4, 64, 4));
        assertEquals(CaptureSelectionState.FIRST_CORNER, result.state());
        assertEquals(new BlockPos(4, 64, 4), result.firstCorner());
    }

    @Test
    void expiry_dimension_change_or_slot_change_starts_fresh_selection() {
        BlueprintCaptureSelection selection = new BlueprintCaptureSelection();
        UUID player = UUID.randomUUID();
        Object stack = new Object();
        selection.click(context(player, stack, 0, 100), BlockPos.ZERO);
        assertEquals(CaptureSelectionState.FIRST_CORNER,
                selection.click(context(player, stack, 0, 1_500), new BlockPos(1, 0, 0)).state());
        assertEquals(CaptureSelectionState.FIRST_CORNER,
                selection.click(new BlueprintCaptureSelection.Context(player, "minecraft:the_nether", 0, stack, 1_501),
                        new BlockPos(2, 0, 0)).state());
        assertEquals(CaptureSelectionState.FIRST_CORNER,
                selection.click(context(player, stack, 1, 1_502), new BlockPos(3, 0, 0)).state());
    }

    @Test
    void cancel_clears_selection_on_death_or_disconnect() {
        BlueprintCaptureSelection selection = new BlueprintCaptureSelection();
        UUID player = UUID.randomUUID();
        selection.click(context(player, new Object(), 0, 100), BlockPos.ZERO);
        selection.cancel(player);
        assertNull(selection.firstCorner(player));
    }

    @Test
    void corner_request_must_match_servers_targeted_block() {
        BlockPos visible = new BlockPos(2, 70, 2);
        BlockHitResult serverHit = new BlockHitResult(new Vec3(2.5, 70.5, 2.5),
                Direction.UP, visible, false);
        assertTrue(BlueprintCaptureTargeting.matches(serverHit, visible));
        assertFalse(BlueprintCaptureTargeting.matches(serverHit, new BlockPos(3, 70, 2)));
    }

    private static BlueprintCaptureSelection.Context context(UUID player, Object stack, int slot, long tick) {
        return new BlueprintCaptureSelection.Context(player, "minecraft:overworld", slot, stack, tick);
    }
}
