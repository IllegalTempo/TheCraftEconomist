package com.jedts.theeconomist.client;

import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.info.CitizenInfoScreenData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;

public final class TheEconomistClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CitizenClientRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(ContractBoardPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreenAndShow(new ContractBoardScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(CitizenInfoPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().level == null) return;
            if (!(context.client().level.getEntity(payload.entityId()) instanceof CitizenEntity citizen)) return;
            CitizenClientHooks.open(citizen, payload);
        }));
    }
}
