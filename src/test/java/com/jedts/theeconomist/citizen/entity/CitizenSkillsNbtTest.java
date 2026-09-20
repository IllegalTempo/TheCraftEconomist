package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.stats.CitizenSkill;
import com.jedts.theeconomist.citizen.stats.CitizenSkills;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenSkillsNbtTest {
    @Test
    void roundTrips_skill_values() {
        CitizenSkills skills = CitizenSkills.defaults().train(CitizenSkill.BUILDING, 44).train(CitizenSkill.TRADE, 9);
        assertEquals(skills, CitizenSkillsNbt.read(CitizenSkillsNbt.write(skills)));
    }
}
