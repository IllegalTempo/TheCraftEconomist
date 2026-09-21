package com.jedts.theeconomist.blueprint;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class BlueprintServerHandlers {
    private BlueprintServerHandlers() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SaveBlueprintDesignPayload.TYPE, (payload, context) ->
                context.server().execute(() -> saveDesign(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ConfirmBlueprintPlacementPayload.TYPE, (payload, context) ->
                context.server().execute(() -> confirmPlacement(context.player(), payload)));
    }

    private static void saveDesign(ServerPlayer player, SaveBlueprintDesignPayload payload) {
        ItemStack stack = heldBlueprint(player);
        if (stack == null) {
            feedback(player, payload.requestId(), false, false, "hold a Blueprint in the main hand");
            return;
        }
        BlueprintTransitionResult result = BlueprintTransitionService.saveDesign(BlueprintStackData.read(stack), payload.design());
        if (!result.accepted()) {
            player.sendSystemMessage(Component.literal("Design rejected: " + result.reason()));
            feedback(player, payload.requestId(), false, false, result.reason());
            return;
        }
        BlueprintStackData.write(stack, result.data());
        player.sendSystemMessage(Component.literal("Blueprint design saved."));
        feedback(player, payload.requestId(), false, true, "");
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
                                || !player.level().getBlockState(position).isAir(), payload.expectedDesignHash());
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
