package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.stats.CitizenStats;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenStatsNbtTest {
    @Test
    void roundTripsAllPersistentStats() {
        CitizenStats stats = new CitizenStats(91, 82, 73, 64, 55, 46, 37, 28, 19, 10, 1, 99);
        CitizenStats restored = CitizenStatsNbt.read(CitizenStatsNbt.write(stats));
        assertEquals(stats, restored);
    }

    @Test
    void reads_legacy_stat_keys() {
        CompoundTag root = new CompoundTag();
        root.putInt("Hunger", 31);
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("TheEconomistCitizenStats", root);

        assertEquals(31, CitizenStatsNbt.read(wrapper).hunger());
    }
}
