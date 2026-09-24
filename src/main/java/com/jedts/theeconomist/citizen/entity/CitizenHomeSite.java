package com.jedts.theeconomist.citizen.entity;

import com.jedts.theeconomist.citizen.farm.claim.PlotClaimService;
import com.jedts.theeconomist.citizen.farm.NaturalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

/** Chooses a stable, unclaimed standing place with useful farming ground nearby. */
final class CitizenHomeSite {
    private static final int SEARCH_RADIUS = 6;
    private static final int HOME_SPACING = 4;

    private CitizenHomeSite() { }

    static Optional<BlockPos> choose(CitizenEntity citizen, ServerLevel level) {
        BlockPos origin = citizen.blockPosition();
        PlotClaimService claims = PlotClaimService.forLevel(level);
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                BlockPos probe = new BlockPos(x, origin.getY(), z);
                if (!level.hasChunkAt(probe)) continue;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                int localGround = origin.getY() - 1;
                int y = Math.abs(surface - localGround) <= 2 ? surface : localGround;
                BlockPos ground = new BlockPos(x, y, z);
                BlockPos feet = ground.above();
                int nearbySoil = nearbySoil(level, ground);
                if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)
                        || level.getBlockState(ground).is(Blocks.FARMLAND)
                        || nearbySoil < 2
                        || claims.ownerAt(ground).isPresent()
                        || !level.getFluidState(feet).isEmpty()
                        || !level.getFluidState(feet.above()).isEmpty()
                        || !level.getBlockState(feet).isAir()
                        || !level.getBlockState(feet.above()).isAir()
                        || occupiedByAnotherCitizen(citizen, level, feet)) continue;
                int distance = dx * dx + dz * dz;
                int waterDistance = NaturalWater.distanceFromHome(level, feet);
                int score = -distance * 2 + nearbySoil * 3;
                if (waterDistance >= 0) score += (9 - waterDistance) * 50;
                if (score > bestScore) {
                    bestScore = score;
                    best = feet;
                }
            }
        }
        return best == null ? fallback(citizen, level, claims) : Optional.of(best);
    }

    private static Optional<BlockPos> fallback(CitizenEntity citizen, ServerLevel level, PlotClaimService claims) {
        BlockPos origin = citizen.blockPosition();
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                BlockPos feet = origin.offset(dx, 0, dz);
                int distance = dx * dx + dz * dz;
                if (distance >= bestDistance || !level.hasChunkAt(feet)
                        || !level.getFluidState(feet).isEmpty()
                        || !level.getFluidState(feet.above()).isEmpty()
                        || !level.getBlockState(feet).isAir()
                        || !level.getBlockState(feet.above()).isAir()
                        || claims.ownerAt(feet.below()).isPresent()
                        || occupiedByAnotherCitizen(citizen, level, feet)) continue;
                best = feet;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int nearbySoil(ServerLevel level, BlockPos ground) {
        int score = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = ground.relative(direction);
            if (level.getBlockState(neighbor).is(Blocks.DIRT)
                    || level.getBlockState(neighbor).is(Blocks.GRASS_BLOCK)
                    || level.getBlockState(neighbor).is(Blocks.FARMLAND)) score++;
        }
        return score;
    }

    private static boolean occupiedByAnotherCitizen(CitizenEntity citizen, ServerLevel level, BlockPos feet) {
        for (CitizenEntity other : level.getEntitiesOfClass(CitizenEntity.class,
                citizen.getBoundingBox().inflate(SEARCH_RADIUS * 2), candidate -> candidate != citizen && candidate.isAlive())) {
            if (other.homeSpot().isEmpty()) continue;
            BlockPos home = other.homeSpot().orElseThrow();
            int dx = home.getX() - feet.getX();
            int dz = home.getZ() - feet.getZ();
            if (dx * dx + dz * dz < HOME_SPACING * HOME_SPACING) return true;
        }
        return false;
    }
}
