package com.jedts.theeconomist.citizen.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CitizenTargetMemoryTest {
    @Test
    void rememberedTargetsAreCopiedAndHiddenOutsideTheirDimension() {
        CitizenTargetMemory memory = new CitizenTargetMemory();
        BlockPos.MutableBlockPos source = new BlockPos.MutableBlockPos(2, 64, -3);
        memory.rememberLocalTarget("farm", source, "minecraft:overworld");

        source.set(9, 80, 12);

        assertEquals(new BlockPos(2, 64, -3),
                memory.localTargetLocation("farm", "minecraft:overworld").orElseThrow());
        assertTrue(memory.localTargetLocation("farm", "minecraft:the_nether").isEmpty());
    }

    @Test
    void forgettingTargetAlsoForgetsItsDimension() {
        CitizenTargetMemory memory = new CitizenTargetMemory();
        memory.rememberLocalTarget("wood", new BlockPos(1, 2, 3), "minecraft:overworld");

        memory.forgetLocalTarget("wood");

        assertTrue(memory.localTargetLocation("wood", "minecraft:overworld").isEmpty());
    }

    @Test
    void searchCursorIsClampedAndDimensionIsStoredBySearchId() {
        CitizenTargetMemory memory = new CitizenTargetMemory();
        memory.targetSearchCursor("logs", -20);
        memory.targetSearchDimension("logs", "minecraft:overworld");

        assertEquals(0, memory.targetSearchCursor("logs"));
        assertEquals("minecraft:overworld", memory.targetSearchDimension("logs").orElseThrow());
    }

    @Test
    void serializationKeepsExistingListKeysAndRoundTripsMemory() {
        CitizenTargetMemory memory = new CitizenTargetMemory();
        memory.rememberLocalTarget("farm", new BlockPos(2, 64, -3), "minecraft:overworld");
        memory.targetSearchCursor("logs", 8);
        memory.targetSearchDimension("logs", "minecraft:overworld");
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);

        memory.write(output);

        CompoundTag saved = output.buildResult();
        assertTrue(saved.contains("TargetLocations"));
        assertTrue(saved.contains("TargetSearchCursors"));
        assertTrue(saved.contains("TargetSearchDimensions"));
        CitizenTargetMemory restored = new CitizenTargetMemory();
        restored.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, saved));
        assertEquals(new BlockPos(2, 64, -3),
                restored.localTargetLocation("farm", "minecraft:overworld").orElseThrow());
        assertEquals(8, restored.targetSearchCursor("logs"));
        assertEquals("minecraft:overworld", restored.targetSearchDimension("logs").orElseThrow());
    }

    @Test
    void absentSavedListsRestoreEmptyMemory() {
        CitizenTargetMemory memory = new CitizenTargetMemory();
        memory.rememberLocalTarget("farm", new BlockPos(2, 64, -3), "minecraft:overworld");
        memory.targetSearchCursor("logs", 8);

        memory.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, new CompoundTag()));

        assertTrue(memory.localTargetLocation("farm", "minecraft:overworld").isEmpty());
        assertEquals(0, memory.targetSearchCursor("logs"));
        assertTrue(memory.targetSearchDimension("logs").isEmpty());
    }
}
