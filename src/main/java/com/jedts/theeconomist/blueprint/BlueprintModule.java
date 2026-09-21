package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.api.module.TheEconomistModule;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class BlueprintModule implements TheEconomistModule {
    @Override public String id() { return "blueprint"; }
    @Override public void initialize() {
        BlueprintItems.register();
        PayloadTypeRegistry.serverboundPlay().register(SaveBlueprintDesignPayload.TYPE, SaveBlueprintDesignPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfirmBlueprintPlacementPayload.TYPE,
                ConfirmBlueprintPlacementPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BlueprintTransitionFeedbackPayload.TYPE,
                BlueprintTransitionFeedbackPayload.CODEC);
        BlueprintServerHandlers.register();
    }
}
