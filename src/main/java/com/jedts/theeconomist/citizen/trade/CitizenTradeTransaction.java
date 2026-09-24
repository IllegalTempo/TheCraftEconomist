package com.jedts.theeconomist.citizen.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Computes an all-or-nothing exchange on copies of both inventories. */
public final class CitizenTradeTransaction {
    private CitizenTradeTransaction() { }

    public static TradeOutcome evaluate(TradeInventory playerSource, TradeInventory citizenSource,
                                        CoinDenominations denominations, CitizenPriceSnapshot quote,
                                        CitizenTradeAction action, int quantity) {
        if (quantity < 1 || quantity > 64 || action == CitizenTradeAction.OPEN || action == CitizenTradeAction.CLOSE
                || (action == CitizenTradeAction.SELL_WOODEN_HOE && quantity != 1))
            return TradeOutcome.refused(CitizenTradeResult.INVALID_QUANTITY);
        TradeInventory player = playerSource.copy();
        TradeInventory citizen = citizenSource.copy();
        boolean playerBuys = action == CitizenTradeAction.BUY_WHEAT;
        Item goods = switch (action) {
            case BUY_WHEAT -> Items.WHEAT;
            case SELL_SEEDS -> Items.WHEAT_SEEDS;
            case SELL_WOODEN_HOE -> Items.WOODEN_HOE;
            default -> throw new IllegalArgumentException("not a trade action");
        };
        TradeInventory seller = playerBuys ? citizen : player;
        TradeInventory buyer = playerBuys ? player : citizen;
        int available = playerBuys ? Math.max(0, citizen.count(goods) - 4) : seller.count(goods);
        if (available < quantity) return TradeOutcome.refused(CitizenTradeResult.INSUFFICIENT_GOODS);
        int unitPrice = switch (action) {
            case BUY_WHEAT -> quote.wheatPrice();
            case SELL_SEEDS -> quote.seedPrice();
            case SELL_WOODEN_HOE -> quote.hoePrice();
            default -> throw new IllegalArgumentException("not a trade action");
        };
        int price;
        try { price = Math.multiplyExact(unitPrice, quantity); }
        catch (ArithmeticException overflow) { return TradeOutcome.refused(CitizenTradeResult.INVALID_QUANTITY); }
        TradeInventory payer = playerBuys ? player : citizen;
        TradeInventory receiver = playerBuys ? citizen : player;
        CoinStock payerCoins = payer.coins(denominations);
        if (payerCoins.value() < price) return TradeOutcome.refused(CitizenTradeResult.INSUFFICIENT_FUNDS);
        CoinPlan coins = CoinExchange.plan(payerCoins, receiver.coins(denominations), price).orElse(null);
        if (coins == null) return TradeOutcome.refused(CitizenTradeResult.NO_CHANGE);
        ItemStack movedGoods = seller.remove(goods, quantity);
        if (movedGoods.isEmpty() || !payer.removeCoins(denominations, coins.payment())
                || !receiver.removeCoins(denominations, coins.change())
                || !buyer.insert(movedGoods) || !receiver.insertCoins(denominations, coins.payment())
                || !payer.insertCoins(denominations, coins.change()))
            return TradeOutcome.refused(CitizenTradeResult.NO_CAPACITY);
        return new TradeOutcome(CitizenTradeResult.SUCCESS, player.slots(), citizen.slots());
    }
}
