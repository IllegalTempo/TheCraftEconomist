package com.jedts.theeconomist.trade;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client intent only; the server owns every request, offer, and transfer. */
public record TradeActionPayload(UUID id, int action, int slot) implements CustomPacketPayload {
    public static final int ACCEPT = 0;
    public static final int DECLINE = 1;
    public static final int TOGGLE_SLOT = 2;
    public static final int READY = 3;
    public static final int CANCEL = 4;
    public static final Type<TradeActionPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/trade_action");
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.id);
                buf.writeVarInt(payload.action);
                buf.writeVarInt(payload.slot);
            }, buf -> new TradeActionPayload(buf.readUUID(), buf.readVarInt(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
