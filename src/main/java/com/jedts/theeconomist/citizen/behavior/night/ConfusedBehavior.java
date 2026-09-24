package com.jedts.theeconomist.citizen.behavior.night;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.RoutineDecisionRules;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;
import com.jedts.theeconomist.citizen.house.HouseholdSavedData;

/** Night state for Citizens that cannot resolve a generated household. */
public final class ConfusedBehavior implements CitizenBehavior {
    @Override public String id() { return "confused"; }

    @Override
    public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        boolean night = CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime());
        boolean householdIdMissing = context.householdId().isEmpty();
        boolean householdDataMissing = context.home().isEmpty() || !householdIdMissing
                && HouseholdSavedData.forLevel(context.level()).ledger()
                .get(context.home().orElseThrow()).isEmpty();
        return RoutineDecisionRules.confused(night, householdIdMissing, householdDataMissing,
                com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring());
    }

    @Override
    public boolean canStart(CitizenBehaviorContext context) {
        if (!CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime())) return false;
        if (context.householdId().isEmpty()) return true;
        return context.home().isEmpty() || HouseholdSavedData.forLevel(context.level()).ledger()
                .get(context.home().orElseThrow()).isEmpty();
    }

    @Override
    public void tick(CitizenBehaviorContext context) {
        context.navigation().stop();
    }

    @Override
    public String status(CitizenBehaviorContext context) {
        return context.householdId().isEmpty() ? "No generated household" : "Household record missing";
    }
}
