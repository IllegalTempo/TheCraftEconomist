package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintDraftTest {
    @Test
    void adds_replaces_and_removes_private_blocks() {
        BlueprintDraft draft = new BlueprintDraft();
        BlockPos position = new BlockPos(4, 70, -2);
        draft.put(position, new BlueprintBlockSnapshot("minecraft:stone", ""));
        draft.put(position, new BlueprintBlockSnapshot("minecraft:oak_stairs", "facing=east,half=bottom"));

        assertEquals("minecraft:oak_stairs", draft.blocks().get(position).blockId());
        assertEquals(new BlueprintBlockSnapshot("minecraft:oak_stairs", "facing=east,half=bottom"),
                draft.remove(position));
        assertTrue(draft.isEmpty());
    }

    @Test
    void normalizes_negative_world_coordinates() {
        BlueprintDraft draft = new BlueprintDraft();
        draft.put(new BlockPos(-5, 63, -8), new BlueprintBlockSnapshot("minecraft:stone", ""));
        draft.put(new BlockPos(-3, 65, -7), new BlueprintBlockSnapshot("minecraft:oak_slab", "type=top"));

        BlueprintDesign design = draft.normalize();

        assertEquals(3, design.width());
        assertEquals(3, design.height());
        assertEquals(2, design.depth());
        assertTrue(design.blocks().contains(new BlueprintBlock(0, 0, 0, "minecraft:stone", "")));
        assertTrue(design.blocks().contains(new BlueprintBlock(2, 2, 1, "minecraft:oak_slab", "type=top")));
    }

    @Test
    void refuses_to_normalize_an_empty_draft() {
        assertThrows(IllegalStateException.class, () -> new BlueprintDraft().normalize());
    }
}
