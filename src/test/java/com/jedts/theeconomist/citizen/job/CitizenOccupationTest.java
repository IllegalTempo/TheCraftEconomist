package com.jedts.theeconomist.citizen.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenOccupationTest {
    @Test
    void contract_targets_map_to_work_roles() {
        assertEquals(CitizenOccupation.FARMER, CitizenOccupation.fromContractTarget("farm wheat"));
        assertEquals(CitizenOccupation.MINER, CitizenOccupation.fromContractTarget("mine stone"));
        assertEquals(CitizenOccupation.BUILDER, CitizenOccupation.fromContractTarget("build house"));
        assertEquals(CitizenOccupation.SOLDIER, CitizenOccupation.fromContractTarget("guard road"));
        assertEquals(CitizenOccupation.TRADER, CitizenOccupation.fromContractTarget("deliver goods"));
    }
}
