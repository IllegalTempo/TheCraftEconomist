package com.jedts.theeconomist.contract;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenContractRegistryTest {
    private static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID WORKER = UUID.fromString("00000000-0000-0000-0000-000000000602");

    @Test
    void worker_accepts_the_highest_bounty_eligible_contract_once() {
        CitizenContractRegistry board = new CitizenContractRegistry();
        CitizenContract low = CitizenContract.service(UUID.randomUUID(), REQUESTER, "Small repair", 10, 1_000L, 20);
        CitizenContract high = CitizenContract.service(UUID.randomUUID(), REQUESTER, "Build house", 50, 1_000L, 30);
        board.publish(low);
        board.publish(high);

        Optional<CitizenContract> accepted = board.acceptBest(WORKER, 40, 100L);

        assertEquals(Optional.of(high), accepted);
        assertTrue(board.acceptBest(WORKER, 100, 100L).isEmpty());
        assertEquals(1, board.activeContracts().size());
    }

    @Test
    void expired_contracts_are_removed_from_available_work() {
        CitizenContractRegistry board = new CitizenContractRegistry();
        CitizenContract expired = CitizenContract.service(UUID.randomUUID(), REQUESTER, "Old work", 50, 100L, 10);
        board.publish(expired);

        assertTrue(board.acceptBest(WORKER, 100, 100L).isEmpty());
        assertTrue(board.openContracts().isEmpty());
    }
}
