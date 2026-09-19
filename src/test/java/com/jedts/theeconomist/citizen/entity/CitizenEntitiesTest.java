package com.jedts.theeconomist.citizen.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CitizenEntitiesTest {
    @Test
    void exposesTheCustomCitizenEntityType() {
        assertEquals("theeconomist:citizen", CitizenEntities.ID.toString());
    }
}
