package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
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
}
