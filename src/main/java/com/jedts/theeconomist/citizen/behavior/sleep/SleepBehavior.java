package com.jedts.theeconomist.citizen.behavior.sleep;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorStopReason;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.RoutineDecisionRules;
import com.jedts.theeconomist.citizen.house.HouseholdSavedData;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetFinder;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetSearchService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;

/** Sends a generated-house resident to any free bed in that household at night. */
public final class SleepBehavior implements CitizenBehavior {
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    private final CitizenTargetSearchService targetSearch = new CitizenTargetSearchService();
    private final CitizenTargetFinder bedFinder = new BedTargetFinder();
    private static final int HORIZONTAL_RANGE = 12;
    private static final int VERTICAL_RANGE = 5;
    private static final long RETRY_TICKS = 40;
    private static final double BED_REACHED_DISTANCE_SQUARED = 2.25;

    private BlockPos bed;
    private BlockPos approach;
    private BlockPos temporarilyUnavailableBed;
    private long temporarilyUnavailableUntil;
    private long retryAt;
    private String status = "Finding bed";

    @Override public String id() { return "sleep"; }

    @Override
    public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        boolean night = CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime());
        boolean household = hasGeneratedHousehold(context);
        boolean bedAvailable = bed != null && isUsableBed(context, bed);
        // Initial availability is unknown until the behavior performs its first bounded bed search.
        if (night && household && bed == null && retryAt == 0) bedAvailable = true;
        double distance = context.home().map(home -> Math.sqrt(context.citizen().blockPosition().distSqr(home)))
                .orElse(0.0);
        return RoutineDecisionRules.sleep(night, household, context.citizen().stats().energy(),
                bedAvailable, distance, com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring());
    }

    @Override
    public boolean canStart(CitizenBehaviorContext context) {
        return CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime())
                && hasGeneratedHousehold(context);
    }

    @Override
    public void start(CitizenBehaviorContext context) {
        status = "Finding bed";
        retryAt = 0;
        openHouseDoor(context);
    }

    @Override
    public void tick(CitizenBehaviorContext context) {
        var citizen = context.citizen();
        if (citizen.isSleeping()) {
            if (bed != null && isUsableBed(context, bed)) {
                status = "Sleeping";
                return;
            }
            citizen.stopSleeping();
            releaseBed(context);
        }

        if (bed != null && !isUsableBed(context, bed)) {
            context.navigation().stop();
            targetMemory.forget(context, "sleep.bed");
            releaseBed(context);
            retryAt = context.gameTime() + RETRY_TICKS;
        }

        if (bed == null) {
            if (context.gameTime() < retryAt) {
                status = "Waiting for bed";
                return;
            }
            findBed(context);
            if (bed == null) {
                status = "Searching for bed";
                retryAt = context.gameTime() + 5;
                return;
            }
        }

        citizen.faceBlock(bed);
        if (distanceToBedSquared(context, bed) <= BED_REACHED_DISTANCE_SQUARED) {
            context.navigation().stop();
            if (citizen.startSleeping(bed)) status = "Sleeping";
            else {
                markBedUnavailable(context, "Bed could not be entered; trying another bed");
                releaseBed(context);
                retryAt = context.gameTime() + 5;
            }
            return;
        }

        status = "Walking to bed";
        // Reassert movement after vanilla mob ticking until the remaining legacy goals are migrated.
        // The behavior controller is the final movement owner for this tick.
        var path = context.navigation().createPath(approach, 0);
        if (path == null || !context.navigation().moveTo(path, 1.0)) {
            markBedUnavailable(context, "Bed approach unreachable; trying another bed");
            releaseBed(context);
            retryAt = context.gameTime() + 5;
        }
    }

    @Override
    public void stop(CitizenBehaviorContext context, CitizenBehaviorStopReason reason) {
        if (context.citizen().isSleeping()) context.citizen().stopSleeping();
        context.navigation().stop();
        releaseBed(context);
        status = "";
    }

    @Override public String status(CitizenBehaviorContext context) { return status; }

    private boolean hasGeneratedHousehold(CitizenBehaviorContext context) {
        if (context.householdId().isEmpty() || context.home().isEmpty()) return false;
        return HouseholdSavedData.forLevel(context.level()).ledger().get(context.home().orElseThrow()).isPresent();
    }

    private void findBed(CitizenBehaviorContext context) {
        var result = targetSearch.tick(context, "sleep.bed", bedFinder, 1.0);
        if (result.location().isEmpty()) return;
        BlockPos selected = result.location().orElseThrow();
        BlockPos standAt = findApproach(context, selected);
        if (standAt == null) {
            targetMemory.forget(context, "sleep.bed");
            return;
        }
        bed = selected;
        approach = standAt;
    }

    private final class BedTargetFinder implements CitizenTargetFinder {
        @Override public java.util.Optional<BlockPos> findAt(CitizenBehaviorContext context, BlockPos location) {
            if (isTemporarilyUnavailable(context, location) || !isUsableBed(context, location) || findApproach(context, location) == null)
                return java.util.Optional.empty();
            return java.util.Optional.of(location);
        }
        @Override public java.util.Optional<BlockPos> findNear(CitizenBehaviorContext context, BlockPos waypoint, int radius) {
            var level = context.level();
            var candidates = new ArrayList<CitizenBedCandidate>();
            for (int y = waypoint.getY() - VERTICAL_RANGE; y <= waypoint.getY() + VERTICAL_RANGE; y++)
                for (int z = waypoint.getZ() - radius; z <= waypoint.getZ() + radius; z++)
                    for (int x = waypoint.getX() - radius; x <= waypoint.getX() + radius; x++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.hasChunkAt(pos)) continue;
                        var state = level.getBlockState(pos);
                        if (!(state.getBlock() instanceof AbstractBedBlock) || state.getValue(AbstractBedBlock.PART) != BedPart.HEAD) continue;
                        if (isTemporarilyUnavailable(context, pos)) continue;
                        boolean reachable = findApproach(context, pos) != null;
                        candidates.add(new CitizenBedCandidate(pos, state.getValue(AbstractBedBlock.OCCUPIED), reachable));
                    }
            return CitizenBedSelector.select(context.home().orElse(waypoint), candidates);
        }
    }

    private void openHouseDoor(CitizenBehaviorContext context) {
        BlockPos home = context.home().orElseThrow();
        for (BlockPos mutable : BlockPos.betweenClosed(home.offset(-2, -1, -2), home.offset(2, 2, 2))) {
            BlockPos pos = mutable.immutable();
            var state = context.level().getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN))
                context.level().setBlock(pos, state.setValue(DoorBlock.OPEN, true), 10);
        }
    }

    private BlockPos findApproach(CitizenBehaviorContext context, BlockPos bedPos) {
        var bedState = context.level().getBlockState(bedPos);
        if (!(bedState.getBlock() instanceof AbstractBedBlock)
                || bedState.getValue(AbstractBedBlock.PART) != BedPart.HEAD) return null;
        // The head is commonly against a wall. Approach from around the foot
        // half so beds in the generated house remain reachable.
        BlockPos footPos = bedPos.relative(bedState.getValue(AbstractBedBlock.FACING).getOpposite());
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int[] offset : offsets) {
            BlockPos candidate = footPos.offset(offset[0], 0, offset[1]);
            if (!context.level().hasChunkAt(candidate) || !context.level().hasChunkAt(candidate.above())
                    || !context.level().getBlockState(candidate).isAir()
                    || !context.level().getBlockState(candidate.above()).isAir()) continue;
            var path = context.navigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) continue;
            double distance = context.citizen().distanceToSqr(candidate.getX() + .5,
                    candidate.getY(), candidate.getZ() + .5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private double distanceToBedSquared(CitizenBehaviorContext context, BlockPos headPos) {
        var state = context.level().getBlockState(headPos);
        BlockPos footPos = headPos.relative(state.getValue(AbstractBedBlock.FACING).getOpposite());
        var citizen = context.citizen();
        double headDistance = citizen.distanceToSqr(headPos.getX() + 0.5, headPos.getY() + 0.5, headPos.getZ() + 0.5);
        double footDistance = citizen.distanceToSqr(footPos.getX() + 0.5, footPos.getY() + 0.5, footPos.getZ() + 0.5);
        return Math.min(headDistance, footDistance);
    }

    private boolean isUsableBed(CitizenBehaviorContext context, BlockPos pos) {
        if (!context.level().hasChunkAt(pos)) return false;
        var state = context.level().getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractBedBlock)
                || state.getValue(AbstractBedBlock.PART) != BedPart.HEAD) return false;
        return !state.getValue(AbstractBedBlock.OCCUPIED) || context.citizen().isSleeping();
    }

    private boolean isTemporarilyUnavailable(CitizenBehaviorContext context, BlockPos pos) {
        if (temporarilyUnavailableBed == null || !temporarilyUnavailableBed.equals(pos)) return false;
        if (context.gameTime() < temporarilyUnavailableUntil) return true;
        temporarilyUnavailableBed = null;
        return false;
    }

    private void markBedUnavailable(CitizenBehaviorContext context, String failureStatus) {
        if (bed != null) {
            temporarilyUnavailableBed = bed.immutable();
            temporarilyUnavailableUntil = context.gameTime() + RETRY_TICKS;
            targetMemory.forget(context, "sleep.bed");
        }
        status = failureStatus;
    }

    private void releaseBed(CitizenBehaviorContext context) {
        if (bed != null) {
            bed = null;
            approach = null;
        }
    }
}
