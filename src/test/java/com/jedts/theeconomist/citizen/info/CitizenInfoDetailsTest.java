package com.jedts.theeconomist.citizen.info;

import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.farm.claim.PlotClaims;
import com.jedts.theeconomist.citizen.farm.claim.PlotOwner;
import com.jedts.theeconomist.citizen.trade.CitizenPriceSnapshot;
import com.jedts.theeconomist.citizen.trade.CoinDenominations;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenInfoDetailsTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var components = DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
        for (Item item : List.of(Items.WHEAT, Items.WOODEN_HOE, Items.COPPER_INGOT,
                Items.IRON_INGOT, Items.GOLD_INGOT)) item.builtInRegistryHolder().bindComponents(components);
    }

    @Test void snapshotIncludesPhysicalInventoryLandAndPrices() {
        UUID citizenId = UUID.randomUUID();
        CitizenFarmInventory inventory = new CitizenFarmInventory();
        inventory.insert(new ItemStack(Items.WHEAT, 9));
        inventory.insert(new ItemStack(Items.COPPER_INGOT, 3));
        inventory.insert(new ItemStack(Items.IRON_INGOT, 1));
        PlotClaims claims = new PlotClaims();
        claims.claimNewFarmland(new BlockPos(0, 64, 0), new PlotOwner(PlotOwner.Kind.CITIZEN, citizenId));
        claims.claimNewFarmland(new BlockPos(1, 64, 0), new PlotOwner(PlotOwner.Kind.CITIZEN, citizenId));
        claims.claimNewFarmland(new BlockPos(2, 64, 0), new PlotOwner(PlotOwner.Kind.PLAYER, UUID.randomUUID()));
        var details = CitizenInfoDetails.from(inventory, claims, citizenId,
                new CoinDenominations(Items.COPPER_INGOT, Items.IRON_INGOT, Items.GOLD_INGOT),
                new CitizenPriceSnapshot(6000, 1, 5, 1, 8), "Harvesting wheat", "0, 64, 0");
        assertEquals(27, details.inventory().size());
        assertEquals(2, details.claimedFarmland());
        assertEquals(5, details.sellableWheat());
        assertEquals(13, details.crownBalance());
        assertEquals(5, details.wheatPrice());
        assertEquals("Harvesting wheat", details.farmStatus());
        assertEquals("0, 64, 0", details.home());
    }
}
