package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintBlockSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Optional;
import java.util.StringJoiner;

public final class BlueprintBlockStateCodec {
    private BlueprintBlockStateCodec() { }

    public static BlueprintBlockSnapshot encode(BlockState state) {
        StringJoiner properties = new StringJoiner(",");
        for (Property<?> property : state.getProperties()) properties.add(encodeProperty(state, property));
        return new BlueprintBlockSnapshot(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                properties.toString());
    }

    public static Optional<BlockState> decode(BlueprintBlockSnapshot snapshot) {
        Identifier id = Identifier.tryParse(snapshot.blockId());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return Optional.empty();
        BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        for (String encoded : snapshot.stateProperties().split(",")) {
            if (encoded.isBlank()) continue;
            String[] pair = encoded.split("=", 2);
            if (pair.length != 2) throw new IllegalArgumentException("invalid block state property: " + encoded);
            state = apply(state, pair[0], pair[1]);
        }
        return Optional.of(state);
    }

    @SuppressWarnings("unchecked")
    private static String encodeProperty(BlockState state, Property<?> property) {
        return encodeTypedProperty(state, (Property<? extends Comparable>) property);
    }

    private static <T extends Comparable<T>> String encodeTypedProperty(BlockState state, Property<T> property) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }

    @SuppressWarnings("unchecked")
    private static BlockState apply(BlockState state, String name, String value) {
        Property<? extends Comparable<?>> raw = state.getBlock().getStateDefinition().getProperty(name);
        if (raw == null) throw new IllegalArgumentException("unknown block state property: " + name);
        return applyTyped(state, (Property) raw, value);
    }

    private static <T extends Comparable<T>> BlockState applyTyped(BlockState state, Property<T> property,
                                                                   String value) {
        T parsed = property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("invalid block state value: " + value));
        return state.setValue(property, parsed);
    }
}
