package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ConfirmBlueprintPlacementPayload(int requestId, String expectedDesignFingerprint, BlueprintPlacement placement)
        implements CustomPacketPayload {
    public static final Type<ConfirmBlueprintPlacementPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/confirm_blueprint_placement");
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmBlueprintPlacementPayload> CODEC =
            StreamCodec.of((buf, payload) -> {
                buf.writeVarInt(payload.requestId());
                buf.writeUtf(payload.expectedDesignFingerprint(), 64);
                BlueprintPayloadCodec.writePlacement(buf, payload.placement());
            }, buf -> new ConfirmBlueprintPlacementPayload(buf.readVarInt(), buf.readUtf(64),
                    BlueprintPayloadCodec.readPlacement(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
