package com.jedts.theeconomist.client;

import com.jedts.theeconomist.trade.TradeViewPayload;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Client-only menu model. Slot clicks are sent as trade intents by TradeOfferScreen. */
public final class TradeMenu extends AbstractContainerMenu {
    public static final int OFFER_SLOTS = 18;
    public static final int INVENTORY_START = OFFER_SLOTS * 2;
    private final SimpleContainer ownOffer = new SimpleContainer(OFFER_SLOTS);
    private final SimpleContainer peerOffer = new SimpleContainer(OFFER_SLOTS);

    public TradeMenu(Inventory inventory) {
        super(MenuType.GENERIC_9x6, 0);
        for (int i = 0; i < OFFER_SLOTS; i++) {
            addSlot(new DisplaySlot(ownOffer, i, 13 + (i % 6) * 18, 35 + (i / 6) * 18));
        }
        for (int i = 0; i < OFFER_SLOTS; i++) {
            addSlot(new DisplaySlot(peerOffer, i, 151 + (i % 6) * 18, 35 + (i / 6) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int inventorySlot = 9 + row * 9 + col;
                addSlot(new Slot(inventory, inventorySlot, 55 + col * 18, 122 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 55 + col * 18, 180));
        }
    }

    public void updateOffers(TradeViewPayload view) {
        update(ownOffer, view.ownOffers());
        update(peerOffer, view.peerOffers());
    }

    private static void update(SimpleContainer container, List<ItemStack> stacks) {
        for (int i = 0; i < OFFER_SLOTS; i++) {
            container.setItem(i, i < stacks.size() ? stacks.get(i).copy() : ItemStack.EMPTY);
        }
    }

    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        // This menu is never opened on the server. TradeOfferScreen sends bounded intent packets instead.
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }
}
