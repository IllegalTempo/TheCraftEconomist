package com.jedts.theeconomist.client;

import com.jedts.theeconomist.trade.TradeActionPayload;
import com.jedts.theeconomist.trade.TradeViewPayload;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.UUID;

/** Uses vanilla container slot rendering, hover highlighting, item counts, and tooltips. */
public final class TradeOfferScreen extends AbstractContainerScreen<TradeMenu> {
    private static final Identifier SLOT = Identifier.fromNamespaceAndPath("minecraft", "container/slot");
    private static final int LABEL_COLOR = 0xFF171717;
    private static final int STATUS_COLOR = 0xFF392700;
    private static TradeOfferScreen current;
    private static UUID cancelledId;
    private TradeViewPayload view;
    private Button readyButton;

    public TradeOfferScreen(Inventory inventory, TradeViewPayload view) {
        super(new TradeMenu(inventory), inventory, Component.literal("Trade with " + view.peerName()), 270, 226);
        this.view = view;
        menu.updateOffers(view);
    }

    @Override
    protected void init() {
        super.init();
        current = this;
        readyButton = addRenderableWidget(Button.builder(readyText(),
                button -> act(TradeActionPayload.READY, -1))
                .bounds(leftPos + 30, topPos + 202, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel trade"),
                button -> act(TradeActionPayload.CANCEL, -1))
                .bounds(leftPos + 140, topPos + 202, 100, 18).build());
    }

    public void update(TradeViewPayload next) {
        if (!view.id().equals(next.id())) return;
        view = next;
        menu.updateOffers(next);
        if (readyButton != null) readyButton.setMessage(readyText());
    }

    private Component readyText() { return Component.literal(view.selfReady() ? "Unready" : "Ready"); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
        graphics.fill(0, 0, width, height, 0xD9141A20);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFF373737);
        graphics.outline(leftPos + 2, topPos + 2, imageWidth - 4, imageHeight - 4, 0xFFF8F8F8);
        for (Slot slot : menu.slots) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT,
                    leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(8, 5, imageWidth - 8, 19, 0xFF34404B);
        graphics.text(font, Component.literal("Trade with " + view.peerName()).withStyle(ChatFormatting.BOLD),
                12, 8, 0xFFFFFFFF);
        graphics.text(font, Component.literal("Your offer" + (view.selfReady() ? " ✓" : ""))
                .withStyle(ChatFormatting.BOLD), 12, 22, LABEL_COLOR);
        graphics.text(font, Component.literal(view.peerName() + "'s offer" + (view.peerReady() ? " ✓" : ""))
                .withStyle(ChatFormatting.BOLD), 150, 22, LABEL_COLOR);
        graphics.fill(8, 92, imageWidth - 8, 106, 0xFFF4E6BD);
        String status;
        if (view.selfReady() && view.peerReady()) {
            status = "Completing in " + Math.max(1, (view.countdown() + 19) / 20) + "...";
        } else {
            status = view.message().isBlank() ? "Click an inventory slot to offer it" : view.message();
        }
        String fullStatus = status;
        int visibleChars = fullStatus.length();
        while (visibleChars > 0 && font.width(Component.literal(status).withStyle(ChatFormatting.BOLD)) > imageWidth - 24) {
            status = fullStatus.substring(0, --visibleChars) + "...";
        }
        graphics.centeredText(font, Component.literal(status).withStyle(ChatFormatting.BOLD),
                imageWidth / 2, 96, STATUS_COLOR);
        graphics.text(font, Component.literal("Inventory").withStyle(ChatFormatting.BOLD),
                54, 108, LABEL_COLOR);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        if (slot != null && slot.index >= TradeMenu.INVENTORY_START
                && input == ContainerInput.PICKUP && button == 0) {
            act(TradeActionPayload.TOGGLE_SLOT, slot.getContainerSlot());
        }
        // Do not call super: there is no server container to receive vanilla click packets.
    }

    private void act(int action, int slot) {
        ClientPlayNetworking.send(new TradeActionPayload(view.id(), action, slot));
    }

    @Override
    public void onClose() {
        cancelledId = view.id();
        act(TradeActionPayload.CANCEL, -1);
        minecraft.setScreenAndShow(null);
    }

    @Override
    public void removed() {
        if (current == this) current = null;
        super.removed();
    }

    public static TradeOfferScreen active(UUID id) {
        return current != null && current.view.id().equals(id) ? current : null;
    }
    public static boolean cancelled(UUID id) { return id.equals(cancelledId); }
    public static void closed(UUID id) { if (id.equals(cancelledId)) cancelledId = null; }
}
