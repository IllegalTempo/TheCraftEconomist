package com.jedts.theeconomist.citizen.trade;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record TradeOutcome(CitizenTradeResult result, List<ItemStack> playerItems, List<ItemStack> citizenItems) {
    public static TradeOutcome refused(CitizenTradeResult reason) {
        return new TradeOutcome(reason, List.of(), List.of());
    }
}
