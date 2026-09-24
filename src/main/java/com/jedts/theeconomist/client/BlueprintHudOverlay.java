package com.jedts.theeconomist.client;

import com.jedts.theeconomist.TheEconomistMod;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BlueprintHudOverlay {
    private BlueprintHudOverlay() { }

    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "blueprint_controls");
        HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, id,
                BlueprintHudOverlay::extractRenderState);
    }

    private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!BlueprintClientController.placing() && !BlueprintClientController.selecting()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int x = minecraft.getWindow().getGuiScaledWidth() / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 72;
        if (BlueprintClientController.selecting()) {
            var corner = BlueprintClientController.firstCorner();
            String hint = corner == null ? "Right-click a block to select the first corner"
                    : "First corner " + corner.toShortString() + " — right-click opposite corner (Esc cancels)";
            graphics.centeredText(minecraft.font, Component.literal(hint), x, y, 0xFFFFFFFF);
        } else {
            graphics.centeredText(minecraft.font, Component.translatable("hud.theeconomist.blueprint.rotate",
                            BlueprintClientKeys.rotateLabel()), x, y, 0xFFFFFFFF);
        }
    }
}
