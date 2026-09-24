package com.jedts.theeconomist.blueprint;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class BlueprintItems {
    public static Item EMPTY_BLUEPRINT;

    private BlueprintItems() { }

    public static synchronized void register() {
        if (EMPTY_BLUEPRINT != null) return;
        Identifier id = Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "empty_blueprint");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        EMPTY_BLUEPRINT = Registry.register(BuiltInRegistries.ITEM, key,
                new EmptyBlueprintItem(new Item.Properties().setId(key).stacksTo(1)));
    }
}
