package com.jedts.theeconomist.citizen.trade;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitizenTradeTransactionTest {
    private static final CitizenPriceSnapshot QUOTE = new CitizenPriceSnapshot(0, 1, 3, 1, 8);

    private static CoinDenominations coins() {
        return new CoinDenominations(Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT);
    }

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var components = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        for (Item item : List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.WOODEN_HOE,
                Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT))
            item.builtInRegistryHolder().bindComponents(components);
    }

    @Test void buyingWheatMovesGoodsAndTheSameCrownValue() {
        TradeInventory player = new TradeInventory(List.of(new ItemStack(Items.COPPER_INGOT, 3), ItemStack.EMPTY));
        TradeInventory citizen = new TradeInventory(List.of(new ItemStack(Items.WHEAT, 8), ItemStack.EMPTY));
        TradeOutcome outcome = CitizenTradeTransaction.evaluate(player, citizen, coins(), QUOTE,
                CitizenTradeAction.BUY_WHEAT, 1);
        assertEquals(CitizenTradeResult.SUCCESS, outcome.result());
        assertEquals(1, new TradeInventory(outcome.playerItems()).count(Items.WHEAT));
        assertEquals(7, new TradeInventory(outcome.citizenItems()).count(Items.WHEAT));
        assertEquals(3, new TradeInventory(outcome.citizenItems()).count(Items.COPPER_INGOT));
        assertEquals(3, player.count(Items.COPPER_INGOT));
    }

    @Test void unavailableChangeDoesNotMoveAnything() {
        TradeInventory player = new TradeInventory(List.of(new ItemStack(Items.IRON_INGOT, 1), ItemStack.EMPTY));
        TradeInventory citizen = new TradeInventory(List.of(new ItemStack(Items.WHEAT, 8),
                new ItemStack(Items.COPPER_INGOT, 6)));
        TradeOutcome outcome = CitizenTradeTransaction.evaluate(player, citizen, coins(), QUOTE,
                CitizenTradeAction.BUY_WHEAT, 1);
        assertEquals(CitizenTradeResult.NO_CHANGE, outcome.result());
        assertEquals(8, citizen.count(Items.WHEAT));
        assertEquals(1, player.count(Items.IRON_INGOT));
    }
}
