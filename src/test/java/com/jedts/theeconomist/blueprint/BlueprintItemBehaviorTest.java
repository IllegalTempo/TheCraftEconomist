package com.jedts.theeconomist.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintItemBehaviorTest {
    @Test
    void empty_and_designed_states_start_the_matching_mode() {
        assertEquals(BlueprintItemAction.DESIGN, BlueprintItemBehavior.action(BlueprintState.EMPTY));
        assertEquals(BlueprintItemAction.PLACE, BlueprintItemBehavior.action(BlueprintState.DESIGNED));
        assertEquals(BlueprintItemAction.NONE, BlueprintItemBehavior.action(BlueprintState.PLANNED));
    }
}
