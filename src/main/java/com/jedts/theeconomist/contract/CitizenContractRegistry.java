package com.jedts.theeconomist.contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class CitizenContractRegistry {
    private final Map<UUID, CitizenContract> contracts = new HashMap<>();
    private final Map<UUID, UUID> activeByWorker = new HashMap<>();
    private CitizenContractSavedData savedData;

    public synchronized void load(ServerLevel level) {
        savedData = level.getDataStorage().computeIfAbsent(CitizenContractSavedData.TYPE);
        contracts.clear();
        activeByWorker.clear();
        for (CitizenContractSavedData.PersistedContract saved : savedData.contracts()) {
            CitizenContract contract = CitizenContract.restore(saved.id(), saved.requester(), saved.kind(), saved.target(),
                    saved.quantity(), saved.bounty(), saved.deadline(), saved.requiredSkill(), saved.worker().orElse(null),
                    saved.status(), saved.bountyReserved());
            contracts.put(contract.id(), contract);
            contract.workerId().ifPresent(worker -> activeByWorker.put(worker, contract.id()));
        }
    }

    public synchronized void publish(CitizenContract contract) {
        Objects.requireNonNull(contract, "contract");
        if (contracts.putIfAbsent(contract.id(), contract) != null) {
            throw new IllegalArgumentException("contract id already exists: " + contract.id());
        }
        save();
    }

    public synchronized Optional<CitizenContract> acceptBest(UUID worker, int skill, long nowTick) {
        Objects.requireNonNull(worker, "worker");
        if (activeByWorker.containsKey(worker)) return Optional.empty();
        cleanupExpired(nowTick);
        return contracts.values().stream()
                .filter(contract -> contract.status() == ContractStatus.OPEN)
                .filter(contract -> contract.requiredSkill() <= skill)
                .sorted(Comparator.comparingInt(CitizenContract::bounty).reversed()
                        .thenComparingLong(CitizenContract::deadlineTick))
                .findFirst()
                .filter(contract -> contract.accept(worker, skill, nowTick) == ContractResult.ACCEPTED)
                .map(contract -> {
                    activeByWorker.put(worker, contract.id());
                    save();
                    return contract;
                });
    }

    private void save() {
        if (savedData != null) {
            savedData.replace(contracts.values().stream().map(CitizenContractSavedData::from).toList());
        }
    }

    public synchronized List<CitizenContract> openContracts() {
        return contracts.values().stream().filter(contract -> contract.status() == ContractStatus.OPEN).toList();
    }

    public synchronized List<CitizenContract> activeContracts() {
        return new ArrayList<>(contracts.values().stream()
                .filter(contract -> contract.status() == ContractStatus.ACCEPTED || contract.status() == ContractStatus.IN_PROGRESS)
                .toList());
    }

    public synchronized Optional<CitizenContract> activeContractFor(UUID worker) {
        UUID contractId = activeByWorker.get(worker);
        if (contractId == null) return Optional.empty();
        CitizenContract contract = contracts.get(contractId);
        return contract == null ? Optional.empty() : Optional.of(contract);
    }

    private void cleanupExpired(long nowTick) {
        for (CitizenContract contract : contracts.values()) {
            if (contract.status() == ContractStatus.OPEN && nowTick >= contract.deadlineTick()) {
                contract.accept(UUID.randomUUID(), 100, nowTick);
            }
        }
        contracts.entrySet().removeIf(entry -> entry.getValue().status() == ContractStatus.FAILED
                || entry.getValue().status() == ContractStatus.CANCELLED);
    }
}
