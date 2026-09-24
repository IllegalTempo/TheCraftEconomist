package com.jedts.theeconomist.citizen.behavior.work;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenBehaviorEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenScoreInputs;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SeedFindingBehavior implements CitizenBehavior {
    private static final long WORK_INTERVAL_TICKS = 5;
    private long nextWorkTick;

    @Override public String id() { return "find_seeds"; }

    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var citizen = context.citizen();
        var config = CitizenRuntime.decisionScoring();
        boolean daytime = !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime());
        boolean hasHome = context.home().isPresent();
        double farmRadius = config.value("farmer", "workRadius");
        boolean inFarmArea = context.home().filter(home -> horizontalDistanceSquared(citizen.blockPosition(), home)
                <= farmRadius * farmRadius).isPresent();
        int seedCount = citizen.farmInventory().count(Items.WHEAT_SEEDS);
        boolean hasStorage = citizen.farmInventory().canInsert(new ItemStack(Items.WHEAT_SEEDS));

        if (!daytime) return CitizenBehaviorEvaluation.unavailable(id(), "Find seeds", "It is outside work hours");
        if (!citizen.identity().lifeStage().canWork())
            return CitizenBehaviorEvaluation.unavailable(id(), "Find seeds", "Citizen cannot work at this life stage");
        if (!hasHome) return CitizenBehaviorEvaluation.unavailable(id(), "Find seeds", "No home work area");
        if (!inFarmArea) return CitizenBehaviorEvaluation.unavailable(id(), "Find seeds", "Outside the home work area");
        if (seedCount > 0) return CitizenBehaviorEvaluation.unavailable(id(), "Find seeds", "Seed supply available");
        if (!hasStorage) return CitizenBehaviorEvaluation.unavailable(id(), "Find seeds", "Inventory is full");

        double skill = citizen.skills().farming() / config.statScale();
        double ambition = citizen.stats().ambition() / config.statScale();
        return CitizenBehaviorEvaluation.score(id(), "Find seeds", true,
                new CitizenScoreInputs(config.value("farmer", "urgencyBase")
                        + config.value("farmer", "urgencyAmbition") * unit(ambition),
                        config.value("farmer", "benefit"), config.value("farmer", "skillBase")
                        + config.value("farmer", "skillShare") * unit(skill),
                        config.value("farmer", "opportunity"), config.value("farmer", "cost"),
                        config.value("farmer", "risk")),
                "No wheat seeds; looking for nearby grass and ferns", false, config);
    }

    @Override public boolean canStart(CitizenBehaviorContext context) {
        return evaluate(context).eligible();
    }

    @Override public boolean canContinue(CitizenBehaviorContext context) {
        return evaluate(context).eligible();
    }

    @Override public void tick(CitizenBehaviorContext context) {
        if (context.gameTime() >= nextWorkTick) {
            context.citizen().runSeedFindingCycle();
            nextWorkTick = context.gameTime() + WORK_INTERVAL_TICKS;
        }
    }

    @Override public String status(CitizenBehaviorContext context) {
        return context.citizen().seedFindingStatus();
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
    private static double horizontalDistanceSquared(net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) {
        long dx = (long) a.getX() - b.getX(), dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
