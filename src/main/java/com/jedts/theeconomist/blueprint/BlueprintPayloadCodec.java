package com.jedts.theeconomist.blueprint;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintPayloadCodec {
    private BlueprintPayloadCodec() { }

    public static void writeDesign(RegistryFriendlyByteBuf buf, BlueprintDesign design) {
        buf.writeVarInt(design.width());
        buf.writeVarInt(design.height());
        buf.writeVarInt(design.depth());
        buf.writeVarInt(design.blocks().size());
        for (BlueprintBlock block : design.blocks()) {
            buf.writeVarInt(block.x());
            buf.writeVarInt(block.y());
            buf.writeVarInt(block.z());
            buf.writeUtf(block.blockId(), 256);
            buf.writeUtf(block.stateProperties(), 256);
        }
    }

    public static BlueprintDesign readDesign(RegistryFriendlyByteBuf buf) {
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int depth = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > BlueprintLimits.MAX_BLOCKS) {
            throw new IllegalArgumentException("invalid blueprint block count");
        }
        List<BlueprintBlock> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            blocks.add(new BlueprintBlock(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(256), buf.readUtf(256)));
        }
        return new BlueprintDesign(width, height, depth, blocks);
    }

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
