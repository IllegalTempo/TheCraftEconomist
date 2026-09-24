package com.jedts.theeconomist.citizen.behavior.emergency;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.SurvivalDecisionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class EscapeWaterBehavior implements CitizenBehavior {
    private static final int SEARCH_RADIUS = 12;
    private static final int MAX_FAILED_TARGETS = 32;
    private final Set<BlockPos> failedTargets = new HashSet<>();
    private BlockPos escapeTarget;
    private int retryDelay;
    private int stalledTicks;
    private double lastX;
    private double lastY;
    private double lastZ;

    @Override public String id() { return "escape_water"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        return SurvivalDecisionRules.escapeLiquid(context.citizen().isInWater() || context.citizen().isInLava(),
                context.citizen().getHealth() / context.citizen().getMaxHealth(),
                com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring());
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        return context.citizen().isInWater() || context.citizen().isInLava();
    }
    @Override public void start(CitizenBehaviorContext context) {
        failedTargets.clear();
        escapeTarget = null;
        retryDelay = 0;
        stalledTicks = 0;
    }

    @Override public void tick(CitizenBehaviorContext context) {
        var citizen = context.citizen();
        if (!inLiquid(citizen)) return;

        if (escapeTarget != null) {
            double dx = citizen.getX() - (escapeTarget.getX() + 0.5);
            double dz = citizen.getZ() - (escapeTarget.getZ() + 0.5);
            if (dx * dx + dz * dz <= 2.25 && isDryStandable(context, escapeTarget)) {
                abandonTarget(context);
            } else if (context.navigation().isInProgress()) {
                double moveX = citizen.getX() - lastX;
                double moveY = citizen.getY() - lastY;
                double moveZ = citizen.getZ() - lastZ;
                if (moveX * moveX + moveY * moveY + moveZ * moveZ >= 0.04) {
                    lastX = citizen.getX();
                    lastY = citizen.getY();
                    lastZ = citizen.getZ();
                    stalledTicks = 0;
                } else if (++stalledTicks >= 40) {
                    abandonTarget(context);
                }
                if (escapeTarget == null) {
                    citizen.getJumpControl().jump();
                } else {
                    citizen.getJumpControl().jump();
                    return;
                }
            } else {
                abandonTarget(context);
            }
        }

        if (retryDelay > 0) {
            retryDelay--;
            citizen.getJumpControl().jump();
            return;
        }

        for (BlockPos candidate : dryCandidates(context)) {
            if (failedTargets.contains(candidate)) continue;
            if (context.navigation().moveTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5, 1.25)) {
                escapeTarget = candidate.immutable();
                lastX = citizen.getX();
                lastY = citizen.getY();
                lastZ = citizen.getZ();
                stalledTicks = 0;
                citizen.getJumpControl().jump();
                return;
            }
            failedTargets.add(candidate.immutable());
            if (failedTargets.size() >= MAX_FAILED_TARGETS) break;
        }

        if (failedTargets.size() >= MAX_FAILED_TARGETS) {
            failedTargets.clear();
            retryDelay = 20;
        } else {
            retryDelay = 10;
        }
        citizen.getJumpControl().jump();
    }

    @Override public void stop(CitizenBehaviorContext context, com.jedts.theeconomist.citizen.behavior.CitizenBehaviorStopReason reason) {
        context.navigation().stop();
        failedTargets.clear();
        escapeTarget = null;
        retryDelay = 0;
        stalledTicks = 0;
    }

    @Override public String status(CitizenBehaviorContext context) { return "Escaping liquid"; }

    private static boolean inLiquid(net.minecraft.world.entity.LivingEntity citizen) {
        return citizen.isInWater() || citizen.isInLava();
    }

    private static java.util.List<BlockPos> dryCandidates(CitizenBehaviorContext context) {
        BlockPos origin = context.citizen().blockPosition();
        ArrayList<BlockPos> candidates = new ArrayList<>();
        for (int dy = -2; dy <= 3; dy++) for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++)
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                if (dx * dx + dz * dz < 9 || dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS) continue;
                BlockPos feet = origin.offset(dx, dy, dz);
                if (isDryStandable(context, feet)) candidates.add(feet.immutable());
            }
        candidates.sort(Comparator.comparingInt((BlockPos pos) -> horizontalDistanceSquared(origin, pos))
                .thenComparingInt(pos -> Math.abs(pos.getY() - origin.getY())));
        return candidates;
    }

    private static boolean isDryStandable(CitizenBehaviorContext context, BlockPos feet) {
        var level = context.level();
        BlockPos head = feet.above();
        BlockPos upper = head.above();
        BlockPos floor = feet.below();
        if (!level.hasChunkAt(floor) || !level.hasChunkAt(feet) || !level.hasChunkAt(head) || !level.hasChunkAt(upper)) return false;
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()
                || !level.getFluidState(upper).isEmpty()) return false;
        var citizen = context.citizen();
        AABB targetBox = citizen.getBoundingBox().move(feet.getX() + 0.5 - citizen.getX(),
                feet.getY() - citizen.getY(), feet.getZ() + 0.5 - citizen.getZ());
        return level.noCollision(citizen, targetBox);
    }

    private static int horizontalDistanceSquared(BlockPos from, BlockPos to) {
        int dx = from.getX() - to.getX();
        int dz = from.getZ() - to.getZ();
        return dx * dx + dz * dz;
    }

    private void abandonTarget(CitizenBehaviorContext context) {
        if (escapeTarget != null) failedTargets.add(escapeTarget);
        escapeTarget = null;
        stalledTicks = 0;
        context.navigation().stop();
    }
}
