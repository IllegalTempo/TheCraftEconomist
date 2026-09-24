package com.jedts.theeconomist.citizen.behavior;

import com.jedts.theeconomist.citizen.behavior.decision.CitizenActionEvaluation;
import com.jedts.theeconomist.citizen.info.CitizenCraftingPreview;

import java.util.Optional;

public interface CitizenBehavior {
    String id();
    boolean canStart(CitizenBehaviorContext context);
    CitizenActionEvaluation evaluate(CitizenBehaviorContext context);

    default boolean canContinue(CitizenBehaviorContext context) {
        return true;
    }

    default void start(CitizenBehaviorContext context) { }

    default Optional<CitizenCraftingPreview> craftingPreview() { return Optional.empty(); }

    void tick(CitizenBehaviorContext context);

    default void stop(CitizenBehaviorContext context, CitizenBehaviorStopReason reason) { }

    String status(CitizenBehaviorContext context);
}
