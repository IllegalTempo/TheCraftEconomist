package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.stats.CitizenSkills;
import net.minecraft.nbt.CompoundTag;

public final class CitizenSkillsNbt {
    private static final String ROOT = "TheEconomistCitizenSkills";

    private CitizenSkillsNbt() {
    }

    public static CompoundTag write(CitizenSkills skills) {
        CompoundTag root = new CompoundTag();
        root.putInt("Farming", skills.farming());
        root.putInt("Mining", skills.mining());
        root.putInt("Building", skills.building());
        root.putInt("Combat", skills.combat());
        root.putInt("Trade", skills.trade());
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(ROOT, root);
        return wrapper;
    }

    public static CitizenSkills read(CompoundTag wrapper) {
        CompoundTag root = wrapper.getCompound(ROOT).orElse(new CompoundTag());
        CitizenSkills defaults = CitizenSkills.defaults();
        return new CitizenSkills(root.getInt("Farming").orElse(defaults.farming()),
                root.getInt("Mining").orElse(defaults.mining()), root.getInt("Building").orElse(defaults.building()),
                root.getInt("Combat").orElse(defaults.combat()), root.getInt("Trade").orElse(defaults.trade()));
    }
}
