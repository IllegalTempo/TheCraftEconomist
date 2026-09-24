package com.jedts.theeconomist.contract;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenContractTest {
    private static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID WORKER = UUID.fromString("00000000-0000-0000-0000-000000000502");

    @Test
    void qualified_citizen_accepts_and_reserves_bounty_until_completion() {
        CitizenContract contract = CitizenContract.service(UUID.randomUUID(), REQUESTER, "Build house", 25, 1_000L, 40);

        assertEquals(ContractResult.ACCEPTED, contract.accept(WORKER, 50, 100L));
        assertEquals(ContractStatus.ACCEPTED, contract.status());
        assertTrue(contract.bountyReserved());
        assertEquals(WORKER, contract.workerId().orElseThrow());
        assertEquals(ContractResult.STARTED, contract.start(WORKER));
        assertEquals(ContractResult.COMPLETED, contract.complete(WORKER));
        assertEquals(ContractStatus.COMPLETED, contract.status());
        assertFalse(contract.bountyReserved());
        assertEquals(25, contract.payout());
    }

    @Test
    void underqualified_workers_and_expired_contracts_cannot_accept() {
        CitizenContract contract = CitizenContract.resource(UUID.randomUUID(), REQUESTER, "stone", 32, 60, 1_000L, 20);

        assertEquals(ContractResult.INSUFFICIENT_SKILL, contract.accept(WORKER, 19, 100L));
        assertEquals(ContractResult.EXPIRED, contract.accept(WORKER, 20, 1_000L));
        assertEquals(ContractStatus.FAILED, contract.status());
        assertFalse(contract.bountyReserved());
    }
}
