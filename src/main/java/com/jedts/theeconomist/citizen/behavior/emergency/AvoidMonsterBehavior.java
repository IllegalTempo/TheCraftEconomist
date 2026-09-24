package com.jedts.theeconomist.citizen.behavior.emergency;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.SurvivalDecisionRules;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

public final class AvoidMonsterBehavior implements CitizenBehavior {
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    private Monster threat;
    @Override public String id() { return "avoid_monster"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var citizen = context.citizen();
        var scoring = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring();
        var nearest = context.level().getEntitiesOfClass(Monster.class,
                        citizen.getBoundingBox().inflate(scoring.value("avoid_monster", "threatSearchRange")), Monster::isAlive).stream()
                .min(Comparator.comparingDouble(citizen::distanceTo)).orElse(null);
        if (nearest == null) {
            threat = null;
            clearStaleMemory(context);
            return SurvivalDecisionRules.avoidMonster(false, 0, citizen.stats().safety(),
                    citizen.stats().bravery(), citizen.getHealth() / citizen.getMaxHealth(), scoring);
        }
        threat = nearest;
        return SurvivalDecisionRules.avoidMonster(true, citizen.distanceTo(nearest), citizen.stats().safety(),
                citizen.stats().bravery(), citizen.getHealth() / citizen.getMaxHealth(), scoring);
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        double threatRange = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring()
                .value("avoid_monster", "threatSearchRange");
        threat = context.level().getEntitiesOfClass(Monster.class,
                        context.citizen().getBoundingBox().inflate(threatRange), Monster::isAlive).stream()
                .min(Comparator.comparingDouble(context.citizen()::distanceToSqr)).orElse(null);
        return threat != null;
    }
    @Override public void tick(CitizenBehaviorContext context) {
        if (threat == null) return;
        targetMemory.remember(context, "avoid_monster.threat", threat.blockPosition());
        var away = DefaultRandomPos.getPosAway(context.citizen(), 12, 5, threat.position());
        if (away != null) context.navigation().moveTo(away.x, away.y, away.z, 1.25);
    }
    @Override public String status(CitizenBehaviorContext context) { return "Avoiding monster"; }

    private void clearStaleMemory(CitizenBehaviorContext context) {
        BlockPos saved = targetMemory.get(context, "avoid_monster.threat").orElse(null);
        if (saved == null || !context.level().hasChunkAt(saved)) return;
        if (context.level().getEntitiesOfClass(Monster.class, new AABB(saved).inflate(.5), Monster::isAlive).isEmpty())
            targetMemory.forget(context, "avoid_monster.threat");
    }
}
