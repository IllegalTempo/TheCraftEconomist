package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned first corners; the same exact item must be held at the second click. */
public final class BlueprintCaptureSelection {
    public static final long TIMEOUT_TICKS = 20 * 60;

    public record Context(UUID playerId, String dimension, int selectedSlot, Object stack, long tick) { }
    public record Result(CaptureSelectionState state, BlockPos firstCorner, BlockPos secondCorner) { }

    private record Pending(Context context, BlockPos firstCorner) { }

    private final Map<UUID, Pending> pending = new HashMap<>();

    public Result click(Context context, BlockPos clicked) {
        Pending previous = pending.remove(context.playerId());
        if (previous != null && valid(previous.context(), context)) {
            return new Result(CaptureSelectionState.COMPLETE, previous.firstCorner(), clicked);
        }
        pending.put(context.playerId(), new Pending(context, clicked));
        return new Result(CaptureSelectionState.FIRST_CORNER, clicked, null);
    }

    public BlockPos firstCorner(UUID playerId) {
        Pending current = pending.get(playerId);
        return current == null ? null : current.firstCorner();
    }

    public void cancel(UUID playerId) {
        pending.remove(playerId);
    }

    public boolean valid(Context current) {
        Pending previous = pending.get(current.playerId());
        if (previous == null) return false;
        if (valid(previous.context(), current)) return true;
        pending.remove(current.playerId());
        return false;
    }

    private static boolean valid(Context first, Context current) {
        return first.stack() == current.stack()
                && first.selectedSlot() == current.selectedSlot()
                && first.dimension().equals(current.dimension())
                && current.tick() >= first.tick()
                && current.tick() - first.tick() <= TIMEOUT_TICKS;
    }
}
