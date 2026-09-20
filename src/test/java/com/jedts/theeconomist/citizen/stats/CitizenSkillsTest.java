package com.jedts.theeconomist.citizen.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenSkillsTest {
    @Test
    void skills_start_at_zero_and_training_is_bounded() {
        CitizenSkills skills = CitizenSkills.defaults().train(CitizenSkill.FARMING, 12).train(CitizenSkill.MINING, 200);
        assertEquals(12, skills.farming());
        assertEquals(100, skills.mining());
        assertEquals(0, skills.building());
    }
}
