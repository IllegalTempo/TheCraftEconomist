package com.jedts.theeconomist.blueprint;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

public final class BlueprintServerHandlers {
    private static final BlueprintCaptureSelection CAPTURE_SELECTION = new BlueprintCaptureSelection();
    private BlueprintServerHandlers() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(BlueprintCaptureCornerPayload.TYPE, (payload, context) ->
                context.server().execute(() -> captureCorner(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ConfirmBlueprintPlacementPayload.TYPE, (payload, context) ->
                context.server().execute(() -> confirmPlacement(context.player(), payload)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CAPTURE_SELECTION.cancel(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (CAPTURE_SELECTION.firstCorner(player.getUUID()) == null) continue;
                if (!player.isAlive() || !validEmptyBlueprint(player)
                        || !CAPTURE_SELECTION.valid(context(player))) {
                    CAPTURE_SELECTION.cancel(player.getUUID());
                    captureFeedback(player, 0, BlockPos.ZERO, "Selection cancelled.");
                }
            }
        });
    }

    private static void captureCorner(ServerPlayer player, BlueprintCaptureCornerPayload payload) {
        if (payload.cancel()) {
            CAPTURE_SELECTION.cancel(player.getUUID());
            captureFeedback(player, 0, BlockPos.ZERO, "");
            return;
        }
        ItemStack stack = heldBlueprint(player);
        if (stack == null || BlueprintStackData.read(stack).state() != BlueprintState.EMPTY) {
            CAPTURE_SELECTION.cancel(player.getUUID());
            captureFeedback(player, 0, BlockPos.ZERO, "Hold an Empty Blueprint in the main hand.");
            return;
        }
        HitResult serverHit = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (!BlueprintCaptureTargeting.matches(serverHit, payload.corner())
                || !player.isWithinBlockInteractionRange(payload.corner(), 0.0)) {
            CAPTURE_SELECTION.cancel(player.getUUID());
            captureFeedback(player, 0, BlockPos.ZERO, "Corner is not within reach and line of sight.");
            return;
        }
        BlueprintCaptureSelection.Result selection = CAPTURE_SELECTION.click(context(player), payload.corner());
        if (selection.state() == CaptureSelectionState.FIRST_CORNER) {
            captureFeedback(player, 1, selection.firstCorner(), "Choose the opposite corner.");
            return;
        }
        BlueprintCapture.Result captured = BlueprintCapture.capture(selection.firstCorner(), selection.secondCorner(),
                new ServerBlueprintCaptureSource(player.level(), player));
        if (!captured.accepted()) {
            captureFeedback(player, 0, BlockPos.ZERO, captured.reason());
            return;
        }
        BlueprintTransitionResult transition = BlueprintTransitionService.saveDesign(BlueprintStackData.read(stack),
                captured.design());
        if (!transition.accepted()) {
            captureFeedback(player, 0, BlockPos.ZERO, transition.reason());
            return;
        }
        BlueprintStackData.write(stack, transition.data());
        player.sendSystemMessage(Component.literal("Blueprint design captured."));
        captureFeedback(player, 2, BlockPos.ZERO, "Blueprint design captured.");
    }

    private static BlueprintCaptureSelection.Context context(ServerPlayer player) {
        return new BlueprintCaptureSelection.Context(player.getUUID(),
                player.level().dimension().identifier().toString(), player.getInventory().getSelectedSlot(),
                player.getMainHandItem(), player.level().getGameTime());
    }

    private static boolean validEmptyBlueprint(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof EmptyBlueprintItem
                && BlueprintStackData.read(stack).state() == BlueprintState.EMPTY;
    }

    private static void captureFeedback(ServerPlayer player, int status, BlockPos corner, String reason) {
        ServerPlayNetworking.send(player, new BlueprintCaptureFeedbackPayload(status, corner, reason));
    }

    private static void confirmPlacement(ServerPlayer player, ConfirmBlueprintPlacementPayload payload) {
        ItemStack stack = heldBlueprint(player);
        if (stack == null) {
            feedback(player, payload.requestId(), true, false, "hold a Blueprint in the main hand");
            return;
        }
        BlueprintPlacement proposed = payload.placement();
        if (player.distanceToSqr(proposed.origin().getX() + 0.5,
                proposed.origin().getY() + 0.5, proposed.origin().getZ() + 0.5) > 33.0 * 33.0) {
            feedback(player, payload.requestId(), true, false, "placement is beyond preview reach");
            return;
        }
        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(BlueprintStackData.read(stack), proposed,
                player.level().dimension().identifier().toString(), position ->
                        !player.level().isInWorldBounds(position)
                                || !player.level().getWorldBorder().isWithinBounds(position)
                                || !player.level().isLoaded(position)
                                || !player.level().mayInteract(player, position)
                                || !player.level().getBlockState(position).isAir(), payload.expectedDesignFingerprint());
        if (!result.accepted()) {
            player.sendSystemMessage(Component.literal("Placement rejected: " + result.reason()));
            feedback(player, payload.requestId(), true, false, result.reason());
            return;
        }
        BlueprintStackData.write(stack, result.data());
        player.sendSystemMessage(Component.literal("Blueprint planned at " + proposed.origin().toShortString() + "."));
        feedback(player, payload.requestId(), true, true, "");
    }

    private static void feedback(ServerPlayer player, int requestId, boolean placement,
                                 boolean accepted, String reason) {
        ServerPlayNetworking.send(player,
                new BlueprintTransitionFeedbackPayload(requestId, placement, accepted, reason));
    }

    private static ItemStack heldBlueprint(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof EmptyBlueprintItem) return stack;
        player.sendSystemMessage(Component.literal("Hold a Blueprint in your main hand."));
        return null;
    }
}
