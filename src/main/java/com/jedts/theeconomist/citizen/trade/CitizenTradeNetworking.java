package com.jedts.theeconomist.citizen.trade;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Validates the open screen association before any trade request. */
public final class CitizenTradeNetworking {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private CitizenTradeNetworking() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CitizenTradeActionPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handle(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(server -> SESSIONS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) return true;
            if (!(player.level().getEntity(entry.getValue().citizenId) instanceof CitizenEntity citizen)) return true;
            return !citizen.isAlive() || player.distanceToSqr(citizen) > 36.0;
        }));
    }

    public static void close(ServerPlayer player) { SESSIONS.remove(player.getUUID()); }

    public static void handle(ServerPlayer player, CitizenTradeActionPayload payload) {
        CitizenTradeAction action = payload.parsedAction();
        if (action == null) return;
        if (action == CitizenTradeAction.CLOSE) {
            close(player);
            return;
        }
        if (!(player.level().getEntity(payload.entityId()) instanceof CitizenEntity citizen)
                || !citizen.isAlive() || player.distanceToSqr(citizen) > 36.0) {
            close(player);
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (action == CitizenTradeAction.OPEN) {
            if (session == null || !session.citizenId.equals(citizen.getUUID()))
                SESSIONS.put(player.getUUID(), new Session(citizen.getUUID(), new CitizenTradeService()));
            sendView(player, citizen, "");
            return;
        }
        if (session == null || !session.citizenId.equals(citizen.getUUID())) return;
        CitizenTradeResult result = session.service.execute(player, citizen, action, payload.quantity(),
                payload.quoteVersion(), payload.requestId());
        sendView(player, citizen, message(result));
    }

    private static void sendView(ServerPlayer player, CitizenEntity citizen, String message) {
        CoinDenominations denominations = CoinDenominations.registered();
        var inventory = player.getInventory();
        int copper = 0, silver = 0, gold = 0;
        for (int i = 0; i < 36; i++) {
            var stack = inventory.getItem(i);
            if (stack.is(denominations.copper())) copper += stack.getCount();
            if (stack.is(denominations.silver())) silver += stack.getCount();
            if (stack.is(denominations.gold())) gold += stack.getCount();
        }
        CoinStock playerStock = new CoinStock(copper, silver, gold);
        CoinStock citizenStock = new TradeInventory(citizen.farmInventory().stacks()).coins(denominations);
        long playerCrowns = playerStock.value();
        long citizenCrowns = citizenStock.value();
        var quote = citizen.priceSnapshot();
        boolean wheatChangeAvailable = CoinExchange.plan(playerStock, citizenStock, quote.wheatPrice()).isPresent();
        ServerPlayNetworking.send(player, new CitizenTradeViewPayload(citizen.getId(), quote.version(),
                quote.wheatPrice(), quote.seedPrice(), quote.hoePrice(), citizen.farmInventory().sellableWheat(),
                playerCrowns, citizenCrowns, wheatChangeAvailable, citizen.farmStatus(), message));
    }

    private static String message(CitizenTradeResult result) {
        return switch (result) {
            case SUCCESS -> "Trade complete";
            case DUPLICATE -> "Request already processed";
            case STALE_QUOTE -> "Price changed. Review the new quote.";
            case INVALID_QUANTITY -> "Choose 1 to 64 items";
            case OUT_OF_RANGE -> "Move closer to the Citizen";
            case INSUFFICIENT_GOODS -> "Not enough goods for sale";
            case INSUFFICIENT_FUNDS -> "Not enough Crowns";
            case NO_CHANGE -> "Change unavailable";
            case NO_CAPACITY -> "Inventory space unavailable";
        };
    }

    private record Session(UUID citizenId, CitizenTradeService service) { }
}
