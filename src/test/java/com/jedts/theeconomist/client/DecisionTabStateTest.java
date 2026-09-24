package com.jedts.theeconomist.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DecisionTabStateTest {
    @Test
    void togglesActionDetailsIndependently() {
        var state = new DecisionTabState();
        assertTrue(state.toggle("farmer"));
        assertTrue(state.isExpanded("farmer"));
        assertTrue(state.toggle("sleep"));
        assertTrue(state.isExpanded("farmer"));
        assertTrue(state.isExpanded("sleep"));
        assertFalse(state.toggle("farmer"));
        assertFalse(state.isExpanded("farmer"));
        assertTrue(state.isExpanded("sleep"));
    }
}
