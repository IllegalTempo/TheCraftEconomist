package com.jedts.theeconomist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class BlueprintSoulCamera {
    private Entity previousCamera;
    private ClientInput previousInput;
    private ArmorStand proxy;

    public void attach(Minecraft minecraft) {
        LocalPlayer player = Objects.requireNonNull(minecraft.player);
        previousCamera = minecraft.getCameraEntity();
        previousInput = player.input;
        proxy = new ArmorStand(Objects.requireNonNull(minecraft.level), player.getX(), player.getY(), player.getZ());
        proxy.setInvisible(true);
        proxy.setNoGravity(true);
        proxy.setYRot(player.getYRot());
        proxy.setXRot(player.getXRot());
        player.input = new ClientInput();
        minecraft.setCameraEntity(proxy);
    }

    public void tick(Minecraft minecraft) {
        if (proxy == null || minecraft.player == null) return;
        proxy.setYRot(minecraft.player.getYRot());
        proxy.setXRot(minecraft.player.getXRot());
        Vec3 forward = Vec3.directionFromRotation(0.0F, proxy.getYRot());
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 motion = Vec3.ZERO;
        if (minecraft.options.keyUp.isDown()) motion = motion.add(forward);
        if (minecraft.options.keyDown.isDown()) motion = motion.subtract(forward);
        if (minecraft.options.keyRight.isDown()) motion = motion.add(right);
        if (minecraft.options.keyLeft.isDown()) motion = motion.subtract(right);
        if (minecraft.options.keyJump.isDown()) motion = motion.add(0.0, 1.0, 0.0);
        if (minecraft.options.keyShift.isDown()) motion = motion.add(0.0, -1.0, 0.0);
        if (motion.lengthSqr() > 0.0) {
            proxy.setPos(proxy.position().add(motion.normalize().scale(0.35)));
        }
    }

    public void detach(Minecraft minecraft) {
        if (minecraft.player != null && previousInput != null) minecraft.player.input = previousInput;
        if (previousCamera != null) minecraft.setCameraEntity(previousCamera);
        previousCamera = null;
        previousInput = null;
        proxy = null;
    }

    public Vec3 eyePosition() {
        return Objects.requireNonNull(proxy, "soul camera is not attached").getEyePosition();
    }

    public Vec3 lookDirection() {
        return Objects.requireNonNull(proxy, "soul camera is not attached").getLookAngle();
    }
}
