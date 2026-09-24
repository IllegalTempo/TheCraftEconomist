package com.jedts.theeconomist.trade;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class TradeModule implements TheEconomistModule {
    @Override public String id() { return "trade"; }

    @Override public void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TradeActionPayload.TYPE, TradeActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TradeViewPayload.TYPE, TradeViewPayload.CODEC);
        TradeServer.register();
    }
}
