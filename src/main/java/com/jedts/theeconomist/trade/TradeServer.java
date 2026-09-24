package com.jedts.theeconomist.trade;

import com.jedts.theeconomist.trade.domain.TradeRules;
import com.jedts.theeconomist.trade.request.TradeRequest;
import com.jedts.theeconomist.trade.request.TradeRequestResult;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All trade transitions run on the server thread. Offers are pledges against exact inventory stacks. */
public final class TradeServer {
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();

    private TradeServer() { }

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                    || !player.getMainHandItem().isEmpty() || !(entity instanceof Player)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer sender && entity instanceof ServerPlayer peer) begin(sender, peer);
            return InteractionResult.SUCCESS_SERVER;
        });
        ServerPlayNetworking.registerGlobalReceiver(TradeActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> action(context.player(), payload)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = BY_PLAYER.get(handler.player.getUUID());
            if (session != null) close(server, session, "Trade cancelled: player disconnected.");
        });
        ServerTickEvents.END_SERVER_TICK.register(TradeServer::tick);
    }

    private static void begin(ServerPlayer sender, ServerPlayer peer) {
        if (sender == peer || sender.isSpectator() || peer.isSpectator()
                || BY_PLAYER.containsKey(sender.getUUID()) || BY_PLAYER.containsKey(peer.getUUID())) return;
        if (!near(sender, peer)) return;
        Session session = new Session(sender.getUUID(), peer.getUUID(), sender.level().getGameTime());
        BY_PLAYER.put(session.first, session);
        BY_PLAYER.put(session.second, session);
        show(sender.level().getServer(), session, "Trade request: both players must accept.");
    }

    private static void action(ServerPlayer player, TradeActionPayload payload) {
        Session session = BY_PLAYER.get(player.getUUID());
        if (session == null || !session.id.equals(payload.id())) return;
        MinecraftServer server = player.level().getServer();
        ServerPlayer first = server.getPlayerList().getPlayer(session.first);
        ServerPlayer second = server.getPlayerList().getPlayer(session.second);
        if (first == null || second == null || !near(first, second)) {
            close(server, session, "Trade cancelled: players moved apart.");
            return;
        }
        boolean isFirst = player.getUUID().equals(session.first);
        if (payload.action() == TradeActionPayload.DECLINE || payload.action() == TradeActionPayload.CANCEL) {
            close(server, session, "Trade cancelled.");
            return;
        }
        if (session.stage == TradeViewPayload.REQUEST) {
            if (payload.action() != TradeActionPayload.ACCEPT) return;
            TradeRequestResult result = session.request.accept(player.getUUID(), player.level().getGameTime());
            if (result == TradeRequestResult.EXPIRED) {
                close(server, session, "Trade request expired.");
                return;
            }
            if (result == TradeRequestResult.READY_TO_OPEN) session.stage = TradeViewPayload.OFFER;
            show(server, session, "");
            return;
        }
        if (session.stage != TradeViewPayload.OFFER) return;
        LinkedHashMap<Integer, ItemStack> offers = isFirst ? session.firstOffers : session.secondOffers;
        if (payload.action() == TradeActionPayload.TOGGLE_SLOT) {
            int slot = payload.slot();
            if (slot < 0 || slot >= 36) return;
            if (offers.containsKey(slot)) offers.remove(slot);
            else {
                ItemStack item = player.getInventory().getItem(slot);
                if (item.isEmpty() || offers.size() >= TradeRules.OFFER_SLOTS) return;
                offers.put(slot, item.copy());
            }
            session.resetReady();
            show(server, session, "Offer changed; both players must ready again.");
        } else if (payload.action() == TradeActionPayload.READY) {
            if (!validOffers(player, offers)) {
                offers.clear();
                session.resetReady();
                show(server, session, "Inventory changed; select your offer again.");
                return;
            }
            if (isFirst) session.firstReady = !session.firstReady;
            else session.secondReady = !session.secondReady;
            session.countdown = TradeRules.COUNTDOWN_TICKS;
            show(server, session, "");
        }
    }

    private static void tick(MinecraftServer server) {
        for (Session session : List.copyOf(BY_PLAYER.values()).stream().distinct().toList()) {
            ServerPlayer first = server.getPlayerList().getPlayer(session.first);
            ServerPlayer second = server.getPlayerList().getPlayer(session.second);
            if (first == null || second == null || !first.isAlive() || !second.isAlive() || !near(first, second)) {
                close(server, session, "Trade cancelled: a player left, died, or moved away.");
                continue;
            }
            if (session.stage == TradeViewPayload.REQUEST) {
                if (session.request.expire(first.level().getGameTime()) == TradeRequestResult.EXPIRED) {
                    close(server, session, "Trade request expired.");
                }
                continue;
            }
            if (!validOffers(first, session.firstOffers) || !validOffers(second, session.secondOffers)) {
                session.firstOffers.clear();
                session.secondOffers.clear();
                session.resetReady();
                show(server, session, "Inventory changed; select offers again.");
                continue;
            }
            if (session.firstReady && session.secondReady) {
                if (--session.countdown <= 0) {
                    complete(server, session, first, second);
                } else if (session.countdown % 20 == 0) {
                    show(server, session, "");
                }
            }
        }
    }

    private static void complete(MinecraftServer server, Session session, ServerPlayer first, ServerPlayer second) {
        if (!near(first, second) || !validOffers(first, session.firstOffers)
                || !validOffers(second, session.secondOffers)) {
            close(server, session, "Trade cancelled: offer changed before completion.");
            return;
        }
        List<ItemStack> firstItems = take(first, session.firstOffers);
        List<ItemStack> secondItems = take(second, session.secondOffers);
        BY_PLAYER.remove(session.first);
        BY_PLAYER.remove(session.second);
        for (ItemStack item : firstItems) give(second, item);
        for (ItemStack item : secondItems) give(first, item);
        sendClosed(first, session, "Trade completed.");
        sendClosed(second, session, "Trade completed.");
    }

    private static List<ItemStack> take(ServerPlayer player, Map<Integer, ItemStack> offers) {
        List<ItemStack> result = new ArrayList<>(offers.size());
        for (var entry : offers.entrySet()) {
            result.add(player.getInventory().removeItem(entry.getKey(), entry.getValue().getCount()));
        }
        return result;
    }

    private static void give(ServerPlayer player, ItemStack item) {
        if (item.isEmpty()) return;
        player.getInventory().add(item);
        if (!item.isEmpty()) player.drop(item, false, Prediction.SERVER_ONLY);
    }

    private static boolean validOffers(ServerPlayer player, Map<Integer, ItemStack> offers) {
        for (var entry : offers.entrySet()) {
            if (!ItemStack.matches(entry.getValue(), player.getInventory().getItem(entry.getKey()))) return false;
        }
        return true;
    }

    private static boolean near(ServerPlayer a, ServerPlayer b) {
        return a.level() == b.level() && a.distanceToSqr(b) <= TradeRules.MAX_DISTANCE_SQUARED;
    }

    private static void show(MinecraftServer server, Session session, String message) {
        ServerPlayer first = server.getPlayerList().getPlayer(session.first);
        ServerPlayer second = server.getPlayerList().getPlayer(session.second);
        if (first == null || second == null) return;
        send(first, second, session, true, message);
        send(second, first, session, false, message);
    }

    private static void send(ServerPlayer player, ServerPlayer peer, Session session, boolean isFirst, String message) {
        ServerPlayNetworking.send(player, new TradeViewPayload(session.id, session.stage, peer.getName().getString(),
                session.request.accepted(player.getUUID()),
                session.request.accepted(peer.getUUID()),
                isFirst ? session.firstReady : session.secondReady,
                isFirst ? session.secondReady : session.firstReady,
                session.countdown,
                copies(isFirst ? session.firstOffers : session.secondOffers),
                copies(isFirst ? session.secondOffers : session.firstOffers), message));
    }

    private static List<ItemStack> copies(Map<Integer, ItemStack> offers) {
        return offers.values().stream().map(ItemStack::copy).toList();
    }

    private static void close(MinecraftServer server, Session session, String reason) {
        if (BY_PLAYER.get(session.first) != session) return;
        BY_PLAYER.remove(session.first);
        BY_PLAYER.remove(session.second);
        ServerPlayer first = server.getPlayerList().getPlayer(session.first);
        ServerPlayer second = server.getPlayerList().getPlayer(session.second);
        if (first != null) sendClosed(first, session, reason);
        if (second != null) sendClosed(second, session, reason);
    }

    private static void sendClosed(ServerPlayer player, Session session, String message) {
        if (!message.isBlank()) player.sendSystemMessage(Component.literal(message));
        ServerPlayNetworking.send(player, new TradeViewPayload(session.id, TradeViewPayload.CLOSED, "",
                false, false, false, false, 0, List.of(), List.of(), message));
    }

    private static final class Session {
        private final UUID id = UUID.randomUUID();
        private final UUID first;
        private final UUID second;
        private final TradeRequest request;
        private final LinkedHashMap<Integer, ItemStack> firstOffers = new LinkedHashMap<>();
        private final LinkedHashMap<Integer, ItemStack> secondOffers = new LinkedHashMap<>();
        private int stage = TradeViewPayload.REQUEST;
        private boolean firstReady;
        private boolean secondReady;
        private int countdown = TradeRules.COUNTDOWN_TICKS;

        private Session(UUID first, UUID second, long createdTick) {
            this.first = first;
            this.second = second;
            this.request = TradeRequest.create(id, first, second, createdTick);
        }

        private void resetReady() {
            firstReady = false;
            secondReady = false;
            countdown = TradeRules.COUNTDOWN_TICKS;
        }
    }
}
