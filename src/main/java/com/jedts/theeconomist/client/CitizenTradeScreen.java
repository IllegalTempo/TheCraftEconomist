package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.trade.CitizenTradeAction;
import com.jedts.theeconomist.citizen.trade.CitizenTradeActionPayload;
import com.jedts.theeconomist.citizen.trade.CitizenTradeViewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class CitizenTradeScreen extends Screen {
    private static CitizenTradeScreen current;
    private static int awaitingEntityId = -1;
    private CitizenTradeViewPayload view;
    private int quantity = 1;

    public CitizenTradeScreen(CitizenTradeViewPayload view) {
        super(Component.literal("Citizen trade"));
        this.view = view;
    }

    public int entityId() { return view.entityId(); }

    public void update(CitizenTradeViewPayload view) { this.view = view; }

    public static void await(int entityId) { awaitingEntityId = entityId; }

    public static void receive(CitizenTradeViewPayload view) {
        if (current != null && current.entityId() == view.entityId()) current.update(view);
        else if (awaitingEntityId == view.entityId())
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new CitizenTradeScreen(view));
    }

    @Override
    protected void init() {
        current = this;
        awaitingEntityId = -1;
        int cx = width / 2;
        int y = height / 2;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> quantity = Math.max(1, quantity - 1))
                .bounds(cx - 100, y - 22, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> quantity = Math.min(64, quantity + 1))
                .bounds(cx + 65, y - 22, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Buy wheat"), b -> act(CitizenTradeAction.BUY_WHEAT))
                .bounds(cx - 105, y + 10, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sell seeds"), b -> act(CitizenTradeAction.SELL_SEEDS))
                .bounds(cx + 5, y + 10, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Sell wooden hoe"), b -> act(CitizenTradeAction.SELL_WOODEN_HOE))
                .bounds(cx - 65, y + 40, 130, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = width / 2;
        int y = height / 2;
        graphics.centeredText(font, Component.literal("Trade with Citizen"), cx, y - 90, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Wheat: " + view.sellableWheat() + " available at "
                + view.wheatPrice() + " Crowns each"), cx, y - 69, 0xFFE8D79E);
        graphics.centeredText(font, Component.literal("Citizen pays: seeds " + view.seedPrice()
                + ", wooden hoe " + view.hoePrice()), cx, y - 53, 0xFFE8D79E);
        graphics.centeredText(font, Component.literal("Your Crowns: " + view.playerCrowns()
                + "    Citizen Crowns: " + view.citizenCrowns()), cx, y - 37, 0xFFD5DEE5);
        graphics.centeredText(font, Component.literal(view.farmStatus()), cx, y + 88, 0xFFB8DFA8);
        graphics.centeredText(font, Component.literal("One wheat payment/change: "
                + (view.wheatChangeAvailable() ? "available" : "unavailable")), cx, y - 110, 0xFFD5DEE5);
        graphics.centeredText(font, Component.literal("Quantity: " + quantity), cx, y - 17, 0xFFFFFFFF);
        if (!view.message().isBlank())
            graphics.centeredText(font, Component.literal(view.message()), cx, y + 72, 0xFFFFD166);
    }

    private void act(CitizenTradeAction action) {
        ClientPlayNetworking.send(new CitizenTradeActionPayload(view.entityId(), action.ordinal(),
                action == CitizenTradeAction.SELL_WOODEN_HOE ? 1 : quantity, view.quoteVersion(), UUID.randomUUID()));
    }

    @Override
    public void onClose() {
        current = null;
        awaitingEntityId = -1;
        ClientPlayNetworking.send(new CitizenTradeActionPayload(view.entityId(), CitizenTradeAction.CLOSE.ordinal(),
                0, view.quoteVersion(), UUID.randomUUID()));
        super.onClose();
    }

    @Override
    public void removed() {
        if (current == this) current = null;
        super.removed();
    }
}
