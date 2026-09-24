package com.jedts.theeconomist.citizen.action;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.util.context.ContextMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One live recipe resolution. Plans are recomputed from the active recipe manager and are not saved. */
public record RecipeCraftingPlan(CraftingRecipe recipe, int width, int height,
                                 boolean requiresCraftingTable, List<Ingredient> ingredients,
                                 List<Integer> ingredientSlots) {
    public RecipeCraftingPlan {
        ingredients = List.copyOf(ingredients);
        ingredientSlots = List.copyOf(ingredientSlots);
        if (ingredients.size() != ingredientSlots.size())
            throw new IllegalArgumentException("ingredient slots must match ingredients");
    }

    public static Optional<RecipeCraftingPlan> resolve(ServerLevel level, Item requested) {
        return resolveAll(level, requested).stream().findFirst();
    }

    public static List<RecipeCraftingPlan> resolveAll(ServerLevel level, Item requested) {
        List<RecipeCraftingPlan> plans = new ArrayList<>();
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe) || recipe.isSpecial()) continue;
            ItemStack result = recipe.display().stream()
                    .map(display -> display.result().resolveForFirstStack(ContextMap.EMPTY))
                    .filter(stack -> !stack.isEmpty())
                    .findFirst().orElse(ItemStack.EMPTY);
            if (!result.is(requested)) continue;
            var placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace()) continue;
            List<Ingredient> ingredients = new ArrayList<>();
            List<Integer> ingredientSlots = new ArrayList<>();
            int width;
            int height;
            if (recipe instanceof ShapedRecipe shaped) {
                width = shaped.getWidth();
                height = shaped.getHeight();
                List<java.util.Optional<Ingredient>> pattern = shaped.getIngredients();
                for (int index = 0; index < pattern.size(); index++) {
                    if (pattern.get(index).isEmpty()) continue;
                    ingredients.add(pattern.get(index).orElseThrow());
                    int x = index % width;
                    int y = index / width;
                    ingredientSlots.add(y * 3 + x);
                }
            } else {
                ingredients.addAll(placement.ingredients());
                width = Math.min(3, ingredients.size());
                height = Math.max(1, (ingredients.size() + 2) / 3);
                for (int i = 0; i < ingredients.size(); i++) ingredientSlots.add(i);
            }
            if (width <= 0 || height <= 0 || width > 3 || height > 3 || ingredients.size() > 9) continue;
            plans.add(new RecipeCraftingPlan(recipe, width, height,
                    recipe instanceof ShapedRecipe ? width > 2 || height > 2 : ingredients.size() > 4,
                    ingredients, ingredientSlots));
        }
        return List.copyOf(plans);
    }

    /** Builds a 3x3 input in recipe order and rejects any candidate assignment that does not match. */
    public Optional<CraftingInput> input(List<ItemStack> selectedItems, ServerLevel level) {
        if (selectedItems.size() != ingredients.size()) return Optional.empty();
        List<ItemStack> slots = grid(selectedItems);
        CraftingInput input = CraftingInput.of(3, 3, slots);
        return recipe.matches(input, level) ? Optional.of(input) : Optional.empty();
    }

    public List<ItemStack> grid(List<ItemStack> selectedItems) {
        if (selectedItems.size() != ingredients.size()) return List.of();
        List<ItemStack> slots = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        for (int ingredientIndex = 0; ingredientIndex < selectedItems.size(); ingredientIndex++) {
            int slot = ingredientSlots.get(ingredientIndex);
            if (slot < 0 || slot >= slots.size()) return List.of();
            slots.set(slot, selectedItems.get(ingredientIndex).copyWithCount(1));
        }
        return List.copyOf(slots);
    }
}
