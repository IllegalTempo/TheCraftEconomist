package com.jedts.theeconomist.citizen.behavior.target;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import net.minecraft.core.BlockPos;

/** Incremental 256-block home-centred search. It never asks the world to load a chunk. */
public final class CitizenTargetSearchService {
    public static final int SEARCH_RADIUS = 256;
    private static final int WAYPOINT_SPACING = 16;
    private static final int LOCAL_RADIUS = 8;
    private static final int GRID_RADIUS = SEARCH_RADIUS / WAYPOINT_SPACING;
    private static final int ROUTE_STEPS = (GRID_RADIUS * 2 + 1) * (GRID_RADIUS * 2 + 1);
    private final CitizenTargetMemoryStore memory = new CitizenTargetMemoryStore();

    public CitizenTargetSearchResult tick(CitizenBehaviorContext context, String id, CitizenTargetFinder finder, double speed) {
        BlockPos home = context.home().orElse(context.citizen().blockPosition());
        var citizen = context.citizen();
        String dimension = context.level().dimension().identifier().toString();
        if (citizen.targetSearchDimension(id).filter(dimension::equals).isEmpty()) {
            citizen.targetSearchCursor(id, 0);
            citizen.targetSearchDimension(id, dimension);
            memory.forget(context, id);
        }
        // Nearby targets take priority over family history so relatives do not travel to a
        // distant remembered location while an eligible target is already close by.
        if (context.level().hasChunkAt(citizen.blockPosition())) {
            var nearby = finder.findNear(context, citizen.blockPosition(), LOCAL_RADIUS);
            if (nearby.isPresent() && withinRadius(home, nearby.orElseThrow())) {
                BlockPos location = nearby.orElseThrow().immutable();
                memory.remember(context, id, location);
                return CitizenTargetSearchResult.found(location);
            }
        }
        var saved = memory.get(context, id);
        if (saved.isPresent()) {
            BlockPos target = saved.orElseThrow();
            if (!withinRadius(home, target)) memory.forget(context, id);
            else if (context.level().hasChunkAt(target) && finder.findAt(context, target).isEmpty()) {
                memory.forget(context, id);
                context.navigation().stop();
                // Do not keep navigating to a remembered location after its target disappears.
                // Advance past the route waypoint too, so the next search does not immediately
                // revisit the empty area.
                citizen.targetSearchCursor(id, citizen.targetSearchCursor(id) + 1);
            }
            else if (horizontalDistanceSquared(citizen.blockPosition(), target) > 9) {
                if ((context.navigation().isDone() || !context.navigation().isInProgress())
                        && !context.navigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, speed))
                    memory.forget(context, id);
                else return CitizenTargetSearchResult.of(CitizenTargetSearchResult.State.WALKING_TO_MEMORY);
            } else if (context.level().hasChunkAt(target)) {
                var found = finder.findAt(context, target);
                if (found.isPresent()) { memory.remember(context, id, found.orElseThrow()); return CitizenTargetSearchResult.found(found.orElseThrow()); }
                memory.forget(context, id);
                // The target can disappear between the validation above and this lookup.
                citizen.targetSearchCursor(id, citizen.targetSearchCursor(id) + 1);
            }
        }

        int cursor = citizen.targetSearchCursor(id);
        for (int attempts = 0; attempts < 128; attempts++, cursor++) {
            if (cursor >= ROUTE_STEPS) cursor = 0;
            BlockPos waypoint = waypoint(home, cursor);
            if (!withinRadius(home, waypoint)) continue;
            if (horizontalDistanceSquared(citizen.blockPosition(), waypoint) > 9) {
                citizen.targetSearchCursor(id, cursor);
                if (context.navigation().isDone() || !context.navigation().isInProgress()) {
                    if (!context.navigation().moveTo(waypoint.getX() + .5, waypoint.getY(), waypoint.getZ() + .5, speed)) {
                        citizen.targetSearchCursor(id, cursor + 1);
                        continue;
                    }
                }
                return CitizenTargetSearchResult.of(CitizenTargetSearchResult.State.WALKING_SEARCH_ROUTE);
            }
            if (context.level().hasChunkAt(waypoint)) {
                var found = finder.findNear(context, waypoint, LOCAL_RADIUS);
                if (found.isPresent() && withinRadius(home, found.orElseThrow())) {
                    memory.remember(context, id, found.orElseThrow());
                    // Resume beyond this waypoint once the gathered target is gone. Keeping
                    // the cursor here makes each new search revisit the same depleted area.
                    citizen.targetSearchCursor(id, cursor + 1);
                    return CitizenTargetSearchResult.found(found.orElseThrow());
                }
            }
        }
        if (cursor >= ROUTE_STEPS) cursor = 0;
        citizen.targetSearchCursor(id, cursor);
        return CitizenTargetSearchResult.of(CitizenTargetSearchResult.State.SEARCHING);
    }

    private static boolean withinRadius(BlockPos home, BlockPos pos) { return horizontalDistanceSquared(home, pos) <= (long) SEARCH_RADIUS * SEARCH_RADIUS; }
    private static long horizontalDistanceSquared(BlockPos a, BlockPos b) { long dx = (long)a.getX()-b.getX(), dz=(long)a.getZ()-b.getZ(); return dx*dx+dz*dz; }

    /** Maps a non-negative index to a deterministic square spiral, spaced by one local scan diameter. */
    private static BlockPos waypoint(BlockPos home, int index) {
        int x = 0, z = 0, dx = 0, dz = -1;
        int steps = Math.min(index, ROUTE_STEPS - 1);
        for (int i = 0; i < steps; i++) {
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) { int temp = dx; dx = -dz; dz = temp; }
            x += dx; z += dz;
        }
        return home.offset(x * WAYPOINT_SPACING, 0, z * WAYPOINT_SPACING);
    }
}
