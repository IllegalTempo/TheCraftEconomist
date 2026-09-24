package com.jedts.theeconomist.citizen.behavior.target;

import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.house.Household;
import com.jedts.theeconomist.citizen.house.HouseholdSavedData;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public final class CitizenTargetMemoryStore {
    public Optional<BlockPos> get(CitizenBehaviorContext context, String id) {
        return household(context).flatMap(h -> h.targetLocation(id)).or(() -> context.citizen().localTargetLocation(id));
    }
    public void remember(CitizenBehaviorContext context, String id, BlockPos pos) {
        validate(id);
        Household household = household(context).orElse(null);
        if (household != null) household.rememberTarget(id, pos); else context.citizen().rememberLocalTarget(id, pos);
    }
    public void forget(CitizenBehaviorContext context, String id) {
        validate(id);
        Household household = household(context).orElse(null);
        if (household != null) household.forgetTarget(id); else context.citizen().forgetLocalTarget(id);
    }
    private Optional<Household> household(CitizenBehaviorContext context) {
        if (context.householdId().isEmpty() || context.home().isEmpty()) return Optional.empty();
        return HouseholdSavedData.forLevel(context.level()).ledger().get(context.home().orElseThrow())
                .filter(h -> h.householdId().equals(context.householdId().orElseThrow()));
    }
    private void validate(String id) { if (id == null || id.isBlank()) throw new IllegalArgumentException("target action ID must not be blank"); }
}
