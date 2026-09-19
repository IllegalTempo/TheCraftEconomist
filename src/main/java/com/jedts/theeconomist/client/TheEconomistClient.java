package com.jedts.theeconomist.client;

import net.fabricmc.api.ClientModInitializer;

public final class TheEconomistClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CitizenClientRenderer.register();
    }
}
