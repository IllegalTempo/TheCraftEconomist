package com.jedts.theeconomist.citizen.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenJobTest {
    @Test
    void unemployed_defaults_are_safe_and_assignment_preserves_schedule() {
        CitizenJob job = CitizenJob.unemployed();
        assertEquals(CitizenOccupation.UNEMPLOYED, job.occupation());
        CitizenJob farmer = new CitizenJob(CitizenOccupation.FARMER, "Green Acres", 12, 8, 16);
        assertEquals("Green Acres", farmer.employer());
        assertEquals(12, farmer.wagePerDay());
        assertEquals(8, farmer.startHour());
        assertEquals(16, farmer.endHour());
    }
}
