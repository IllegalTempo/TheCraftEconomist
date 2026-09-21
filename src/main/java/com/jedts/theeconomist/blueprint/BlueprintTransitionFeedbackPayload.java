package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BlueprintTransitionFeedbackPayload(int requestId, boolean placement, boolean accepted, String reason)
        implements CustomPacketPayload {
    public static final Type<BlueprintTransitionFeedbackPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/blueprint_transition_feedback");
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintTransitionFeedbackPayload> CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.requestId());
                buf.writeBoolean(payload.placement());
                buf.writeBoolean(payload.accepted());
                buf.writeUtf(payload.reason(), 256);
            }, buf -> new BlueprintTransitionFeedbackPayload(buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean(), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
