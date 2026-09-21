package com.jedts.theeconomist.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

public final class BlueprintTargeting {
    private BlueprintTargeting() { }

    public static BlueprintTarget select(Vec3 eye, Vec3 look, double reach, Set<BlockPos> fakeBlocks,
                                         Optional<BlockHitResult> realHit) {
        Vec3 end = eye.add(look.normalize().scale(reach));
        FakeHit fakeHit = fakeBlocks.stream()
                .map(position -> clip(position, eye, end))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(hit -> hit.location().distanceToSqr(eye)))
                .orElse(null);
        double realDistance = realHit.map(hit -> hit.getLocation().distanceToSqr(eye))
                .orElse(Double.MAX_VALUE);
        if (fakeHit != null && fakeHit.location().distanceToSqr(eye) < realDistance) {
            return new BlueprintTarget(fakeHit.position().relative(fakeHit.face()), fakeHit.position());
        }
        if (realHit.isPresent()) {
            BlockHitResult hit = realHit.get();
            return new BlueprintTarget(hit.getBlockPos().relative(hit.getDirection()), null);
        }
        return new BlueprintTarget(BlockPos.containing(eye.add(look.normalize().scale(4.0))), null);
    }

    private static Optional<FakeHit> clip(BlockPos position, Vec3 eye, Vec3 end) {
        AABB box = new AABB(position);
        Optional<Vec3> clipped = box.clip(eye, end);
        if (clipped.isEmpty()) return Optional.empty();
        Vec3 point = clipped.get();
        double epsilon = 1.0E-6;
        Direction face;
        if (Math.abs(point.x - box.minX) < epsilon) face = Direction.WEST;
        else if (Math.abs(point.x - box.maxX) < epsilon) face = Direction.EAST;
        else if (Math.abs(point.y - box.minY) < epsilon) face = Direction.DOWN;
        else if (Math.abs(point.y - box.maxY) < epsilon) face = Direction.UP;
        else if (Math.abs(point.z - box.minZ) < epsilon) face = Direction.NORTH;
        else face = Direction.SOUTH;
        return Optional.of(new FakeHit(position, face, point));
    }

    private record FakeHit(BlockPos position, Direction face, Vec3 location) { }
}
