package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.job.CitizenJob;
import com.jedts.theeconomist.citizen.job.CitizenOccupation;
import net.minecraft.nbt.CompoundTag;

public final class CitizenJobNbt {
    private static final String ROOT = "TheEconomistCitizenJob";

    private CitizenJobNbt() {
    }

    public static CompoundTag write(CitizenJob job) {
        CompoundTag root = new CompoundTag();
        root.putString("Occupation", job.occupation().name());
        root.putString("Employer", job.employer());
        root.putInt("WagePerDay", job.wagePerDay());
        root.putInt("StartHour", job.startHour());
        root.putInt("EndHour", job.endHour());
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(ROOT, root);
        return wrapper;
    }

    public static CitizenJob read(CompoundTag wrapper) {
        CompoundTag root = wrapper.getCompound(ROOT).orElse(new CompoundTag());
        CitizenOccupation occupation = root.getString("Occupation").map(CitizenOccupation::valueOf)
                .orElse(CitizenOccupation.UNEMPLOYED);
        return new CitizenJob(occupation, root.getStringOr("Employer", ""), root.getIntOr("WagePerDay", 0),
                root.getIntOr("StartHour", 0), root.getIntOr("EndHour", 0));
    }
}
