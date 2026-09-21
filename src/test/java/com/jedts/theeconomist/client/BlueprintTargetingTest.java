package com.jedts.theeconomist.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlueprintTargetingTest {
    @Test
    void attaches_to_the_nearest_fake_block_face() {
        Vec3 eye = new Vec3(0.5, 0.5, -2.0);
        Vec3 look = new Vec3(0.0, 0.0, 1.0);

        BlueprintTarget target = BlueprintTargeting.select(eye, look, 6.0,
                Set.of(new BlockPos(0, 0, 0)), Optional.empty());

        assertEquals(new BlockPos(0, 0, -1), target.addPosition());
        assertEquals(new BlockPos(0, 0, 0), target.removePosition());
    }

    @Test
    void places_the_first_block_four_blocks_ahead_when_nothing_is_hit() {
        BlueprintTarget target = BlueprintTargeting.select(new Vec3(0.5, 64.5, 0.5),
                new Vec3(0.0, 0.0, 1.0), 6.0, Set.of(), Optional.empty());

        assertEquals(new BlockPos(0, 64, 4), target.addPosition());
        assertNull(target.removePosition());
    }

    @Test
    void chooses_the_nearer_of_real_and_fake_hits() {
        BlockHitResult real = new BlockHitResult(new Vec3(0.5, 0.5, 3.0), Direction.NORTH,
                new BlockPos(0, 0, 3), false);

        BlueprintTarget target = BlueprintTargeting.select(new Vec3(0.5, 0.5, -2.0),
                new Vec3(0.0, 0.0, 1.0), 8.0, Set.of(new BlockPos(0, 0, 0)), Optional.of(real));

        assertEquals(new BlockPos(0, 0, -1), target.addPosition());
    }
}
