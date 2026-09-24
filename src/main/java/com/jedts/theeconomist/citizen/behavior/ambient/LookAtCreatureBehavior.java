package com.jedts.theeconomist.citizen.behavior.ambient;

import com.jedts.theeconomist.citizen.CitizenRuntime;
import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.AmbientDecisionRules;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.sleep.CitizenSleepSchedule;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class LookAtCreatureBehavior implements CitizenBehavior {
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    private LivingEntity creature;

    @Override public String id() { return "look_at_creature"; }

    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var scoring = CitizenRuntime.decisionScoring();
        boolean daytime = !CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime());
        creature = daytime ? nearestCreature(context, scoring.value("look_at_creature", "creatureRange")) : null;
        clearStaleMemory(context);
        double distance = creature == null ? scoring.value("look_at_creature", "creatureRange")
                : context.citizen().distanceTo(creature);
        return AmbientDecisionRules.lookAtCreature(daytime, creature != null, distance, scoring);
    }

    @Override public boolean canStart(CitizenBehaviorContext context) {
        if (CitizenSleepSchedule.isSleepingTime(context.level().getDefaultClockTime())) return false;
        creature = nearestCreature(context, CitizenRuntime.decisionScoring()
                .value("look_at_creature", "creatureRange"));
        return creature != null;
    }

    @Override public void tick(CitizenBehaviorContext context) {
        context.navigation().stop();
        if (creature != null && creature.isAlive()) {
            targetMemory.remember(context, "look_at_creature.entity", creature.blockPosition());
            context.citizen().getLookControl().setLookAt(creature, 30, 30);
        }
    }

    @Override public String status(CitizenBehaviorContext context) { return "Watching nearby creature"; }

    private LivingEntity nearestCreature(CitizenBehaviorContext context, double range) {
        var citizen = context.citizen();
        return context.level().getEntitiesOfClass(LivingEntity.class, citizen.getBoundingBox().inflate(range),
                        candidate -> candidate != citizen && (candidate instanceof Mob || candidate instanceof Player)
                                && candidate.isAlive())
                .stream().min(java.util.Comparator.comparingDouble(citizen::distanceTo)).orElse(null);
    }

    private void clearStaleMemory(CitizenBehaviorContext context) {
        BlockPos saved = targetMemory.get(context, "look_at_creature.entity").orElse(null);
        if (saved == null || !context.level().hasChunkAt(saved)) return;
        boolean present = !context.level().getEntitiesOfClass(LivingEntity.class, new AABB(saved).inflate(0.5),
                candidate -> candidate != context.citizen() && (candidate instanceof Mob || candidate instanceof Player)
                        && candidate.isAlive()).isEmpty();
        if (!present) targetMemory.forget(context, "look_at_creature.entity");
    }
}
