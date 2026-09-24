package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaims;
import com.jedts.theeconomist.citizen.farm.claim.PlotOwner;
import com.jedts.theeconomist.citizen.trade.CitizenPriceSnapshot;
import com.jedts.theeconomist.citizen.trade.CoinDenominations;
import com.jedts.theeconomist.citizen.trade.TradeInventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** A bounded, server-authored summary for the Citizen information screen. */
public record CitizenInfoDetails(List<ItemStack> inventory, String farmStatus, String home, int claimedFarmland,
                                 int sellableWheat, long crownBalance,
                                 int wheatPrice, int seedPrice, int hoePrice) {
    public CitizenInfoDetails {
        if (inventory.size() != CitizenFarmInventory.SIZE || farmStatus.length() > 128 || home.length() > 64
                || claimedFarmland < 0 || sellableWheat < 0 || crownBalance < 0
                || wheatPrice < 1 || seedPrice < 1 || hoePrice < 1)
            throw new IllegalArgumentException("invalid Citizen info details");
        inventory = inventory.stream().map(ItemStack::copy).toList();
    }

    public static CitizenInfoDetails from(CitizenFarmInventory inventory, PlotClaims claims, PlotOwner plotOwner,
                                          CoinDenominations coins, CitizenPriceSnapshot prices, String status,
                                          String home) {
        int claimed = (int) claims.entries().values().stream()
                .filter(plotOwner::equals).count();
        List<ItemStack> stacks = inventory.stacks();
        return new CitizenInfoDetails(stacks, status, home, claimed, inventory.sellableWheat(),
                new TradeInventory(stacks).coins(coins).value(), prices.wheatPrice(),
                prices.seedPrice(), prices.hoePrice());
    }

    public static CitizenInfoDetails from(CitizenFarmInventory inventory, PlotClaims claims, UUID citizenId,
                                          CoinDenominations coins, CitizenPriceSnapshot prices, String status,
                                          String home) {
        return from(inventory, claims, new PlotOwner(PlotOwner.Kind.CITIZEN, citizenId),
                coins, prices, status, home);
    }
}
