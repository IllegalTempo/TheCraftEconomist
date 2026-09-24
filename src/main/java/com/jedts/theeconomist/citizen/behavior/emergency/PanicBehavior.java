package com.jedts.theeconomist.citizen.behavior.emergency;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.SurvivalDecisionRules;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class PanicBehavior implements CitizenBehavior {
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    @Override public String id() { return "panic"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var attacker = context.citizen().getLastHurtByMob();
        if (attacker == null || !attacker.isAlive()) {
            attacker = null;
            clearStaleMemory(context);
        }
        double distance = attacker == null ? 24.0 : context.citizen().distanceTo(attacker);
        var stats = context.citizen().stats();
        return SurvivalDecisionRules.panic(context.citizen().hurtTime > 0,
                context.citizen().getHealth() / context.citizen().getMaxHealth(), attacker != null,
                distance, stats.bravery(), stats.safety(),
                com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring());
    }
    @Override public boolean canStart(CitizenBehaviorContext context) { return context.citizen().hurtTime > 0; }
    @Override public void tick(CitizenBehaviorContext context) {
        var attacker = context.citizen().getLastHurtByMob();
        if (attacker == null || !attacker.isAlive()) return;
        targetMemory.remember(context, "panic.attacker", attacker.blockPosition());
        var away = DefaultRandomPos.getPosAway(context.citizen(), 12, 5, attacker.position());
        if (away != null) context.navigation().moveTo(away.x, away.y, away.z, 1.25);
    }
    @Override public String status(CitizenBehaviorContext context) { return "Fleeing after injury"; }

    private void clearStaleMemory(CitizenBehaviorContext context) {
        BlockPos saved = targetMemory.get(context, "panic.attacker").orElse(null);
        if (saved == null || !context.level().hasChunkAt(saved)) return;
        if (context.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new AABB(saved).inflate(.5), entity -> entity != context.citizen() && entity.isAlive()).isEmpty())
            targetMemory.forget(context, "panic.attacker");
    }
}
