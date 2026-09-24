package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTransitionServiceTest {
    private static final BlueprintDesign DESIGN = new BlueprintDesign(1, 1, 1,
            List.of(new BlueprintBlock(0, 0, 0, "minecraft:stone")));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void saves_only_valid_designs_on_empty_blueprints() {
        BlueprintStackData empty = new BlueprintStackData(BlueprintState.EMPTY, null, null);

        BlueprintTransitionResult result = BlueprintTransitionService.saveDesign(empty, DESIGN);

        assertTrue(result.accepted());
        assertEquals(BlueprintState.DESIGNED, result.data().state());
        assertEquals(DESIGN, result.data().design());
    }

    @Test
    void rejects_stale_placement_transition() {
        BlueprintStackData changed = new BlueprintStackData(BlueprintState.PLANNED, DESIGN,
                new BlueprintPlacement("minecraft:overworld", BlockPos.ZERO, 0, false, false));

        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(changed,
                new BlueprintPlacement("minecraft:overworld", new BlockPos(5, 70, 5), 0, false, false),
                "minecraft:overworld", position -> false);

        assertFalse(result.accepted());
        assertSame(changed, result.data());
    }

    @Test
    void rechecks_occupied_positions_when_planning() {
        BlueprintStackData designed = new BlueprintStackData(BlueprintState.DESIGNED, DESIGN, null);

        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(designed,
                new BlueprintPlacement("minecraft:overworld", new BlockPos(5, 70, 5), 0, false, false),
                "minecraft:overworld", position -> position.equals(new BlockPos(5, 70, 5)));

        assertFalse(result.accepted());
        assertEquals(BlueprintState.DESIGNED, result.data().state());
    }

    @Test
    void rejects_a_same_state_blueprint_replaced_after_preview_started() {
        BlueprintDesign previewed = DESIGN;
        BlueprintDesign replacement = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:dirt")));
        BlueprintStackData changed = new BlueprintStackData(BlueprintState.DESIGNED, replacement, null);

        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(changed,
                new BlueprintPlacement("minecraft:overworld", BlockPos.ZERO, 0, false, false),
                "minecraft:overworld", position -> false, BlueprintDesignFingerprint.of(previewed));

        assertFalse(result.accepted());
        assertSame(changed, result.data());
    }

    @Test
    void rejects_distinct_designs_even_when_their_java_hash_codes_collide() {
        BlueprintDesign previewed = new BlueprintDesign(2, 32, 1, List.of(
                new BlueprintBlock(0, 0, 0, "minecraft:stone"),
                new BlueprintBlock(1, 31, 0, "minecraft:stone"),
                new BlueprintBlock(0, 31, 0, "minecraft:stone")));
        BlueprintDesign replacement = new BlueprintDesign(2, 32, 1, List.of(
                new BlueprintBlock(0, 0, 0, "minecraft:stone"),
                new BlueprintBlock(1, 31, 0, "minecraft:stone"),
                new BlueprintBlock(1, 0, 0, "minecraft:stone")));
        assertEquals(previewed.hashCode(), replacement.hashCode());

        BlueprintStackData changed = new BlueprintStackData(BlueprintState.DESIGNED, replacement, null);
        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(changed,
                new BlueprintPlacement("minecraft:overworld", BlockPos.ZERO, 0, false, false),
                "minecraft:overworld", position -> false, BlueprintDesignFingerprint.of(previewed));

        assertFalse(result.accepted());
        assertSame(changed, result.data());
    }

    @Test
    void captured_chest_data_survives_planning_and_design_rotation() {
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putString("CustomName", "Food");
        BlueprintDesign captured = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:chest", "facing=north", chest)));
        BlueprintPlacement placement = new BlueprintPlacement("minecraft:overworld", BlockPos.ZERO, 1, false, false);

        BlueprintTransitionResult planned = BlueprintTransitionService.confirmPlacement(
                new BlueprintStackData(BlueprintState.DESIGNED, captured, null), placement,
                "minecraft:overworld", pos -> false, BlueprintDesignFingerprint.of(captured));

        assertTrue(planned.accepted());
        assertEquals(captured, planned.data().design());
        assertEquals(chest, captured.rotated(1).blocks().getFirst().blockEntityData());
    }

    @Test
    void changed_chest_contents_reject_stale_placement_fingerprint() {
        CompoundTag oldChest = new CompoundTag();
        oldChest.putString("id", "minecraft:chest");
        oldChest.putString("CustomName", "Old");
        CompoundTag newChest = oldChest.copy();
        newChest.putString("CustomName", "New");
        BlueprintDesign before = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:chest", "", oldChest)));
        BlueprintDesign after = new BlueprintDesign(1, 1, 1,
                List.of(new BlueprintBlock(0, 0, 0, "minecraft:chest", "", newChest)));
        BlueprintTransitionResult result = BlueprintTransitionService.confirmPlacement(
                new BlueprintStackData(BlueprintState.DESIGNED, after, null),
                new BlueprintPlacement("minecraft:overworld", BlockPos.ZERO, 0, false, false),
                "minecraft:overworld", pos -> false, BlueprintDesignFingerprint.of(before));
        assertFalse(result.accepted());
    }
}
