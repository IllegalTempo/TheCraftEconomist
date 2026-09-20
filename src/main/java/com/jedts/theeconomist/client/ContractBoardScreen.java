package com.jedts.theeconomist.client;

import com.jedts.theeconomist.contract.board.ContractBoardEntry;
import com.jedts.theeconomist.contract.board.ContractBoardPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only first version of the contract board. Acceptance will be added to the same screen later. */
public final class ContractBoardScreen extends Screen {
    private final ContractBoardPayload payload;

    public ContractBoardScreen(ContractBoardPayload payload) {
        super(Component.literal("Available contracts"));
        this.payload = payload;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
        graphics.fill(0, 0, width, height, 0xD9141A20);
        graphics.outline(12, 10, width - 24, height - 20, 0xFF718A99);
        graphics.centeredText(font, Component.literal("Available contracts"), width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Use /theeconomist contract service ... to publish one"),
                width / 2, 36, 0xFFB9C8D2);

        int y = 58;
        if (payload.contracts().isEmpty()) {
            graphics.centeredText(font, Component.literal("No open contracts"), width / 2, y + 18, 0xFFD5DEE5);
            return;
        }
        int rowHeight = 42;
        for (ContractBoardEntry entry : payload.contracts()) {
            if (y + rowHeight > height - 12) break;
            graphics.fill(24, y, width - 24, y + rowHeight - 4, 0xAA26323A);
            graphics.outline(24, y, width - 48, rowHeight - 4, 0xFF536A76);
            String title = entry.kind().name() + "  " + entry.target()
                    + (entry.quantity() == 1 ? "" : " x" + entry.quantity());
            graphics.text(font, Component.literal(title), 34, y + 6, 0xFFFFFFFF);
            graphics.text(font, Component.literal("Bounty: " + entry.bounty() + " Crowns"), 34, y + 21, 0xFFFFD166);
            long remaining = Math.max(0L, entry.deadlineTick() - payload.currentTick());
            graphics.text(font, Component.literal("Skill " + entry.requiredSkill() + "  |  " + remaining + " ticks remaining"),
                    Math.max(190, width / 2), y + 21, 0xFFD5DEE5);
            y += rowHeight;
        }
    }
}
