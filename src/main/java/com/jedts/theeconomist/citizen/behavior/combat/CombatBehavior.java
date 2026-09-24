package com.jedts.theeconomist.citizen.behavior.combat;

import com.jedts.theeconomist.citizen.behavior.CitizenBehavior;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.behavior.decision.SurvivalDecisionRules;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;

public final class CombatBehavior implements CitizenBehavior {
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    private long nextAttack;
    @Override public String id() { return "combat"; }
    @Override public CitizenActionEvaluation evaluate(CitizenBehaviorContext context) {
        var scoring = com.jedts.theeconomist.citizen.CitizenRuntime.decisionScoring();
        var citizen = context.citizen();
        var target = citizen.getTarget();
        if (target == null || !target.isAlive() || target.level() != context.level()) {
            targetMemory.forget(context, "combat.challenger");
            return SurvivalDecisionRules.combat(false, 0, citizen.stats().anger(), citizen.stats().bravery(),
                    citizen.getHealth() / citizen.getMaxHealth(), citizen.getArmorValue(), 0, 0, scoring);
        }
        return SurvivalDecisionRules.combat(true, citizen.distanceTo(target), citizen.stats().anger(),
                citizen.stats().bravery(), citizen.getHealth() / citizen.getMaxHealth(), citizen.getArmorValue(),
                target.getHealth(), target.getArmorValue(), scoring);
    }
    @Override public boolean canStart(CitizenBehaviorContext context) {
        var target = context.citizen().getTarget();
        return target != null && target.isAlive() && target.level() == context.level();
    }
    @Override public void tick(CitizenBehaviorContext context) {
        var citizen = context.citizen();
        var target = citizen.getTarget();
        if (target == null) return;
        targetMemory.remember(context, "combat.challenger", target.blockPosition());
        citizen.getLookControl().setLookAt(target, 30.0f, 30.0f);
        context.navigation().moveTo(target, 1.1);
        if (context.gameTime() >= nextAttack && citizen.isWithinMeleeAttackRange(target)) {
            citizen.doHurtTarget(context.level(), target);
            nextAttack = context.gameTime() + 20;
        }
    }
    @Override public String status(CitizenBehaviorContext context) { return "Driving challenger away"; }
}
