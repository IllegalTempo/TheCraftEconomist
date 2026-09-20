package com.jedts.theeconomist.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.UUID;

/** Codec-backed world storage for the contract registry. */
final class CitizenContractSavedData extends SavedData {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<ContractKind> KIND_CODEC = Codec.STRING.xmap(ContractKind::valueOf, Enum::name);
    private static final Codec<ContractStatus> STATUS_CODEC = Codec.STRING.xmap(ContractStatus::valueOf, Enum::name);
    private static final Codec<PersistedContract> CONTRACT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("id").forGetter(PersistedContract::id),
            UUID_CODEC.fieldOf("requester").forGetter(PersistedContract::requester),
            KIND_CODEC.fieldOf("kind").forGetter(PersistedContract::kind),
            Codec.STRING.fieldOf("target").forGetter(PersistedContract::target),
            Codec.INT.fieldOf("quantity").forGetter(PersistedContract::quantity),
            Codec.INT.fieldOf("bounty").forGetter(PersistedContract::bounty),
            Codec.LONG.fieldOf("deadline").forGetter(PersistedContract::deadline),
            Codec.INT.fieldOf("requiredSkill").forGetter(PersistedContract::requiredSkill),
            UUID_CODEC.optionalFieldOf("worker").forGetter(PersistedContract::worker),
            STATUS_CODEC.fieldOf("status").forGetter(PersistedContract::status),
            Codec.BOOL.fieldOf("bountyReserved").forGetter(PersistedContract::bountyReserved)
    ).apply(instance, PersistedContract::new));
    private static final Codec<CitizenContractSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CONTRACT_CODEC.listOf().fieldOf("contracts").forGetter(data -> data.contracts)
    ).apply(instance, CitizenContractSavedData::new));
    static final SavedDataType<CitizenContractSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("theeconomist", "contracts"), CitizenContractSavedData::new,
            CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private List<PersistedContract> contracts;

    CitizenContractSavedData() {
        this(List.of());
    }

    CitizenContractSavedData(List<PersistedContract> contracts) {
        this.contracts = List.copyOf(contracts);
    }

    List<PersistedContract> contracts() {
        return contracts;
    }

    void replace(List<PersistedContract> contracts) {
        this.contracts = List.copyOf(contracts);
        setDirty();
    }

    record PersistedContract(UUID id, UUID requester, ContractKind kind, String target, int quantity, int bounty,
                             long deadline, int requiredSkill, java.util.Optional<UUID> worker,
                             ContractStatus status, boolean bountyReserved) {
    }

    static PersistedContract from(CitizenContract contract) {
        return new PersistedContract(contract.id(), contract.requesterId(), contract.kind(), contract.target(),
                contract.quantity(), contract.bounty(), contract.deadlineTick(), contract.requiredSkill(),
                contract.workerId(), contract.status(), contract.bountyReserved());
    }
}
