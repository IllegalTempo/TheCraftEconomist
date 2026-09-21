package com.jedts.theeconomist.blueprint;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

public final class EmptyBlueprintItem extends Item {
    public EmptyBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND) {
            player.sendOverlayMessage(Component.literal("Hold the Blueprint in your main hand."));
            return InteractionResult.SUCCESS;
        }
        if (BlueprintStackData.read(player.getItemInHand(hand)).state() == BlueprintState.EMPTY) {
            player.sendOverlayMessage(Component.literal("Right-click a block for each blueprint corner."));
            return InteractionResult.SUCCESS;
        }
        try {
            Class<?> controller = Class.forName("com.jedts.theeconomist.client.BlueprintClientController");
            controller.getMethod("handleBlueprintUse", ItemStack.class).invoke(null, player.getItemInHand(hand));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Blueprint client controller is unavailable", exception);
        }
        return InteractionResult.SUCCESS;
    }
}
