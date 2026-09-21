package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.stats.CitizenStats;
import com.jedts.theeconomist.citizen.stats.CitizenStat;
import com.jedts.theeconomist.citizen.stats.CitizenStatRegistry;
import net.minecraft.nbt.CompoundTag;

public final class CitizenStatsNbt {
    private static final String ROOT = "TheEconomistCitizenStats";

    private CitizenStatsNbt() {
    }

    public static CompoundTag write(CitizenStats stats) {
        CompoundTag root = new CompoundTag();
        for (CitizenStat stat : CitizenStatRegistry.all()) root.putInt(stat.id(), stats.value(stat));
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(ROOT, root);
        return wrapper;
    }

    public static CitizenStats read(CompoundTag wrapper) {
        CompoundTag root = wrapper.getCompound(ROOT).orElse(new CompoundTag());
        var values = new java.util.LinkedHashMap<CitizenStat, Integer>();
        for (CitizenStat stat : CitizenStatRegistry.all()) {
            values.put(stat, root.getInt(stat.id()).orElse(root.getInt(stat.legacyKey()).orElse(stat.defaultValue())));
        }
        return CitizenStats.fromValues(values);
    }
}
