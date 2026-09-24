package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.stats.CitizenSkills;
import com.jedts.theeconomist.citizen.stats.CitizenSkillRegistry;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenSkillsNbtTest {
    @Test
    void roundTrips_skill_values() {
        CitizenSkills skills = CitizenSkills.defaults().train(CitizenSkillRegistry.BUILDING, 44)
                .train(CitizenSkillRegistry.TRADE, 9);
        assertEquals(skills, CitizenSkillsNbt.read(CitizenSkillsNbt.write(skills)));
    }

    @Test
    void reads_legacy_skill_keys() {
        CompoundTag root = new CompoundTag();
        root.putInt("Farming", 31);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("TheEconomistCitizenSkills", root);

        assertEquals(31, CitizenSkillsNbt.read(wrapper).farming());
    }
}
