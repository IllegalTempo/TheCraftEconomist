package com.jedts.theeconomist.citizen.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Water proximity checks for citizen housing and farming. */
public final class NaturalWater {
    private NaturalWater() { }

    /** Horizontal distance to natural water by a dry home, or -1 if none is within eight blocks. */
    public static int distanceFromHome(ServerLevel level, BlockPos feet) {
        return nearest(level, feet, 8, -1, 0, true);
    }

    /** Vanilla farmland checks for source water within four horizontal blocks at farmland height or one above. */
    public static boolean hydrates(ServerLevel level, BlockPos farmland) {
        return nearest(level, farmland, 4, 0, 1, false) >= 0;
    }

    private static int nearest(ServerLevel level, BlockPos center, int radius, int lowestY, int highestY,
                               boolean requireNaturalBank) {
        for (int distance = 0; distance <= radius; distance++) {
            for (int dz = -distance; dz <= distance; dz++) {
                for (int dx = -distance; dx <= distance; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    for (int dy = lowestY; dy <= highestY; dy++) {
                        BlockPos water = center.offset(dx, dy, dz);
                        if (isSourceWater(level, water)
                                && (!requireNaturalBank || hasTerrainBank(level, water))) return distance;
                    }
                }
            }
        }
        return -1;
    }

    private static boolean isSourceWater(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.WATER)
                && level.getFluidState(pos).isSource();
    }

    private static boolean hasTerrainBank(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos bank = pos.relative(direction);
            if (level.hasChunkAt(bank) && isTerrain(level.getBlockState(bank))) return true;
        }
        return false;
    }

    private static boolean isTerrain(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE)
                || state.is(Blocks.CLAY) || state.is(Blocks.MUD);
    }
}
