package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CitizenInfoRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<CitizenInfoRequestPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/citizen_info_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenInfoRequestPayload> CODEC =
            StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.entityId),
                    buf -> new CitizenInfoRequestPayload(buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
