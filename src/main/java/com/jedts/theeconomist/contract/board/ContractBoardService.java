package com.jedts.theeconomist.contract.board;

import com.jedts.theeconomist.contract.CitizenContract;
import com.jedts.theeconomist.contract.CitizenContractRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Builds and sends contract-board snapshots without exposing mutable server contracts. */
public final class ContractBoardService {
    private ContractBoardService() {
    }

    public static ContractBoardPayload snapshot(CitizenContractRegistry registry, long currentTick) {
        List<ContractBoardEntry> entries = registry.openContracts().stream()
                .map(contract -> toEntry(contract))
                .toList();
        return ContractBoardPayload.from(currentTick, entries);
    }

    public static void send(ServerPlayer player, CitizenContractRegistry registry) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                snapshot(registry, player.level().getGameTime()));
    }

    private static ContractBoardEntry toEntry(CitizenContract contract) {
        return new ContractBoardEntry(contract.id(), contract.kind(), contract.target(), contract.quantity(),
                contract.bounty(), contract.requiredSkill(), contract.deadlineTick());
    }
}
