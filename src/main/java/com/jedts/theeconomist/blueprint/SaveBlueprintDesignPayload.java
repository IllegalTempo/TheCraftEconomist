package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SaveBlueprintDesignPayload(BlueprintDesign design) implements CustomPacketPayload {
    public static final Type<SaveBlueprintDesignPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/save_blueprint_design");
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveBlueprintDesignPayload> CODEC =
            StreamCodec.of((buf, payload) -> BlueprintPayloadCodec.writeDesign(buf, payload.design()),
                    buf -> new SaveBlueprintDesignPayload(BlueprintPayloadCodec.readDesign(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
