package com.jedts.theeconomist.citizen.action;

import com.jedts.theeconomist.citizen.action.resource.CitizenResourceProvider;
import com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext;
import com.jedts.theeconomist.citizen.farm.CitizenFarmInventory;
import com.jedts.theeconomist.citizen.house.HouseholdStorage;
import com.jedts.theeconomist.citizen.house.HouseholdWorkClaims;
import com.jedts.theeconomist.citizen.info.CitizenCraftingPreview;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Crafts a requested output, recursively finding or crafting any missing inputs. */
public final class CraftItemAction implements CitizenItemAction {
    private static final int MAX_INPUT_COMBINATIONS = 4096;
    private static final int MAX_RECIPE_DEPTH = 16;
    private final Item item;
    private final int quantity;
    private final List<CitizenResourceProvider> providers;
    private final Set<Item> ancestry;
    private final int depth;
    private final CraftingStationService stationService;
    private CitizenItemAction childAction;
    private CitizenCraftingPreview craftingPreview;
    private String status;
    private boolean gatherOnly;

    public CraftItemAction(Item item, int quantity, List<CitizenResourceProvider> providers) {
        this(item, quantity, providers, Set.of(item), 0, true);
    }

    CraftItemAction(Item item, int quantity, List<CitizenResourceProvider> providers, boolean ensureCraftingStation) {
        this(item, quantity, providers, Set.of(item), 0, ensureCraftingStation);
    }

