package com.jedts.theeconomist.citizen.behavior.home;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.RoutineDecisionRules;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;

public final class ReturnHomeBehavior implements CitizenBehavior {
    @Override public String id() { return "return_home"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var scoring = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring();
        var home = context.home().orElse(null);
        double distance = home == null ? 0.0 : Math.sqrt(context.citizen().blockPosition().distSqr(home));
        return RoutineDecisionRules.returnHome(home != null, distance,
                context.citizen().stats().safety(), context.citizen().stats().energy(),
                CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime()), scoring);
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        double radius = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring()
                .value("return_home", "returnHomeRadius");
        return context.home().filter(home -> context.citizen().blockPosition().distSqr(home) > radius * radius).isPresent();
    }
    @Override public void tick(CitizenBehaviorContext context) {
        var home = context.home().orElseThrow();
        context.navigation().moveTo(home.getX() + .5, home.getY(), home.getZ() + .5, .8);
    }
    @Override public String status(CitizenBehaviorContext context) { return "Returning home"; }
}
