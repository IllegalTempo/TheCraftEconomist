package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.stats.CitizenSkills;
import com.jedts.theeconomist.citizen.stats.CitizenSkill;
import com.jedts.theeconomist.citizen.stats.CitizenSkillRegistry;
import net.minecraft.nbt.CompoundTag;

public final class CitizenSkillsNbt {
    private static final String ROOT = "TheEconomistCitizenSkills";

    private CitizenSkillsNbt() {
    }

    public static CompoundTag write(CitizenSkills skills) {
        CompoundTag root = new CompoundTag();
        for (CitizenSkill skill : CitizenSkillRegistry.all()) {
            root.putInt(skill.id(), skills.value(skill));
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(ROOT, root);
        return wrapper;
    }

    public static CitizenSkills read(CompoundTag wrapper) {
        CompoundTag root = wrapper.getCompound(ROOT).orElse(new CompoundTag());
        var values = new java.util.LinkedHashMap<CitizenSkill, Integer>();
        for (CitizenSkill skill : CitizenSkillRegistry.all()) {
            int value = root.getInt(skill.id()).orElse(root.getInt(skill.legacyKey()).orElse(0));
            values.put(skill, value);
        }
        return CitizenSkills.fromValues(values);
    }
}