    private CraftItemAction(Item item, int quantity, List<CitizenResourceProvider> providers, Set<Item> ancestry, int depth,
                            boolean ensureCraftingStation) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.item = java.util.Objects.requireNonNull(item, "item");
        this.quantity = quantity;
        this.providers = List.copyOf(providers);
        this.ancestry = Set.copyOf(ancestry);
        this.depth = depth;
        this.stationService = ensureCraftingStation ? new CraftingStationService(this.providers) : null;
        this.status = "Crafting " + displayName();
    }

    @Override public CitizenItemActionResult tick(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context) {
        CitizenFarmInventory carried = context.citizen().farmInventory();
        Container chest = context.householdId().isPresent() ? HouseholdStorage.resolve(context).orElse(null) : null;
        if (context.householdId().isPresent() && chest == null)
            return set(CitizenItemActionResult.unavailable("Home chest unavailable"));
        int stored = chest == null ? carried.count(item) : HouseholdStorage.count(chest, item);
        if (stored >= quantity) {
            craftingPreview = null;
            return set(CitizenItemActionResult.complete("Crafted " + displayName()));
        }
        if (chest != null && carried.count(item) > 0) {
            if (childAction == null) childAction = new FindItemAction(item, quantity, providers);
            CitizenItemActionResult store = childAction.tick(context);
            if (store.state() == CitizenItemActionResult.State.COMPLETE) childAction = null;
            else return set(CitizenItemActionResult.running("Storing " + displayName() + ": " + store.status()));
            return set(CitizenItemActionResult.running("Stored " + displayName()));
        }
        if (!(context.level() instanceof ServerLevel level))
            return set(CitizenItemActionResult.unavailable("Crafting is only available in the server world"));
        List<RecipeCraftingPlan> plans = RecipeCraftingPlan.resolveAll(level, item);
        if (plans.isEmpty()) {
            craftingPreview = null;
            return set(CitizenItemActionResult.unavailable("No usable recipe for " + displayName()));
        }

        RecipeCraftingPlan plan = null;
        List<ItemStack> selected = null;
        for (RecipeCraftingPlan candidate : plans) {
            Optional<List<ItemStack>> inputs = chooseInputs(context, chest, candidate, level);
            if (inputs.isPresent()) {
                plan = candidate;
                selected = inputs.orElseThrow();
                break;
            }
        }
        if (plan == null) {
            craftingPreview = null;
            return set(CitizenItemActionResult.unavailable("No available materials for " + displayName()));
        }
        ItemStack previewResult = new ItemStack(item);
        try {
            ItemStack assembledPreview = plan.recipe().assemble(plan.input(selected, level).orElseThrow());
            if (!assembledPreview.isEmpty()) previewResult = assembledPreview;
        } catch (RuntimeException ignored) {
            // The action will report the assembly failure when it reaches the crafting step.
        }
        craftingPreview = new CitizenCraftingPreview(plan.grid(selected), previewResult);

        java.util.Map<Item, Integer> requiredItems = new java.util.LinkedHashMap<>();
        for (ItemStack ingredient : selected) if (!ingredient.isEmpty())
            requiredItems.merge(ingredient.getItem(), 1, Integer::sum);
        for (var requirement : requiredItems.entrySet()) {
            int available = chest == null ? carried.count(requirement.getKey()) : HouseholdStorage.count(chest, requirement.getKey());
            if (available >= requirement.getValue()) continue;
            if (childAction == null) childAction = createSupplyAction(context, requirement.getKey(), requirement.getValue());
            if (childAction == null)
                return set(CitizenItemActionResult.unavailable("No source or recipe for " + new ItemStack(requirement.getKey()).getHoverName().getString()));
            CitizenItemActionResult supply = childAction.tick(context);
            if (supply.state() == CitizenItemActionResult.State.COMPLETE) {
                childAction = null;
                return set(CitizenItemActionResult.running("Collected " + new ItemStack(requirement.getKey()).getHoverName().getString()
                        + " for " + displayName()));
            }
            if (supply.state() == CitizenItemActionResult.State.UNAVAILABLE
                    || supply.state() == CitizenItemActionResult.State.FAILED) return set(supply);
            return set(CitizenItemActionResult.running(supply.status()));
        }

        // Gather all missing recipe inputs before requiring a crafting station.
        // Otherwise a citizen leaves the nearby resource search to walk back to
        // the table on every tick, then resumes gathering on the next tick.
        if (plan.requiresCraftingTable()) {
            if (stationService == null)
                return set(CitizenItemActionResult.unavailable("Cannot craft a crafting table that requires itself"));
            CitizenItemActionResult station = stationService.tick(context);
            if (station.state() == CitizenItemActionResult.State.UNAVAILABLE
                    || station.state() == CitizenItemActionResult.State.FAILED) return set(station);
            if (station.state() != CitizenItemActionResult.State.COMPLETE)
                return set(CitizenItemActionResult.running(station.status()));
        }

        if (chest != null) {
            HouseholdStorage.Access access = HouseholdStorage.approach(context, 1.0);
            if (access == HouseholdStorage.Access.WALKING)
                return set(CitizenItemActionResult.running("Returning ingredients to home crafting area"));
            if (access != HouseholdStorage.Access.READY)
                return set(CitizenItemActionResult.unavailable("Home chest unreachable"));
            chest = HouseholdStorage.resolve(context).orElse(null);
            if (chest == null) return set(CitizenItemActionResult.unavailable("Home chest unavailable"));
        }

        if (gatherOnly)
            return set(CitizenItemActionResult.running("Supplies ready for " + displayName()));

        var householdId = context.householdId().orElse(null);
        boolean claimed = householdId != null && HouseholdWorkClaims.tryClaim(householdId,
                "craft." + BuiltInRegistries.ITEM.getKey(item), context.citizen().getUUID(), context.gameTime());
        if (householdId != null && !claimed)
            return set(CitizenItemActionResult.running("Waiting for a family member to craft " + displayName()));
        try {
            // Another family member may have completed this output since this action
            // last checked the shared chest. Don't craft an additional copy.
            int currentOutput = chest == null ? carried.count(item) : HouseholdStorage.count(chest, item);
            if (currentOutput >= quantity)
                return set(CitizenItemActionResult.complete("Crafted " + displayName()));
            return craftSelected(context, level, plan, selected, chest, carried);
        } finally {
            if (claimed) HouseholdWorkClaims.release(householdId, "craft." + BuiltInRegistries.ITEM.getKey(item),
                    context.citizen().getUUID());
        }
    }

    /** Gathers missing recipe inputs without crafting this action's requested output. */
    public CitizenItemActionResult tickGatherOnly(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context) {
        boolean previous = gatherOnly;
        gatherOnly = true;
        try {
            return tick(context);
        } finally {
            gatherOnly = previous;
        }
    }

    private CitizenItemActionResult craftSelected(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context,
                                                   ServerLevel level, RecipeCraftingPlan plan,
                                                   List<ItemStack> selected, Container chest,
                                                   CitizenFarmInventory carried) {

        CraftingInput input = plan.input(selected, level).orElse(null);
        if (input == null) return set(CitizenItemActionResult.unavailable("Available materials do not match the recipe"));
        ItemStack output;
        List<ItemStack> remainders;
        try {
            output = plan.recipe().assemble(input);
            if (output.isEmpty()) return set(CitizenItemActionResult.unavailable("Recipe produced no " + displayName()));
            remainders = List.copyOf(plan.recipe().getRemainingItems(input));
        } catch (RuntimeException exception) {
            return set(CitizenItemActionResult.failed("Could not assemble " + displayName()));
        }
        List<ItemStack> products = new ArrayList<>();
        products.add(output);
        remainders.stream().filter(stack -> !stack.isEmpty()).forEach(products::add);
        if (chest != null) {
            if (!HouseholdStorage.canInsert(chest, products))
                return set(CitizenItemActionResult.unavailable("Home chest has no room for " + displayName()));
        } else {
            CitizenFarmInventory trial = carried.copy();
            for (ItemStack product : products) if (!trial.insert(product).isEmpty())
                return set(CitizenItemActionResult.unavailable("Citizen inventory is full"));
        }

        List<ItemStack> consumed = new ArrayList<>();
        for (ItemStack ingredient : selected) {
            if (ingredient.isEmpty()) continue;
            ItemStack extracted = chest == null ? carried.removeMatching(ingredient, 1)
                    : HouseholdStorage.extract(chest, ingredient, 1);
            if (extracted.isEmpty()) {
                rollback(context, chest, consumed);
                return set(CitizenItemActionResult.running("Ingredients changed; checking supplies again"));
            }
            consumed.add(extracted);
        }
        if (!plan.recipe().matches(input, level)) {
            rollback(context, chest, consumed);
            return set(CitizenItemActionResult.running("Recipe changed; checking materials again"));
        }
        if (chest != null) {
            for (ItemStack product : products) {
                ItemStack remainder = HouseholdStorage.insert(chest, product);
                if (!remainder.isEmpty()) return set(CitizenItemActionResult.failed("Home chest changed during crafting"));
            }
        } else {
            for (ItemStack product : products) {
                ItemStack remainder = carried.insert(product);
                if (!remainder.isEmpty()) return set(CitizenItemActionResult.failed("Citizen inventory changed during crafting"));
            }
        }
        status = "Crafted " + output.getCount() + " " + displayName();
        return set(CitizenItemActionResult.running(status));
    }

    @Override public String status() { return status; }

    @Override public void stop(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context) {
        if (childAction != null) childAction.stop(context);
        childAction = null;
        if (stationService != null) stationService.stop(context);
        craftingPreview = null;
        context.navigation().stop();
    }

    public Optional<CitizenCraftingPreview> craftingPreview() { return Optional.ofNullable(craftingPreview); }

    private Optional<List<ItemStack>> chooseInputs(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context,
                                                    Container chest, RecipeCraftingPlan plan, ServerLevel level) {
        List<List<ItemStack>> choices = new ArrayList<>();
        for (Ingredient ingredient : plan.ingredients()) {
            if (ingredient.isEmpty()) {
                choices.add(List.of(ItemStack.EMPTY));
                continue;
            }
            List<ItemStack> options = ingredient.items().map(holder -> new ItemStack(holder.value()))
                    .filter(stack -> canSupply(context, chest, stack.getItem()))
                    .map(stack -> stack.copyWithCount(1))
                    .sorted(Comparator.comparingInt((ItemStack stack) -> supplyScore(context, chest, stack)).reversed())
                    .toList();
            if (options.isEmpty()) return Optional.empty();
            choices.add(options);
        }
        List<ItemStack> selected = new ArrayList<>(java.util.Collections.nCopies(choices.size(), ItemStack.EMPTY));
        int[] attempts = {0};
        return chooseCombination(context, plan, level, choices, selected, 0, attempts);
    }

    private Optional<List<ItemStack>> chooseCombination(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context,
                                                         RecipeCraftingPlan plan, ServerLevel level,
                                                         List<List<ItemStack>> choices, List<ItemStack> selected,
                                                         int index, int[] attempts) {
        if (index == choices.size()) return plan.input(selected, level).map(ignored -> List.copyOf(selected));
        for (ItemStack candidate : choices.get(index)) {
            if (++attempts[0] > MAX_INPUT_COMBINATIONS) return Optional.empty();
            selected.set(index, candidate);
            Optional<List<ItemStack>> result = chooseCombination(context, plan, level, choices, selected, index + 1, attempts);
            if (result.isPresent()) return result;
        }
        selected.set(index, ItemStack.EMPTY);
        return Optional.empty();
    }

    private boolean canSupply(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context, Container chest, Item candidate) {
        if ((chest != null && HouseholdStorage.count(chest, candidate) > 0)
                || context.citizen().farmInventory().count(candidate) > 0) return true;
        if (providers.stream().anyMatch(provider -> provider.canProvide(candidate))) return true;
        return !ancestry.contains(candidate) && context.level() instanceof ServerLevel level
                && RecipeCraftingPlan.resolve(level, candidate).isPresent();
    }

    private int supplyScore(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context, Container chest, ItemStack candidate) {
        int count = chest == null ? 0 : HouseholdStorage.count(chest, candidate);
        count += context.citizen().farmInventory().count(candidate);
        return count > 0 ? 1000 + count : providers.stream().anyMatch(provider -> provider.canProvide(candidate.getItem()))
                ? 100 : 1;
    }

    private CitizenItemAction createSupplyAction(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context, Item requested, int requiredCount) {
        if (providers.stream().anyMatch(provider -> provider.canProvide(requested)))
            return new FindItemAction(requested, requiredCount, providers);
        if (ancestry.contains(requested) || depth >= MAX_RECIPE_DEPTH || !(context.level() instanceof ServerLevel level)
                || RecipeCraftingPlan.resolve(level, requested).isEmpty()) return null;
        Set<Item> nextAncestry = new HashSet<>(ancestry);
        nextAncestry.add(requested);
        return new CraftItemAction(requested, requiredCount, providers, nextAncestry, depth + 1, true);
    }

    private void rollback(com.jedts.theeconomist.citizen.behavior.CitizenBehaviorContext context,
                          Container chest, List<ItemStack> consumed) {
        for (ItemStack stack : consumed) {
            if (chest == null) context.citizen().farmInventory().insert(stack);
            else HouseholdStorage.insert(chest, stack);
        }
    }

    private String displayName() { return new ItemStack(item).getHoverName().getString(); }

    private CitizenItemActionResult set(CitizenItemActionResult result) {
        status = result.status();
        return result;
    }
}
