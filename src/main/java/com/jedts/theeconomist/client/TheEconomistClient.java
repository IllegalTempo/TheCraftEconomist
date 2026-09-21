package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class TheEconomistClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CitizenClientRenderer.register();
        BlueprintHudOverlay.register();
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(BlueprintClientController::render);
        ClientPlayNetworking.registerGlobalReceiver(ContractBoardPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreenAndShow(new ContractBoardScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(CitizenInfoPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level == null) return;
                    if (!(context.client().level.getEntity(payload.entityId()) instanceof CitizenEntity citizen)) return;
                    CitizenClientHooks.open(citizen, payload);
                }));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !BlueprintClientController.designing()) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            return stack.getItem() instanceof BlockItem blockItem
                    ? BlueprintClientController.useBlock(blockItem)
                    : InteractionResult.PASS;
        });
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!BlueprintClientController.designing()) return false;
            return BlueprintClientController.attack() == InteractionResult.SUCCESS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BlueprintClientKeys.tick();
            BlueprintClientController.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlueprintClientController.cancel());
    }
}
