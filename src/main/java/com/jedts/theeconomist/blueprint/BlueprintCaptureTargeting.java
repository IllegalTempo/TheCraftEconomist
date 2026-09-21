package com.jedts.theeconomist.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class BlueprintCaptureTargeting {
    private BlueprintCaptureTargeting() { }

    public static boolean matches(HitResult serverHit, BlockPos requested) {
        return serverHit instanceof BlockHitResult blockHit
                && serverHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(requested);
    }
}
