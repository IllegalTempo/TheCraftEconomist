package com.jedts.theeconomist.currency;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public final class CrownItems {
    private static final Map<CrownDenomination, Item> ITEMS = new EnumMap<>(CrownDenomination.class);
    public static CreativeModeTab CURRENCY_TAB;

    private CrownItems() {
    }

    public static synchronized void register() {
        if (!ITEMS.isEmpty()) {
            return;
        }
        for (CrownDenomination denomination : CrownDenomination.values()) {
            Identifier id = Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, denomination.path());
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            Item item = new CrownItem(denomination, new Item.Properties().setId(key).stacksTo(64));
            ITEMS.put(denomination, Registry.register(BuiltInRegistries.ITEM, key, item));
        }
        Identifier tabId = Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "currency");
        ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, tabId);
        CURRENCY_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey,
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .title(Component.translatable("itemGroup.theeconomist.currency"))
                        .icon(() -> new ItemStack(item(CrownDenomination.GOLD)))
                        .displayItems((parameters, output) -> {
                            for (CrownDenomination denomination : CrownDenomination.values()) {
                                output.accept(item(denomination));
                            }
                        })
                        .build());
    }

    public static Item item(CrownDenomination denomination) {
        Item item = ITEMS.get(denomination);
        if (item == null) {
            throw new IllegalStateException("Crown items have not been registered");
        }
        return item;
    }
}
