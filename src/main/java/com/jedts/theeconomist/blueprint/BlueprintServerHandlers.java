package com.jedts.theeconomist.blueprint;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class BlueprintServerHandlers {
    private BlueprintServerHandlers() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SaveBlueprintDesignPayload.TYPE, (payload, context) ->
                context.server().execute(() -> saveDesign(context.player(), payload.design())));
        ServerPlayNetworking.registerGlobalReceiver(ConfirmBlueprintPlacementPayload.TYPE, (payload, context) ->
                context.server().execute(() -> confirmPlacement(context.player(), payload.placement())));
    }

    private static void saveDesign(ServerPlayer player, BlueprintDesign proposed) {
        ItemStack stack = heldBlueprint(player);
        if (stack == null) return;
        BlueprintTransitionResult result = BlueprintTransitionService.saveDesign(BlueprintStackData.read(stack), proposed);
        if (!result.accepted()) {
            player.sendSystemMessage(Component.literal("Design rejected: " + result.reason()));
            return;
        }
        BlueprintStackData.write(stack, result.data());
        player.sendSystemMessage(Component.literal("Blueprint design saved."));
    }

    private static void confirmPlacement(ServerPlayer player, BlueprintPlacement proposed) {
        ItemStack stack = heldBlueprint(player);
        if (stack == null) return;
        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(BlueprintStackData.read(stack), proposed,
                player.level().dimension().identifier().toString(), position -> !player.level().getBlockState(position).isAir());
        if (!result.accepted()) {
            player.sendSystemMessage(Component.literal("Placement rejected: " + result.reason()));
            return;
        }
        BlueprintStackData.write(stack, result.data());
        player.sendSystemMessage(Component.literal("Blueprint planned at " + proposed.origin().toShortString() + "."));
    }

    private static ItemStack heldBlueprint(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof EmptyBlueprintItem) return stack;
        player.sendSystemMessage(Component.literal("Hold a Blueprint in your main hand."));
        return null;
    }
}
