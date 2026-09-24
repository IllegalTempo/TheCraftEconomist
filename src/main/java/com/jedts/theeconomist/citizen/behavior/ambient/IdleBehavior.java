package com.jedts.theeconomist.citizen.behavior.ambient;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.AmbientDecisionRules;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;

public final class IdleBehavior implements CitizenBehavior {
    @Override public String id() { return "idle"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        return AmbientDecisionRules.idle(!CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime()),
                com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring());
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        return true;
    }
    @Override public void tick(CitizenBehaviorContext context) { context.navigation().stop(); }
    @Override public String status(CitizenBehaviorContext context) { return "Idle"; }
}
