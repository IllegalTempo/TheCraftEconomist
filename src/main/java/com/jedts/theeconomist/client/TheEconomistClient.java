package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.citizen.trade.CitizenTradeViewPayload;
import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import com.jedts.theeconomist.blueprint.BlueprintTransitionFeedbackPayload;
import com.jedts.theeconomist.blueprint.BlueprintCaptureFeedbackPayload;
import com.jedts.theeconomist.blueprint.BlueprintItems;
import com.jedts.theeconomist.blueprint.BlueprintStackData;
import com.jedts.theeconomist.trade.TradeViewPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
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
        ClientPlayNetworking.registerGlobalReceiver(CitizenTradeViewPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CitizenTradeScreen.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BlueprintTransitionFeedbackPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        BlueprintClientController.handleTransitionFeedback(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BlueprintCaptureFeedbackPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        BlueprintClientController.handleCaptureFeedback(payload)));
        ClientPlayNetworking.registerGlobalReceiver(TradeViewPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (payload.stage() == TradeViewPayload.CLOSED) {
                        if (TradeScreen.active(payload.id()) || TradeOfferScreen.active(payload.id()) != null)
                            context.client().setScreenAndShow(null);
                        TradeScreen.closed(payload.id());
                        TradeOfferScreen.closed(payload.id());
                    } else if (payload.stage() == TradeViewPayload.REQUEST) {
                        if (!TradeScreen.cancelled(payload.id()))
                            context.client().setScreenAndShow(new TradeScreen(payload));
                    } else if (payload.stage() == TradeViewPayload.OFFER
                            && !TradeScreen.cancelled(payload.id()) && !TradeOfferScreen.cancelled(payload.id())
                            && context.client().player != null) {
                        TradeOfferScreen current = TradeOfferScreen.active(payload.id());
                        if (current != null) current.update(payload);
                        else context.client().setScreenAndShow(
                                new TradeOfferScreen(context.client().player.getInventory(), payload));
                    }
                }));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() != BlueprintItems.EMPTY_BLUEPRINT) return InteractionResult.PASS;
            if (BlueprintCaptureInput.consumeBlockUse(BlueprintStackData.read(stack).state(), hand)) {
                BlueprintClientController.handleCaptureCorner(hit.getBlockPos());
                return InteractionResult.FAIL;
            }
            if (BlueprintClientController.placing() || BlueprintStackData.read(stack).design() != null) {
                BlueprintClientController.handleBlueprintUse(stack);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (BlueprintClientController.placing() && hand == InteractionHand.MAIN_HAND
                    && player.getItemInHand(hand).getItem() == BlueprintItems.EMPTY_BLUEPRINT) {
                BlueprintClientController.handleBlueprintUse(player.getItemInHand(hand));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BlueprintClientKeys.tick();
            BlueprintClientController.tick(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BlueprintClientController.cancel());
    }
}
