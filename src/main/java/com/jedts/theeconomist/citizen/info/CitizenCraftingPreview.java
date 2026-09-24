package com.jedts.theeconomist.citizen.info;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Immutable snapshot of the crafting grid and output currently selected by a Citizen. */
public record CitizenCraftingPreview(List<ItemStack> ingredients, ItemStack result) {
    public CitizenCraftingPreview {
        if (ingredients.size() != 9) throw new IllegalArgumentException("crafting preview must contain nine slots");
        ingredients = ingredients.stream().map(ItemStack::copy).toList();
        result = result.copy();
    }

    @Override public List<ItemStack> ingredients() { return ingredients.stream().map(ItemStack::copy).toList(); }
    @Override public ItemStack result() { return result.copy(); }
}
