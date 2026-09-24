package com.jedts.theeconomist.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;


public final class BlueprintPayloadCodec {
    private BlueprintPayloadCodec() { }

    public static void writePlacement(RegistryFriendlyByteBuf buf, BlueprintPlacement placement) {
        buf.writeUtf(placement.dimension(), 128);
        buf.writeBlockPos(placement.origin());
        buf.writeVarInt(placement.rotation());
        buf.writeBoolean(placement.mirrorX());
        buf.writeBoolean(placement.mirrorZ());
    }

    public static BlueprintPlacement readPlacement(RegistryFriendlyByteBuf buf) {
        return new BlueprintPlacement(buf.readUtf(128), buf.readBlockPos(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean());
    }
}
