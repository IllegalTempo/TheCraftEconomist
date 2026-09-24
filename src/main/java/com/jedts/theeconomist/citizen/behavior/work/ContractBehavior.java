package com.jedts.theeconomist.citizen.behavior.work;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.RoutineDecisionRules;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;
import com.jedts.theeconomist.citizen.job.CitizenOccupation;

import java.util.Comparator;

public final class ContractBehavior implements CitizenBehavior {
    private long nextCycle;
    @Override public String id() { return "contract"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var scoring = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring();
        boolean day = !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime());
        boolean ready = context.gameTime() >= nextCycle;
        var citizen = context.citizen();
        boolean activeContract = CitizenRuntime.contracts().activeContractFor(citizen.getUUID()).isPresent();
        var offer = CitizenRuntime.contracts().openContracts().stream()
                .filter(contract -> contract.deadlineTick() > context.gameTime())
                .filter(contract -> contract.requiredSkill() <= citizen.skills().highestValue())
                .min(Comparator.comparingLong(contract -> contract.deadlineTick())).orElse(null);
        boolean offerAvailable = citizen.job().occupation() == CitizenOccupation.UNEMPLOYED && offer != null;
        long ticksToDeadline = offerAvailable ? offer.deadlineTick() - context.gameTime() : 200;
        return RoutineDecisionRules.contractReview(day, ready,
                citizen.identity().lifeStage().canWork(), offerAvailable, activeContract,
                citizen.stats().ambition(), ticksToDeadline, scoring);
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        return !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime())
                && context.gameTime() >= nextCycle;
    }
    @Override public void tick(CitizenBehaviorContext context) {
        context.citizen().advanceStatsAndConsiderContracts();
        nextCycle = context.gameTime() + (long)com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring()
                .value("contract", "reviewIntervalTicks");
    }
    @Override public String status(CitizenBehaviorContext context) { return "Reviewing work and contracts"; }
}
