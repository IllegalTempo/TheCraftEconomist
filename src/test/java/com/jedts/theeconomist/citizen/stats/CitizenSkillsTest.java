package com.jedts.theeconomist.citizen.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenSkillsTest {
    @Test
    void skills_start_at_zero_and_training_is_bounded() {
        CitizenSkills skills = CitizenSkills.defaults().train(CitizenSkillRegistry.FARMING, 12)
                .train(CitizenSkillRegistry.MINING, 200);
        assertEquals(12, skills.farming());
        assertEquals(100, skills.mining());
        assertEquals(0, skills.building());
    }

    @Test
    void highest_value_discovers_all_registered_skills() {
        CitizenSkills skills = CitizenSkills.defaults().train(CitizenSkillRegistry.TRADE, 73);

        assertEquals(73, skills.highestValue());
    }
}
