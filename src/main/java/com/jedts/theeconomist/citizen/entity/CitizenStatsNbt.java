package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.stats.CitizenStats;
import net.minecraft.nbt.CompoundTag;

public final class CitizenStatsNbt {
    private static final String ROOT = "TheEconomistCitizenStats";

    private CitizenStatsNbt() {
    }

    public static CompoundTag write(CitizenStats stats) {
        CompoundTag root = new CompoundTag();
        root.putInt("Hunger", stats.hunger());
        root.putInt("Energy", stats.energy());
        root.putInt("Safety", stats.safety());
        root.putInt("Morale", stats.morale());
        root.putInt("Intelligence", stats.intelligence());
        root.putInt("Anger", stats.anger());
        root.putInt("Education", stats.education());
        root.putInt("Ambition", stats.ambition());
        root.putInt("Thrift", stats.thrift());
        root.putInt("Bravery", stats.bravery());
        root.putInt("Sociability", stats.sociability());
        root.putInt("Loyalty", stats.loyalty());
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(ROOT, root);
        return wrapper;
    }

    public static CitizenStats read(CompoundTag wrapper) {
        CompoundTag root = wrapper.getCompound(ROOT).orElse(new CompoundTag());
        CitizenStats defaults = CitizenStats.defaults();
        return new CitizenStats(
                value(root, "Hunger", defaults.hunger()), value(root, "Energy", defaults.energy()),
                value(root, "Safety", defaults.safety()), value(root, "Morale", defaults.morale()),
                value(root, "Intelligence", defaults.intelligence()), value(root, "Anger", defaults.anger()),
                value(root, "Education", defaults.education()), value(root, "Ambition", defaults.ambition()),
                value(root, "Thrift", defaults.thrift()), value(root, "Bravery", defaults.bravery()),
                value(root, "Sociability", defaults.sociability()), value(root, "Loyalty", defaults.loyalty()));
    }

    private static int value(CompoundTag root, String key, int fallback) {
        return root.getInt(key).orElse(fallback);
    }
}
