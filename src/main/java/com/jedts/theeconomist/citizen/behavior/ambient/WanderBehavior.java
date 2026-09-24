package com.jedts.theeconomist.citizen.behavior.ambient;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.AmbientDecisionRules;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;

public final class WanderBehavior implements CitizenBehavior {
    private long nextAttempt;
    @Override public String id() { return "wander"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var scoring = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring();
        return AmbientDecisionRules.wander(
                !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime()),
                context.gameTime() >= nextAttempt, context.citizen().stats().morale(),
                context.citizen().stats().safety(), true, scoring);
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        return !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime())
                && context.gameTime() >= nextAttempt;
    }
    @Override public boolean canContinue(CitizenBehaviorContext context) {
        return !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime())
                && !context.navigation().isDone();
    }
    @Override public void start(CitizenBehaviorContext context) {
        nextAttempt = context.gameTime() + (long)com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring()
                .value("wander", "retryTicks");
        var pos = DefaultRandomPos.getPos(context.citizen(), 8, 3);
        if (pos != null) context.navigation().moveTo(pos.x, pos.y, pos.z, .8);
    }
    @Override public void tick(CitizenBehaviorContext context) { }
    @Override public String status(CitizenBehaviorContext context) { return "Walking nearby"; }
}
