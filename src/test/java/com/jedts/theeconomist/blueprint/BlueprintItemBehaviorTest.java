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

    @Test
    void active_session_actions_take_precedence_over_stack_state() {
        assertEquals(BlueprintItemAction.SAVE_DESIGN,
                BlueprintItemBehavior.action(BlueprintState.EMPTY, BlueprintSessionMode.DESIGN));
        assertEquals(BlueprintItemAction.CONFIRM_PLACEMENT,
                BlueprintItemBehavior.action(BlueprintState.DESIGNED, BlueprintSessionMode.PLACEMENT));
        assertEquals(BlueprintItemAction.DESIGN,
                BlueprintItemBehavior.action(BlueprintState.EMPTY, BlueprintSessionMode.NONE));
    }
}
