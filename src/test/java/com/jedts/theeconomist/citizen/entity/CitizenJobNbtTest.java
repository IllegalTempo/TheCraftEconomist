package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.job.CitizenJob;
import com.jedts.theeconomist.citizen.job.CitizenOccupation;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenJobNbtTest {
    @Test
    void roundTrips_job_assignment() {
        CitizenJob job = new CitizenJob(CitizenOccupation.MINER, "Deep Rock", 20, 7, 15);
        assertEquals(job, CitizenJobNbt.read(CitizenJobNbt.write(job)));
    }
}
