package com.jedts.theeconomist.currency;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.function.Consumer;

public final class CrownItem extends Item {
    private final CrownDenomination denomination;

    public CrownItem(CrownDenomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        String key = denomination == CrownDenomination.COPPER
                ? "tooltip.theeconomist.crown_value.one"
                : "tooltip.theeconomist.crown_value.many";
        if (denomination == CrownDenomination.COPPER) {
            tooltip.accept(Component.translatable(key));
        } else {
            tooltip.accept(Component.translatable(key, denomination.unitValue()));
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
