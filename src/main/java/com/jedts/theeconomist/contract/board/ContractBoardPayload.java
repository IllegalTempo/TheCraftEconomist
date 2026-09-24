package com.jedts.theeconomist.contract.board;

import com.jedts.theeconomist.TheEconomistMod;
import com.jedts.theeconomist.contract.ContractKind;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-authoritative snapshot sent when a player opens the contract board. */
public record ContractBoardPayload(long currentTick, List<ContractBoardEntry> contracts)
        implements CustomPacketPayload {
    public static final Type<ContractBoardPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/contract_board");
    public static final StreamCodec<RegistryFriendlyByteBuf, ContractBoardPayload> CODEC =
            StreamCodec.of(ContractBoardPayload::write, ContractBoardPayload::read);

    public ContractBoardPayload {
        if (currentTick < 0) throw new IllegalArgumentException("current tick must not be negative");
        contracts = List.copyOf(contracts);
    }

    public static ContractBoardPayload from(long currentTick, List<ContractBoardEntry> entries) {
        return new ContractBoardPayload(currentTick, entries);
    }

    private static void write(RegistryFriendlyByteBuf buf, ContractBoardPayload payload) {
        buf.writeVarLong(payload.currentTick);
        buf.writeVarInt(payload.contracts.size());
        for (ContractBoardEntry entry : payload.contracts) {
            buf.writeLong(entry.id().getMostSignificantBits());
            buf.writeLong(entry.id().getLeastSignificantBits());
            buf.writeVarInt(entry.kind().ordinal());
            buf.writeUtf(entry.target(), 256);
            buf.writeVarInt(entry.quantity());
            buf.writeVarInt(entry.bounty());
            buf.writeVarInt(entry.requiredSkill());
            buf.writeVarLong(entry.deadlineTick());
        }
    }

    private static ContractBoardPayload read(RegistryFriendlyByteBuf buf) {
        long currentTick = buf.readVarLong();
        int count = buf.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("invalid contract count");
        List<ContractBoardEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            ContractKind kind = ContractKind.values()[buf.readVarInt()];
            entries.add(new ContractBoardEntry(id, kind, buf.readUtf(256), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarLong()));
        }
        return new ContractBoardPayload(currentTick, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
