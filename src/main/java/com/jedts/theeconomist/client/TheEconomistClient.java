package com.jedts.theeconomist.client;

import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import com.jedts.theeconomist.citizen.info.CitizenInfoPayload;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import com.jedts.theeconomist.citizen.info.CitizenInfoScreenData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

public final class TheEconomistClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CitizenClientRenderer.register();
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(BlueprintClientController::renderPlacementPreview);
        ClientPlayNetworking.registerGlobalReceiver(ContractBoardPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreenAndShow(new ContractBoardScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(CitizenInfoPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().level == null) return;
            if (!(context.client().level.getEntity(payload.entityId()) instanceof CitizenEntity citizen)) return;
            CitizenClientHooks.open(citizen, payload);
        }));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() == com.jedts.theeconomist.blueprint.BlueprintItems.EMPTY_BLUEPRINT) {
                if (BlueprintClientController.designing()) {
                    BlueprintClientController.captureDesign(stack);
                    return InteractionResult.SUCCESS;
                }
                if (BlueprintClientController.placing()) {
                    if (player.isSprinting()) {
                        BlueprintClientController.rotatePlacement();
                        return InteractionResult.SUCCESS;
                    }
                    BlueprintClientController.confirmPlacement(BlueprintClientController.currentPlacement(
                            net.minecraft.client.Minecraft.getInstance()));
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !BlueprintClientController.designing()) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                return InteractionResult.PASS;
            }
            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            BlueprintClientController.placeFake(minecraft(), target, blockItem.getBlock().defaultBlockState());
            return InteractionResult.SUCCESS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide() && BlueprintClientController.designing()) {
                BlueprintClientController.removeFake(minecraft(), pos);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BlueprintClientController.updatePlacementPreview(client);
            if (BlueprintClientController.designing() && client.player != null
                    && BlueprintClientController.designOrigin() != null
                    && BlueprintClientController.designOrigin().distSqr(client.player.blockPosition()) > 32 * 32) {
                BlueprintClientController.cancel();
                client.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Design mode ended: 32-block limit reached."));
            }
        });
    }

    private static net.minecraft.client.Minecraft minecraft() { return net.minecraft.client.Minecraft.getInstance(); }
}
