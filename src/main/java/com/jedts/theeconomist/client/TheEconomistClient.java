package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import com.jedts.theeconomist.blueprint.BlueprintTransitionFeedbackPayload;
import com.jedts.theeconomist.blueprint.BlueprintItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
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
        ClientPlayNetworking.registerGlobalReceiver(BlueprintTransitionFeedbackPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        BlueprintClientController.handleTransitionFeedback(payload)));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !BlueprintClientController.designing()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem blockItem) {
                BlueprintClientController.useBlock(blockItem);
                return InteractionResult.FAIL;
            }
            return stack.getItem() == BlueprintItems.EMPTY_BLUEPRINT
                    ? InteractionResult.PASS : InteractionResult.FAIL;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && BlueprintClientController.placing()
                    && hand == InteractionHand.MAIN_HAND
                    && player.getItemInHand(hand).getItem() == BlueprintItems.EMPTY_BLUEPRINT) {
                BlueprintClientController.handleBlueprintUse(player.getItemInHand(hand));
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide() || !BlueprintClientController.designing()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem blockItem) {
                BlueprintClientController.useBlock(blockItem);
                return InteractionResult.FAIL;
            }
            if (stack.getItem() == BlueprintItems.EMPTY_BLUEPRINT) {
                BlueprintClientController.handleBlueprintUse(stack);
            }
            return InteractionResult.FAIL;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (BlueprintClientController.placing() && hand == InteractionHand.MAIN_HAND
                    && player.getItemInHand(hand).getItem() == BlueprintItems.EMPTY_BLUEPRINT) {
                BlueprintClientController.handleBlueprintUse(player.getItemInHand(hand));
                return InteractionResult.FAIL;
            }
            if (!BlueprintClientController.designing()) return InteractionResult.PASS;
            if (hand == InteractionHand.MAIN_HAND) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof BlockItem blockItem) {
                    BlueprintClientController.useBlock(blockItem);
                } else if (stack.getItem() == BlueprintItems.EMPTY_BLUEPRINT) {
                    BlueprintClientController.handleBlueprintUse(stack);
                }
            }
            return InteractionResult.FAIL;
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
