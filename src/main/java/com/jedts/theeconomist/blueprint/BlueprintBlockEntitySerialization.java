package com.jedts.theeconomist.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Consumer;

/** Rejects codec problems rather than accepting Minecraft's partial NBT result. */
final class BlueprintBlockEntitySerialization {
    private BlueprintBlockEntitySerialization() { }

    static CompoundTag save(HolderLookup.Provider registries, Consumer<ValueOutput> writer) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, registries);
        writer.accept(output);
        if (!problems.isEmpty()) throw new IllegalStateException("block entity serialization failed");
        return output.buildResult();
    }
}
