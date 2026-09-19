package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.TheEconomistMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class CitizenEntities {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "citizen");
    public static EntityType<CitizenEntity> CITIZEN;
    public static Item CITIZEN_SPAWN_EGG;

    private CitizenEntities() {
    }

    public static synchronized void register() {
        if (CITIZEN != null) {
            return;
        }
        ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, ID);
        CITIZEN = Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey,
                EntityType.Builder.of(CitizenEntity::new, MobCategory.CREATURE)
                        .sized(0.6f, 1.95f)
                        .clientTrackingRange(8)
                        .build(entityKey));

        Identifier eggId = Identifier.fromNamespaceAndPath(TheEconomistMod.MOD_ID, "citizen_spawn_egg");
        ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, eggId);
        CITIZEN_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, eggKey,
                new SpawnEggItem(new Item.Properties().setId(eggKey).spawnEgg(CITIZEN).stacksTo(64)));
    }
}
