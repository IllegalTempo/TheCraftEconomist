package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintDesign;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Visible planning-mode feedback while the full world ghost editor is being added. */
public final class BlueprintPlanningScreen extends Screen {
    private final boolean designMode;
    private final BlueprintDesign design;

    public BlueprintPlanningScreen(boolean designMode, BlueprintDesign design) {
        super(Component.literal(designMode ? "Blueprint design mode" : "Blueprint placement mode"));
        this.designMode = designMode;
        this.design = design;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
        int panelWidth = Math.min(420, width - 32);
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, (height - 150) / 2);
        graphics.fill(left, top, left + panelWidth, top + 150, 0xEE14202A);
        graphics.outline(left, top, panelWidth, 150, 0xFF6D8799);
        graphics.centeredText(font, Component.literal(designMode ? "Blueprint design mode" : "Blueprint placement mode"),
                width / 2, top + 16, 0xFFFFFFFF);
        int y = top + 44;
        if (designMode) {
            graphics.centeredText(font, Component.literal("Build your structure in the world preview."), width / 2, y, 0xFFD5DEE5);
            graphics.centeredText(font, Component.literal("Save: Enter    Cancel: Escape"), width / 2, y + 20, 0xFFD5DEE5);
        } else {
            String size = design == null ? "no design loaded" : design.width() + " x " + design.height() + " x " + design.depth();
            graphics.centeredText(font, Component.literal("Design size: " + size), width / 2, y, 0xFFD5DEE5);
            graphics.centeredText(font, Component.literal("Rotate: R    Place: Enter    Cancel: Escape"), width / 2, y + 20, 0xFFD5DEE5);
        }
        graphics.centeredText(font, Component.literal("Ghost preview controls are being connected."), width / 2, y + 60, 0xFFFFD166);
    }

    @Override
    public void onClose() {
        BlueprintClientController.cancel();
        super.onClose();
    }
}
