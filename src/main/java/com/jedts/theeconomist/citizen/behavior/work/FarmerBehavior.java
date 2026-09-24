package com.jedts.theeconomist.citizen.behavior.work;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.RoutineDecisionRules;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaimHooks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FarmerBehavior implements CitizenBehavior {
    private long nextWorkTick;
    private static final long WORK_INTERVAL_TICKS = 5;
    @Override public String id() { return "farmer"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var citizen = context.citizen();
        var scoring = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring();
        String status = citizen.farmStatus();
        boolean hasHoe = false;
        for (int slot = 0; slot < citizen.farmInventory().size(); slot++)
            if (PlotClaimHooks.isHoe(citizen.farmInventory().slot(slot))) { hasHoe = true; break; }
        boolean hasSeeds = citizen.farmInventory().count(Items.WHEAT_SEEDS) > 0;
        boolean hasCapacity = citizen.farmInventory().canInsert(new ItemStack(Items.WHEAT));
        boolean targetAvailable = !status.startsWith("Work paused") && hasCapacity;
        boolean hasHome = context.home().isPresent();
        double workRadius = scoring.value("farmer", "workRadius");
        boolean withinWorkArea = context.home().filter(home -> horizontalDistanceSquared(citizen.blockPosition(), home) <= workRadius * workRadius).isPresent();
        return RoutineDecisionRules.farming(citizen.identity().lifeStage().canWork(),
                !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime()),
                hasHome, withinWorkArea, citizen.stats().ambition(), citizen.skills().farming(),
                targetAvailable, hasHoe, hasSeeds, hasCapacity ? 1 : 0, scoring);
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        double workRadius = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring().value("farmer", "workRadius");
        if (CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime())
                || !context.citizen().identity().lifeStage().canWork()) return false;
        return context.home().isPresent() && horizontalDistanceSquared(context.citizen().blockPosition(), context.home().orElseThrow()) <= workRadius * workRadius;
    }
    @Override public void tick(CitizenBehaviorContext context) {
        if (context.gameTime() >= nextWorkTick) {
            context.citizen().runFarmerWorkCycle();
            nextWorkTick = context.gameTime() + WORK_INTERVAL_TICKS;
        }
    }
    @Override public String status(CitizenBehaviorContext context) { return context.citizen().farmStatus(); }

    private static double horizontalDistanceSquared(net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) {
        long dx = (long) a.getX() - b.getX(), dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
