package com.jedts.theeconomist.citizen.job;

import java.util.Objects;

public record CitizenJob(CitizenOccupation occupation, String employer, int wagePerDay, int startHour, int endHour) {
    public CitizenJob {
        Objects.requireNonNull(occupation, "occupation");
        Objects.requireNonNull(employer, "employer");
        if (wagePerDay < 0) throw new IllegalArgumentException("wagePerDay must not be negative");
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) {
            throw new IllegalArgumentException("work hours must be between 0 and 23");
        }
        if (occupation != CitizenOccupation.UNEMPLOYED) {
            if (employer.isBlank()) throw new IllegalArgumentException("employer must not be blank");
            if (startHour == endHour) throw new IllegalArgumentException("work schedule must have a duration");
        }
    }

    public static CitizenJob unemployed() {
        return new CitizenJob(CitizenOccupation.UNEMPLOYED, "", 0, 0, 0);
    }
}
