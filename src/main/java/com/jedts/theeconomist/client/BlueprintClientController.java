package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintBlock;
import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import com.jedts.theeconomist.blueprint.BlueprintUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.ItemStack;
import com.jedts.theeconomist.blueprint.BlueprintStackData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Client-only controller for blueprint design and placement sessions. */
public final class BlueprintClientController {
    private static boolean designing;
    private static boolean placing;
    private static BlueprintDesign workingDesign;
    private static BlockPos designOrigin;
    private static final Map<BlockPos, BlockState> originals = new HashMap<>();
    private static final Map<BlockPos, BlockState> fakeBlocks = new HashMap<>();
    private static final Map<BlockPos, BlockState> previewOriginals = new HashMap<>();
    private static BlockPos placementOrigin;
    private static int placementRotation;
    private static boolean previousRotateKey;
    private static boolean previousMayFly;
    private static boolean previousFlying;

    private BlueprintClientController() { }

    public static void startDesign() {
        designing = true;
        placing = false;
        Minecraft minecraft = Minecraft.getInstance();
        designOrigin = minecraft.player.blockPosition();
        previousMayFly = minecraft.player.getAbilities().mayfly;
        previousFlying = minecraft.player.getAbilities().flying;
        minecraft.player.getAbilities().mayfly = true;
        minecraft.player.getAbilities().flying = true;
        minecraft.player.setInvisible(true);
        originals.clear();
        fakeBlocks.clear();
        minecraft.player.sendOverlayMessage(Component.literal("Design Mode Activated"));
    }
    public static void startPlacement(ItemStack stack) {
        BlueprintStackData data = BlueprintStackData.read(stack);
        workingDesign = data.design();
        placing = workingDesign != null;
        designing = false;
        if (placing) Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Placement preview activated."));
    }
    public static void cancel() {
        restoreFakeBlocks();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.getAbilities().mayfly = previousMayFly;
            minecraft.player.getAbilities().flying = previousFlying;
            minecraft.player.setInvisible(false);
        }
        designing = false; placing = false; designOrigin = null;
    }
    public static boolean designing() { return designing; }
    public static boolean placing() { return placing; }
    public static BlockPos designOrigin() { return designOrigin; }

    public static void updatePlacementPreview(Minecraft minecraft) {
        if (!placing || workingDesign == null || minecraft.player == null || minecraft.level == null) return;
        boolean rotateKey = minecraft.options.keySprint.isDown() && minecraft.options.keyUse.isDown();
        if (rotateKey && !previousRotateKey) placementRotation = (placementRotation + 1) % 4;
        previousRotateKey = rotateKey;
        HitResult hit = minecraft.player.pick(32.0, 0.0f, false);
        placementOrigin = hit instanceof BlockHitResult blockHit
                ? blockHit.getBlockPos().relative(blockHit.getDirection()) : minecraft.player.blockPosition();
        clearPlacementPreview(minecraft);
        BlueprintPlacement placement = currentPlacement(minecraft);
        for (BlueprintBlock block : workingDesign.blocks()) {
            BlockPos world = placement.worldPosition(workingDesign, block);
            previewOriginals.putIfAbsent(world, minecraft.level.getBlockState(world));
            minecraft.level.setBlock(world, Blocks.STAINED_GLASS.blue().defaultBlockState(), 19);
        }
    }

    public static BlueprintPlacement currentPlacement(Minecraft minecraft) {
        String dimension = minecraft.level == null ? "minecraft:overworld" : minecraft.level.dimension().identifier().toString();
        return new BlueprintPlacement(dimension, placementOrigin == null ? minecraft.player.blockPosition() : placementOrigin,
                placementRotation, false, false);
    }

    public static void confirmDesign(BlueprintDesign design) {
        workingDesign = design;
        ClientPlayNetworking.send(new BlueprintUpdatePayload(false, design, null));
    }

    public static void confirmPlacement(BlueprintPlacement placement) {
        if (workingDesign == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player.getMainHandItem();
        BlueprintStackData.setPlanned(stack, placement);
        minecraft.player.sendOverlayMessage(Component.literal("Blueprint planned."));
        cancel();
    }

    public static void captureDesign(ItemStack stack) {
        if (!designing || designOrigin == null) return;
        if (fakeBlocks.isEmpty()) {
            Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Build at least one fake block first."));
            return;
        }
        int minX = fakeBlocks.keySet().stream().mapToInt(pos -> pos.getX()).min().orElse(designOrigin.getX());
        int minY = fakeBlocks.keySet().stream().mapToInt(pos -> pos.getY()).min().orElse(designOrigin.getY());
        int minZ = fakeBlocks.keySet().stream().mapToInt(pos -> pos.getZ()).min().orElse(designOrigin.getZ());
        int maxX = fakeBlocks.keySet().stream().mapToInt(pos -> pos.getX()).max().orElse(minX);
        int maxY = fakeBlocks.keySet().stream().mapToInt(pos -> pos.getY()).max().orElse(minY);
        int maxZ = fakeBlocks.keySet().stream().mapToInt(pos -> pos.getZ()).max().orElse(minZ);
        List<com.jedts.theeconomist.blueprint.BlueprintBlock> blocks = new ArrayList<>();
        for (var entry : fakeBlocks.entrySet()) {
            BlockState state = entry.getValue();
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            blocks.add(new com.jedts.theeconomist.blueprint.BlueprintBlock(entry.getKey().getX() - minX,
                    entry.getKey().getY() - minY, entry.getKey().getZ() - minZ, id));
        }
        BlueprintDesign design = new BlueprintDesign(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, blocks);
        BlueprintStackData.setDesigned(stack, design);
        workingDesign = design;
        Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Blueprint designed and saved on client."));
        cancel();
    }

    public static void placeFake(Minecraft minecraft, BlockPos pos, BlockState state) {
        if (!designing || designOrigin == null || minecraft.level == null) return;
        if (designOrigin.distSqr(pos) > 32 * 32) {
            minecraft.player.sendOverlayMessage(Component.literal("Design limit: 32 blocks."));
            return;
        }
        originals.putIfAbsent(pos, minecraft.level.getBlockState(pos));
        fakeBlocks.put(pos, state);
        minecraft.level.setBlock(pos, Blocks.STAINED_GLASS.blue().defaultBlockState(), 19);
    }

    public static void removeFake(Minecraft minecraft, BlockPos pos) {
        if (!designing || minecraft.level == null) return;
        fakeBlocks.remove(pos);
        BlockState original = originals.get(pos);
        if (original != null) minecraft.level.setBlock(pos, original, 19);
    }

    private static void restoreFakeBlocks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            for (var entry : originals.entrySet()) minecraft.level.setBlock(entry.getKey(), entry.getValue(), 19);
        }
        originals.clear(); fakeBlocks.clear();
        clearPlacementPreview(minecraft);
    }

    private static void clearPlacementPreview(Minecraft minecraft) {
        if (minecraft.level != null) {
            for (var entry : previewOriginals.entrySet()) minecraft.level.setBlock(entry.getKey(), entry.getValue(), 19);
        }
        previewOriginals.clear();
    }
}
