package com.jedts.theeconomist.citizen.trade;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record CitizenTradeActionPayload(int entityId, int action, int quantity, long quoteVersion,
                                        UUID requestId) implements CustomPacketPayload {
    public static final Type<CitizenTradeActionPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/citizen_trade_action");
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenTradeActionPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId);
                buf.writeVarInt(value.action);
                buf.writeVarInt(value.quantity);
                buf.writeVarLong(value.quoteVersion);
                buf.writeUUID(value.requestId);
            }, buf -> new CitizenTradeActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarLong(), buf.readUUID()));

    public CitizenTradeAction parsedAction() {
        return action >= 0 && action < CitizenTradeAction.values().length
                ? CitizenTradeAction.values()[action] : null;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
