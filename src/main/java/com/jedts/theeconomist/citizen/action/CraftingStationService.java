package com.jedts.theeconomist.citizen.action;

import com.jedts.theeconomist.citizen.action.resource.CitizenResourceProvider;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.house.HouseholdStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;

/** Ensures a Citizen is beside a usable crafting table near its household chest. */
public final class CraftingStationService {
    private final List<CitizenResourceProvider> providers;
    private final CraftItemAction tableCraft;
    private String status = "Finding crafting table";

    public CraftingStationService(List<CitizenResourceProvider> providers) {
        this.providers = List.copyOf(providers);
        this.tableCraft = new CraftItemAction(Items.CRAFTING_TABLE, 1, this.providers, false);
    }

    public CitizenItemActionResult tick(CitizenBehaviorContext context) {
        BlockPos table = findTable(context).orElse(null);
        if (table != null) return approachTable(context, table);

        CitizenFarmInventory inventory = context.citizen().farmInventory();
        Container chest = HouseholdStorage.resolve(context).orElse(null);
        if (inventory.count(Items.CRAFTING_TABLE) == 0
                && (chest == null || HouseholdStorage.count(chest, Items.CRAFTING_TABLE) == 0)) {
            CitizenItemActionResult crafting = tableCraft.tick(context);
            if (crafting.state() == CitizenItemActionResult.State.UNAVAILABLE
                    || crafting.state() == CitizenItemActionResult.State.FAILED) return result(crafting);
            if (crafting.state() != CitizenItemActionResult.State.COMPLETE)
                return result(CitizenItemActionResult.running("Crafting table: " + crafting.status()));
        }

        if (chest != null) {
            HouseholdStorage.Access access = HouseholdStorage.approach(context, 1.0);
            if (access == HouseholdStorage.Access.WALKING)
                return result(CitizenItemActionResult.running("Returning to home chest for crafting table"));
            if (access != HouseholdStorage.Access.READY)
                return result(CitizenItemActionResult.unavailable("Home chest unreachable"));
        }

        BlockPos site = tableSite(context).orElse(null);
        if (site == null) return result(CitizenItemActionResult.unavailable("No safe crafting table site at home"));
        if (inventory.count(Items.CRAFTING_TABLE) == 0 && chest != null) {
            chest = HouseholdStorage.resolve(context).orElse(null);
            if (chest == null) return result(CitizenItemActionResult.unavailable("Home chest unavailable"));
            ItemStack tableStack = HouseholdStorage.extract(chest, Items.CRAFTING_TABLE, 1);
            if (tableStack.isEmpty()) return result(CitizenItemActionResult.running("Waiting for crafting table in home chest"));
            ItemStack remainder = inventory.insert(tableStack);
            if (!remainder.isEmpty()) {
                HouseholdStorage.insert(chest, remainder);
                return result(CitizenItemActionResult.unavailable("Citizen inventory is full"));
            }
        }

        double dx = context.citizen().getX() - (site.getX() + 0.5);
        double dz = context.citizen().getZ() - (site.getZ() + 0.5);
        if (dx * dx + dz * dz > 4.0) {
            if ((!context.navigation().isInProgress() || context.navigation().isDone())
                    && !context.navigation().moveTo(site.getX() + 0.5, site.getY(), site.getZ() + 0.5, 1.0))
                return result(CitizenItemActionResult.unavailable("Crafting table site unreachable"));
            return result(CitizenItemActionResult.running("Carrying crafting table to home station"));
        }
        if (!context.level().getBlockState(site).isAir() || !context.level().getBlockState(site.above()).isAir()
                || !context.level().getBlockState(site.below()).isFaceSturdy(context.level(), site.below(), Direction.UP))
            return result(CitizenItemActionResult.running("Crafting table site changed"));
        if (!context.level().setBlock(site, Blocks.CRAFTING_TABLE.defaultBlockState(), 3))
            return result(CitizenItemActionResult.running("Could not place crafting table"));
        inventory.remove(Items.CRAFTING_TABLE, 1);
        context.navigation().stop();
        return result(CitizenItemActionResult.running("Placed crafting table"));
    }

    public String status() { return status; }

    public void stop(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context) {
        tableCraft.stop(context);
        context.navigation().stop();
    }

    private Optional<BlockPos> findTable(CitizenBehaviorContext context) {
        BlockPos center = HouseholdStorage.position(context).or(() -> context.home())
                .orElseGet(() -> context.citizen().blockPosition());
        if (center == null) return Optional.empty();
        for (int y = -2; y <= 2; y++) for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            BlockPos pos = center.offset(dx, y, dz);
            if (context.level().hasChunkAt(pos) && context.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE))
                return Optional.of(pos);
        }
        return Optional.empty();
    }

    private Optional<BlockPos> tableSite(CitizenBehaviorContext context) {
        BlockPos chest = HouseholdStorage.position(context).or(() -> context.home())
                .orElseGet(() -> context.citizen().blockPosition());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = chest.relative(direction);
            if (!context.level().hasChunkAt(pos) || !context.level().hasChunkAt(pos.above())
                    || !context.level().hasChunkAt(pos.below())) continue;
            if (context.level().getBlockState(pos).isAir() && context.level().getBlockState(pos.above()).isAir()
                    && context.level().getBlockState(pos.below()).isFaceSturdy(context.level(), pos.below(), Direction.UP))
                return Optional.of(pos);
        }
        return Optional.empty();
    }

    private CitizenItemActionResult approachTable(CitizenBehaviorContext context, BlockPos table) {
        double dx = context.citizen().getX() - (table.getX() + 0.5);
        double dz = context.citizen().getZ() - (table.getZ() + 0.5);
        if (dx * dx + dz * dz <= 4.0)
            return result(CitizenItemActionResult.complete("At crafting table"));
        if ((!context.navigation().isInProgress() || context.navigation().isDone())
                && !context.navigation().moveTo(table.getX() + 0.5, table.getY(), table.getZ() + 0.5, 1.0))
            return result(CitizenItemActionResult.unavailable("Crafting table unreachable"));
        return result(CitizenItemActionResult.running("Walking to crafting table"));
    }

    private CitizenItemActionResult result(CitizenItemActionResult value) {
        status = value.status();
        return value;
    }
}
