package com.jedts.theeconomist.citizen.trade;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class TradeInventoryTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.WHEAT.builtInRegistryHolder().bindComponents(
                DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    @Test void failedInsertionDoesNotMutateOriginalSnapshot() {
        var full = new ArrayList<ItemStack>(Collections.nCopies(2, ItemStack.EMPTY));
        full.set(0, new ItemStack(Items.WHEAT, 64));
        full.set(1, new ItemStack(Items.WHEAT, 64));
        TradeInventory inventory = new TradeInventory(full);
        assertFalse(inventory.insert(new ItemStack(Items.WHEAT, 1)));
        assertEquals(128, inventory.count(Items.WHEAT));
        assertEquals(128, full.get(0).getCount() + full.get(1).getCount());
    }

    @Test void copyCanRemoveAndInsertWithoutChangingSource() {
        TradeInventory source = new TradeInventory(java.util.List.of(new ItemStack(Items.WHEAT, 8), ItemStack.EMPTY));
        TradeInventory copy = source.copy();
        assertEquals(3, copy.remove(Items.WHEAT, 3).getCount());
        assertEquals(5, copy.count(Items.WHEAT));
        assertEquals(8, source.count(Items.WHEAT));
    }
}
