package com.jedts.theeconomist.citizen.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenFamilyRoleTest {
    @Test
    void generatedHouseSlotsAlwaysBeginWithMomAndDad() {
        assertEquals(CitizenFamilyRole.MOM, CitizenFamilyRole.forHouseholdSlot(0));
        assertEquals(CitizenFamilyRole.DAD, CitizenFamilyRole.forHouseholdSlot(1));
        assertEquals(CitizenFamilyRole.CHILD, CitizenFamilyRole.forHouseholdSlot(2));
        assertEquals(CitizenFamilyRole.CHILD, CitizenFamilyRole.forHouseholdSlot(3));
    }

    @Test
    void parentRolesRequireTheirGenderWhileChildrenAllowEither() {
        assertEquals(CitizenGender.FEMALE, CitizenFamilyRole.MOM.requiredGender().orElseThrow());
        assertEquals(CitizenGender.MALE, CitizenFamilyRole.DAD.requiredGender().orElseThrow());
        assertTrue(CitizenFamilyRole.CHILD.requiredGender().isEmpty());
    }

    @Test
    void childrenBecomeAdultsAfterOneMinecraftDay() {
        assertEquals(CitizenLifeStage.CHILD, CitizenLifeStage.CHILD.afterTicks(23_999));
        assertEquals(CitizenLifeStage.ADULT, CitizenLifeStage.CHILD.afterTicks(24_000));
        assertEquals(CitizenLifeStage.ADULT, CitizenLifeStage.ADULT.afterTicks(1));
        assertFalse(CitizenLifeStage.CHILD.canWork());
        assertTrue(CitizenLifeStage.ADULT.canWork());
    }
}
