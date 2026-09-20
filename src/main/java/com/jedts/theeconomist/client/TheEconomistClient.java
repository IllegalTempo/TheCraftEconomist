package com.jedts.theeconomist.client;

import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;

public final class TheEconomistClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CitizenClientRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(ContractBoardPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreenAndShow(new ContractBoardScreen(payload))));
    }
}
