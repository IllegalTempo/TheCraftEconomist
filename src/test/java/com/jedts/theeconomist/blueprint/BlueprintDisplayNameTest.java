package com.jedts.theeconomist.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintDisplayNameTest {
    @Test
    void names_match_blueprint_lifecycle_states() {
        assertEquals("Empty Blueprint", BlueprintDisplayName.forState(BlueprintState.EMPTY));
        assertEquals("Designed Blueprint", BlueprintDisplayName.forState(BlueprintState.DESIGNED));
        assertEquals("Planned Blueprint", BlueprintDisplayName.forState(BlueprintState.PLANNED));
    }
}
