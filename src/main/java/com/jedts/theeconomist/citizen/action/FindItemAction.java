package com.jedts.theeconomist.citizen.action;

import com.jedts.theeconomist.citizen.action.resource.CitizenResourceProvider;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetMemoryStore;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetSearchResult;
import com.jedts.theeconomist.citizen.behavior.target.CitizenTargetSearchService;
import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.house.HouseholdStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** Finds enough of one item, gathering missing items and storing them at the household chest. */
public final class FindItemAction implements CitizenItemAction {
    private final Item item;
    private final int quantity;
    private final List<CitizenResourceProvider> providers;
    private final CitizenTargetSearchService targetSearch = new CitizenTargetSearchService();
    private final CitizenTargetMemoryStore targetMemory = new CitizenTargetMemoryStore();
    private CitizenResourceProvider activeProvider;
    private BlockPos target;
    private String status;

    public FindItemAction(Item item, int quantity, List<CitizenResourceProvider> providers) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.item = java.util.Objects.requireNonNull(item, "item");
        this.quantity = quantity;
        this.providers = List.copyOf(providers);
        this.status = "Finding " + displayName();
    }

    @Override public CitizenItemActionResult tick(CitizenBehaviorContext context) {
        CitizenFarmInventory carried = context.citizen().farmInventory();
        Optional<Container> storage = context.householdId().isPresent()
                ? HouseholdStorage.resolve(context) : Optional.empty();
        if (context.householdId().isPresent() && storage.isEmpty())
            return set(CitizenItemActionResult.unavailable("Home chest unavailable"));

        int available = storage.map(container -> HouseholdStorage.count(container, item)).orElseGet(() -> carried.count(item));
        if (available >= quantity) return set(CitizenItemActionResult.complete("Found " + displayName()));

        int carriedCount = carried.count(item);
        if (storage.isPresent() && carriedCount > 0) {
            HouseholdStorage.Access access = HouseholdStorage.approach(context, 1.0);
            if (access == HouseholdStorage.Access.WALKING) return set(CitizenItemActionResult.running("Returning " + displayName() + " to home chest"));
            if (access != HouseholdStorage.Access.READY) return set(CitizenItemActionResult.unavailable("Home chest unreachable"));
            Container chest = HouseholdStorage.resolve(context).orElse(null);
            if (chest == null) return set(CitizenItemActionResult.unavailable("Home chest unavailable"));
            int shortage = quantity - HouseholdStorage.count(chest, item);
            int moved = Math.min(shortage, carriedCount);
            ItemStack remainder = HouseholdStorage.insert(chest, new ItemStack(item, moved));
            int inserted = moved - remainder.getCount();
            if (inserted > 0) carried.remove(item, inserted);
            available = HouseholdStorage.count(chest, item);
            if (available >= quantity) return set(CitizenItemActionResult.complete("Found " + displayName()));
            if (inserted == 0 && carriedCount > 0)
                return set(CitizenItemActionResult.unavailable("Home chest is full"));
        }

        if (available >= quantity) return set(CitizenItemActionResult.complete("Found " + displayName()));
        if (activeProvider == null || !activeProvider.canProvide(item)) {
            activeProvider = providers.stream().filter(provider -> provider.canProvide(item)).findFirst().orElse(null);
            target = null;
        }
        if (activeProvider == null)
            return set(CitizenItemActionResult.unavailable("No source for " + displayName()));

        String targetId = targetId(activeProvider);
        if (target == null) {
            var search = targetSearch.tick(context, targetId, activeProvider.targetFinder(item), 1.0);
            if (search.state() != CitizenTargetSearchResult.State.FOUND || search.location().isEmpty())
                return set(CitizenItemActionResult.running(searchStatus(search.state())));
            target = search.location().orElseThrow().immutable();
        }

        // Keep noticing resources as they come into view while traveling to the
        // selected source; nearby material may be closer than the original target.
        var visibleTarget = activeProvider.targetFinder(item).findNear(
                context, context.citizen().blockPosition(), 8);
        if (visibleTarget.isPresent() && !visibleTarget.orElseThrow().equals(target)) {
            target = visibleTarget.orElseThrow().immutable();
            targetMemory.remember(context, targetId, target);
            context.navigation().stop();
        }

        Optional<BlockPos> interaction = activeProvider.interactionPosition(context, target);
        if (interaction.isEmpty()) {
            invalidateTarget(context, targetId);
            return set(CitizenItemActionResult.running("Resource target changed; searching again"));
        }
        BlockPos feet = interaction.orElseThrow();
        double dx = context.citizen().getX() - (feet.getX() + 0.5);
        double dy = context.citizen().getY() - feet.getY();
        double dz = context.citizen().getZ() - (feet.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > 4.0) {
            status = "Walking to " + displayName() + " source";
            if ((!context.navigation().isInProgress() || context.navigation().isDone())
                    && !context.navigation().moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 1.0)) {
                invalidateTarget(context, targetId);
                return set(CitizenItemActionResult.running("Resource path blocked; searching again"));
            }
            return set(CitizenItemActionResult.running(status));
        }

        CitizenResourceProvider.GatherResult gathered = activeProvider.gather(context, target);
        if (gathered == CitizenResourceProvider.GatherResult.INVENTORY_FULL) {
            return set(CitizenItemActionResult.unavailable("Citizen inventory is full"));
        }
        if (gathered == CitizenResourceProvider.GatherResult.TARGET_INVALID) {
            invalidateTarget(context, targetId);
            return set(CitizenItemActionResult.running("Resource unavailable; searching again"));
        }
        context.navigation().stop();
        targetMemory.forget(context, targetId);
        target = null;
        status = "Gathered " + displayName();
        return set(CitizenItemActionResult.running(status));
    }

    @Override public String status() { return status; }

    @Override public void stop(CitizenBehaviorContext context) {
        context.navigation().stop();
        target = null;
        activeProvider = null;
    }

    private void invalidateTarget(CitizenBehaviorContext context, String targetId) {
        targetMemory.forget(context, targetId);
        context.navigation().stop();
        target = null;
    }

    private String targetId(CitizenResourceProvider provider) {
        return "resource." + provider.id() + "." + BuiltInRegistries.ITEM.getKey(item);
    }

    private String displayName() { return new ItemStack(item).getHoverName().getString(); }

    private String searchStatus(CitizenTargetSearchResult.State state) {
        return switch (state) {
            case WALKING_TO_MEMORY, WALKING_SEARCH_ROUTE -> "Searching for " + displayName();
            case SEARCHING -> "Searching for " + displayName();
            case FOUND -> "Walking to " + displayName() + " source";
        };
    }

    private CitizenItemActionResult set(CitizenItemActionResult result) {
        status = result.status();
        return result;
    }
}
