package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Status: 0 cleared/rejected, 1 first corner selected, 2 capture succeeded. */
public record BlueprintCaptureFeedbackPayload(int status, BlockPos firstCorner, String reason)
        implements CustomPacketPayload {
    public static final Type<BlueprintCaptureFeedbackPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/blueprint_capture_feedback");
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCaptureFeedbackPayload> CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.status());
                buf.writeBlockPos(payload.firstCorner());
                buf.writeUtf(payload.reason(), 256);
            }, buf -> new BlueprintCaptureFeedbackPayload(buf.readVarInt(), buf.readBlockPos(), buf.readUtf(256)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
