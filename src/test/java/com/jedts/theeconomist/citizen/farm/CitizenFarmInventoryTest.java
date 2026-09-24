package com.jedts.theeconomist.citizen.farm;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitizenFarmInventoryTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var stackable = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        Items.WHEAT.builtInRegistryHolder().bindComponents(stackable);
        Items.COBBLESTONE.builtInRegistryHolder().bindComponents(stackable);
    }

    @Test void wheatReserveIsNotForSale() {
        CitizenFarmInventory inventory = new CitizenFarmInventory();
        assertTrue(inventory.insert(new ItemStack(Items.WHEAT, 8)).isEmpty());
        assertEquals(8, inventory.count(Items.WHEAT));
        assertEquals(4, inventory.sellableWheat());
        assertEquals(5, inventory.remove(Items.WHEAT, 5).getCount());
        assertEquals(0, inventory.sellableWheat());
    }

    @Test void fullInventoryReturnsUninsertedItems() {
        CitizenFarmInventory inventory = new CitizenFarmInventory();
        for (int slot = 0; slot < 27; slot++)
            assertTrue(inventory.insert(new ItemStack(Items.COBBLESTONE, 64)).isEmpty());
        ItemStack remainder = inventory.insert(new ItemStack(Items.WHEAT, 3));
        assertEquals(3, remainder.getCount());
        assertEquals(0, inventory.count(Items.WHEAT));
    }

    @Test void insertionCopiesTheSuppliedStack() {
        CitizenFarmInventory inventory = new CitizenFarmInventory();
        ItemStack supplied = new ItemStack(Items.WHEAT, 2);
        inventory.insert(supplied);
        supplied.setCount(1);
        assertEquals(2, inventory.count(Items.WHEAT));
    }
}
