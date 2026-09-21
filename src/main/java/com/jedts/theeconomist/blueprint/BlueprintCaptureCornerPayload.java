package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BlueprintCaptureCornerPayload(BlockPos corner, boolean cancel) implements CustomPacketPayload {
    public static final Type<BlueprintCaptureCornerPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/blueprint_capture_corner");
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCaptureCornerPayload> CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeBlockPos(payload.corner());
                buf.writeBoolean(payload.cancel());
            }, buf -> new BlueprintCaptureCornerPayload(buf.readBlockPos(), buf.readBoolean()));

    public static BlueprintCaptureCornerPayload cancelSelection() {
        return new BlueprintCaptureCornerPayload(BlockPos.ZERO, true);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
