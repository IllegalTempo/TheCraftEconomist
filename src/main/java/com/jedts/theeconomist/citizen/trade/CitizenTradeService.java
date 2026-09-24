package com.jedts.theeconomist.citizen.trade;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One screen session, confined to the server thread. */
public final class CitizenTradeService {
    private final Set<UUID> processed = new HashSet<>();

    public CitizenTradeResult execute(ServerPlayer player, CitizenEntity citizen, CitizenTradeAction action,
                                      int quantity, long quoteVersion, UUID requestId) {
        if (action == null || requestId == null || quantity < 1 || quantity > 64)
            return CitizenTradeResult.INVALID_QUANTITY;
        if (!citizen.isAlive() || player.level() != citizen.level() || player.distanceToSqr(citizen) > 36.0)
            return CitizenTradeResult.OUT_OF_RANGE;
        if (!processed.add(requestId)) return CitizenTradeResult.DUPLICATE;
        if (quoteVersion != citizen.priceSnapshot().version()) return CitizenTradeResult.STALE_QUOTE;

        var playerInventory = player.getInventory();
        List<ItemStack> playerSlots = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) playerSlots.add(playerInventory.getItem(slot).copy());
        TradeOutcome outcome = CitizenTradeTransaction.evaluate(new TradeInventory(playerSlots),
                new TradeInventory(citizen.farmInventory().stacks()), CoinDenominations.registered(),
                citizen.priceSnapshot(), action, quantity);
        if (outcome.result() != CitizenTradeResult.SUCCESS) return outcome.result();
        for (int slot = 0; slot < 36; slot++) playerInventory.setItem(slot, outcome.playerItems().get(slot));
        for (int slot = 0; slot < citizen.farmInventory().size(); slot++)
            citizen.farmInventory().set(slot, outcome.citizenItems().get(slot));
        playerInventory.setChanged();
        player.containerMenu.broadcastChanges();
        return CitizenTradeResult.SUCCESS;
    }
}
