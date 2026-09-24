package com.jedts.theeconomist.trade;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A bounded, server-authored view of one player's trade. */
public record TradeViewPayload(UUID id, int stage, String peerName, boolean selfAccepted,
                               boolean peerAccepted, boolean selfReady, boolean peerReady,
                               int countdown, List<ItemStack> ownOffers, List<ItemStack> peerOffers,
                               String message) implements CustomPacketPayload {
    public static final int REQUEST = 0;
    public static final int OFFER = 1;
    public static final int CLOSED = 2;
    public static final Type<TradeViewPayload> TYPE =
            CustomPacketPayload.createType(TheEconomistMod.MOD_ID + "/trade_view");
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeViewPayload> CODEC =
            StreamCodec.of(TradeViewPayload::write, TradeViewPayload::read);

    public TradeViewPayload {
        ownOffers = List.copyOf(ownOffers);
        peerOffers = List.copyOf(peerOffers);
    }

    private static void write(RegistryFriendlyByteBuf buf, TradeViewPayload view) {
        buf.writeUUID(view.id);
        buf.writeVarInt(view.stage);
        buf.writeUtf(view.peerName, 64);
        buf.writeBoolean(view.selfAccepted);
        buf.writeBoolean(view.peerAccepted);
        buf.writeBoolean(view.selfReady);
        buf.writeBoolean(view.peerReady);
        buf.writeVarInt(view.countdown);
        writeItems(buf, view.ownOffers);
        writeItems(buf, view.peerOffers);
        buf.writeUtf(view.message, 160);
    }

    private static TradeViewPayload read(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int stage = buf.readVarInt();
        String peer = buf.readUtf(64);
        boolean selfAccepted = buf.readBoolean();
        boolean peerAccepted = buf.readBoolean();
        boolean selfReady = buf.readBoolean();
        boolean peerReady = buf.readBoolean();
        int countdown = buf.readVarInt();
        List<ItemStack> own = readItems(buf);
        List<ItemStack> other = readItems(buf);
        return new TradeViewPayload(id, stage, peer, selfAccepted, peerAccepted,
                selfReady, peerReady, countdown, own, other, buf.readUtf(160));
    }

    private static void writeItems(RegistryFriendlyByteBuf buf, List<ItemStack> items) {
        buf.writeVarInt(items.size());
        for (ItemStack item : items) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
    }

    private static List<ItemStack> readItems(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 18) throw new IllegalArgumentException("invalid trade offer size");
        List<ItemStack> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        return items;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
