package com.jedts.theeconomist.client;

import com.jedts.theeconomist.trade.TradeActionPayload;
import com.jedts.theeconomist.trade.TradeViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** The request screen; accepted trades switch to the vanilla container-style offer screen. */
public final class TradeScreen extends Screen {
    private static UUID activeId;
    private static UUID cancelledId;
    private final TradeViewPayload view;

    public TradeScreen(TradeViewPayload view) {
        super(Component.literal("Player trade"));
        this.view = view;
    }

    @Override
    protected void init() {
        activeId = view.id();
        addRenderableWidget(Button.builder(Component.literal(view.selfAccepted() ? "Accepted" : "Accept"),
                button -> act(TradeActionPayload.ACCEPT))
                .bounds(width / 2 - 105, height / 2 + 22, 100, 20).build()).active = !view.selfAccepted();
        addRenderableWidget(Button.builder(Component.literal("Decline"),
                button -> act(TradeActionPayload.DECLINE))
                .bounds(width / 2 + 5, height / 2 + 22, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
        graphics.fill(0, 0, width, height, 0xD9141A20);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, Component.literal("Trade with " + view.peerName()), width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Both players must accept within 15 seconds"),
                width / 2, height / 2 - 25, 0xFFCFD9E0);
        graphics.centeredText(font, Component.literal("You: " + status(view.selfAccepted())
                        + "     " + view.peerName() + ": " + status(view.peerAccepted())),
                width / 2, height / 2 - 5, 0xFFFFD166);
        if (!view.message().isBlank()) {
            graphics.centeredText(font, Component.literal(view.message()), width / 2,
                    height / 2 + 54, 0xFFB9C8D2);
        }
    }

    private static String status(boolean accepted) { return accepted ? "accepted" : "waiting"; }

    private void act(int action) {
        ClientPlayNetworking.send(new TradeActionPayload(view.id(), action, -1));
    }

    @Override
    public void onClose() {
        cancelledId = view.id();
        act(TradeActionPayload.CANCEL);
        super.onClose();
    }

    @Override
    public void removed() {
        if (view.id().equals(activeId)) activeId = null;
        super.removed();
    }

    public static boolean active(UUID id) { return id.equals(activeId); }
    public static boolean cancelled(UUID id) { return id.equals(cancelledId); }
    public static void closed(UUID id) { if (id.equals(cancelledId)) cancelledId = null; }
}
