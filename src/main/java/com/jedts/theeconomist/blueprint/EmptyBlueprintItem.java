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
        BlueprintItemAction action = BlueprintItemBehavior.action(BlueprintStackData.read(player.getItemInHand(hand)).state());
        if (action == BlueprintItemAction.NONE) {
            if (!level.isClientSide()) player.sendSystemMessage(Component.literal("This blueprint is already planned."));
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            try {
                Class<?> controller = Class.forName("com.jedts.theeconomist.client.BlueprintClientController");
                controller.getMethod(action == BlueprintItemAction.DESIGN ? "startDesign" : "startPlacement").invoke(null);
            } catch (ReflectiveOperationException ignored) {
                // Client-only controller is optional on a dedicated server.
            }
        }
        return InteractionResult.SUCCESS;
    }
}
