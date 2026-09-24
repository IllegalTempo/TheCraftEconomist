package com.jedts.theeconomist.citizen.trade;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CitizenTradeViewPayload(int entityId, long quoteVersion, int wheatPrice, int seedPrice,
                                      int hoePrice, int sellableWheat, long playerCrowns, long citizenCrowns,
                                      boolean wheatChangeAvailable,
                                      String farmStatus, String message) implements CustomPacketPayload {
    public static final Type<CitizenTradeViewPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/citizen_trade_view");
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenTradeViewPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId);
                buf.writeVarLong(value.quoteVersion);
                buf.writeVarInt(value.wheatPrice);
                buf.writeVarInt(value.seedPrice);
                buf.writeVarInt(value.hoePrice);
                buf.writeVarInt(value.sellableWheat);
                buf.writeVarLong(value.playerCrowns);
                buf.writeVarLong(value.citizenCrowns);
                buf.writeBoolean(value.wheatChangeAvailable);
                buf.writeUtf(value.farmStatus, 128);
                buf.writeUtf(value.message, 128);
            }, buf -> new CitizenTradeViewPayload(buf.readVarInt(), buf.readVarLong(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                    buf.readBoolean(), buf.readUtf(128), buf.readUtf(128)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
