package com.jedts.theeconomist.blueprint;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintCaptureTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reversed_corners_capture_inclusive_blocks_and_nested_chest_items() {
        World world = new World();
        world.states.put(new BlockPos(1, 64, 1), Blocks.STONE.defaultBlockState());
        BlockPos chestPos = new BlockPos(2, 64, 2);
        world.states.put(chestPos, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:diamond_sword");
        item.putString("components", "custom enchantments");
        items.add(item);
        chest.put("Items", items);
        world.entities.put(chestPos, chest);

        BlueprintCapture.Result result = BlueprintCapture.capture(chestPos, new BlockPos(1, 64, 1), world);

        assertTrue(result.accepted(), result.reason());
        assertEquals(2, result.design().width());
        assertEquals(1, result.design().height());
        assertEquals(2, result.design().depth());
        assertEquals(2, result.design().blocks().size());
        BlueprintBlock capturedChest = result.design().blocks().stream()
                .filter(b -> b.blockId().equals("minecraft:chest")).findFirst().orElseThrow();
        assertEquals(1, capturedChest.x());
        assertEquals(1, capturedChest.z());
        assertEquals(chest, capturedChest.blockEntityData());
    }

    @Test
    void same_corner_twice_captures_one_block() {
        World world = new World();
        BlockPos pos = new BlockPos(4, 70, 4);
        world.states.put(pos, Blocks.STONE.defaultBlockState());
        BlueprintCapture.Result result = BlueprintCapture.capture(pos, pos, world);
        assertTrue(result.accepted(), result.reason());
        assertEquals(1, result.design().blocks().size());
        assertEquals(1, result.design().width());
    }

    @Test
    void rejects_all_air_and_restricted_or_unloaded_regions() {
        World world = new World();
        BlockPos pos = new BlockPos(4, 70, 4);
        assertFalse(BlueprintCapture.capture(pos, pos, world).accepted());
        world.states.put(pos, Blocks.STONE.defaultBlockState());
        world.loaded = false;
        assertFalse(BlueprintCapture.capture(pos, pos, world).accepted());
        world.loaded = true;
        world.accessible = false;
        assertFalse(BlueprintCapture.capture(pos, pos, world).accepted());
        world.accessible = true;
        world.inBounds = false;
        assertFalse(BlueprintCapture.capture(pos, pos, world).accepted());
    }

    @Test
    void rejects_excess_volume_before_reading_any_world_blocks() {
        World world = new World();
        BlueprintCapture.Result result = BlueprintCapture.capture(BlockPos.ZERO,
                new BlockPos(16, 16, 16), world);
        assertFalse(result.accepted());
        assertNull(result.design());
        assertEquals(0, world.stateReads);
    }

    @Test
    void rejects_oversized_block_entity_without_truncation() {
        World world = new World();
        BlockPos pos = new BlockPos(4, 70, 4);
        world.states.put(pos, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putString("CustomName", "x".repeat(33_000));
        world.entities.put(pos, chest);
        BlueprintCapture.Result result = BlueprintCapture.capture(pos, pos, world);
        assertFalse(result.accepted());
        assertNull(result.design());
    }

    @Test
    void rejects_oversized_nested_inventory_data() {
        World world = new World();
        BlockPos pos = new BlockPos(4, 70, 4);
        world.states.put(pos, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        ListTag items = new ListTag();
        for (int index = 0; index < 100; index++) {
            CompoundTag item = new CompoundTag();
            item.putString("id", "minecraft:written_book");
            item.putString("pages", "x".repeat(500));
            items.add(item);
        }
        chest.put("Items", items);
        world.entities.put(pos, chest);

        BlueprintCapture.Result result = BlueprintCapture.capture(pos, pos, world);

        assertFalse(result.accepted());
        assertNull(result.design());
        assertTrue(result.reason().contains("size limit"), result.reason());
    }

    @Test
    void capture_removes_source_world_coordinates_from_block_entity_data() {
        World world = new World();
        BlockPos pos = new BlockPos(20, 70, -8);
        world.states.put(pos, Blocks.CHEST.defaultBlockState());
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", 20);
        chest.putInt("y", 70);
        chest.putInt("z", -8);
        chest.putString("CustomName", "Food");
        world.entities.put(pos, chest);

        BlueprintCapture.Result result = BlueprintCapture.capture(pos, pos, world);

        assertTrue(result.accepted(), result.reason());
        CompoundTag saved = result.design().blocks().getFirst().blockEntityData();
        assertFalse(saved.contains("x"));
        assertFalse(saved.contains("y"));
        assertFalse(saved.contains("z"));
        assertEquals("Food", saved.getStringOr("CustomName", ""));
    }

    private static final class World implements BlueprintCaptureSource {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, CompoundTag> entities = new HashMap<>();
        boolean loaded = true;
        boolean accessible = true;
        boolean inBounds = true;
        int stateReads;

        @Override public boolean loaded(BlockPos pos) { return loaded; }
        @Override public boolean accessible(BlockPos pos) { return accessible; }
        @Override public boolean inBounds(BlockPos pos) { return inBounds; }
        @Override public BlockState state(BlockPos pos) {
            stateReads++;
            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }
        @Override public CompoundTag blockEntityData(BlockPos pos) { return entities.get(pos); }
    }
}
