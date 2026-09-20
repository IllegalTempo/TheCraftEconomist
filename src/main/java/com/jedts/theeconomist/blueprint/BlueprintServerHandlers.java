package com.jedts.theeconomist.blueprint;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class BlueprintServerHandlers {
    private BlueprintServerHandlers() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(BlueprintUpdatePayload.TYPE, (payload, context) ->
                context.server().execute(() -> apply(context.player(), payload)));
    }

    private static void apply(ServerPlayer player, BlueprintUpdatePayload payload) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof EmptyBlueprintItem)) {
            player.sendSystemMessage(Component.literal("Hold an Empty Blueprint in your main hand."));
            return;
        }
        BlueprintStackData current = BlueprintStackData.read(stack);
        if (!payload.placement()) {
            if (current.state() != BlueprintState.EMPTY) {
                player.sendSystemMessage(Component.literal("This blueprint is not empty."));
                return;
            }
            BlueprintValidationResult result = BlueprintValidator.validateDesign(payload.design());
            if (!result.valid()) { player.sendSystemMessage(Component.literal("Design rejected: " + result.reason())); return; }
            BlueprintStackData.setDesigned(stack, payload.design());
            player.sendSystemMessage(Component.literal("Blueprint design saved."));
            return;
        }
        if (current.state() != BlueprintState.DESIGNED || current.design() == null) {
            player.sendSystemMessage(Component.literal("Only a designed blueprint can be planned."));
            return;
        }
        BlueprintValidationResult result = BlueprintValidator.validatePlacement(current.design(), payload.target(), position ->
                !player.level().getBlockState(position).isAir());
        if (!result.valid()) { player.sendSystemMessage(Component.literal("Placement rejected: " + result.reason())); return; }
        BlueprintStackData.setPlanned(stack, payload.target());
        player.sendSystemMessage(Component.literal("Blueprint planned at " + payload.target().origin().toShortString() + "."));
    }
}
