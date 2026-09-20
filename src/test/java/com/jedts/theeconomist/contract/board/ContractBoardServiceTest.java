package com.jedts.theeconomist.contract.board;

import com.jedts.theeconomist.contract.CitizenContract;
import com.jedts.theeconomist.contract.CitizenContractRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractBoardServiceTest {
    @Test
    void snapshot_contains_only_open_contract_projections() {
        CitizenContractRegistry registry = new CitizenContractRegistry();
        CitizenContract open = CitizenContract.service(UUID.randomUUID(), UUID.randomUUID(), "farm", 40, 500, 15);
        CitizenContract accepted = CitizenContract.service(UUID.randomUUID(), UUID.randomUUID(), "mine", 20, 500, 5);
        registry.publish(open);
        registry.publish(accepted);
        accepted.accept(UUID.randomUUID(), 10, 20);

        ContractBoardPayload snapshot = ContractBoardService.snapshot(registry, 20);

        assertEquals(1, snapshot.contracts().size());
        assertEquals(open.id(), snapshot.contracts().getFirst().id());
        assertEquals(40, snapshot.contracts().getFirst().bounty());
    }

    @Test
    void board_entry_rejects_invalid_terms() {
        assertThrows(IllegalArgumentException.class, () -> new ContractBoardEntry(
                UUID.randomUUID(), com.jedts.theeconomist.contract.ContractKind.SERVICE, "", 1, 1, 0, 1));
    }
}
