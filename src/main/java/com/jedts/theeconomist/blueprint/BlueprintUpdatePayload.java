package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Client proposal for changing a held blueprint; the server always revalidates it. */
public record BlueprintUpdatePayload(boolean placement, BlueprintDesign design, BlueprintPlacement target)
        implements CustomPacketPayload {
    public static final Type<BlueprintUpdatePayload> TYPE = CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/blueprint_update");
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintUpdatePayload> CODEC =
            StreamCodec.of(BlueprintUpdatePayload::write, BlueprintUpdatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, BlueprintUpdatePayload payload) {
        buf.writeBoolean(payload.placement);
        BlueprintDesign design = payload.design;
        buf.writeVarInt(design.width()); buf.writeVarInt(design.height()); buf.writeVarInt(design.depth());
        buf.writeVarInt(design.blocks().size());
        for (BlueprintBlock block : design.blocks()) {
            buf.writeVarInt(block.x()); buf.writeVarInt(block.y()); buf.writeVarInt(block.z()); buf.writeUtf(block.blockId(), 256); buf.writeUtf(block.stateProperties(), 256);
        }
        if (payload.placement) {
            BlueprintPlacement target = payload.target;
            buf.writeUtf(target.dimension(), 128); buf.writeInt(target.origin().getX()); buf.writeInt(target.origin().getY()); buf.writeInt(target.origin().getZ());
            buf.writeVarInt(target.rotation()); buf.writeBoolean(target.mirrorX()); buf.writeBoolean(target.mirrorZ());
        }
    }

    private static BlueprintUpdatePayload read(RegistryFriendlyByteBuf buf) {
        boolean placement = buf.readBoolean();
        int width = buf.readVarInt(), height = buf.readVarInt(), depth = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > BlueprintLimits.MAX_BLOCKS) throw new IllegalArgumentException("invalid blueprint block count");
        List<BlueprintBlock> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(new BlueprintBlock(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256), buf.readUtf(256)));
        }
        BlueprintPlacement target = null;
        if (placement) {
            target = new BlueprintPlacement(buf.readUtf(128), new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }
        return new BlueprintUpdatePayload(placement, new BlueprintDesign(width, height, depth, blocks), target);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
