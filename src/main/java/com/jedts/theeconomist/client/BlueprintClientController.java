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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Client-only controller for blueprint design and placement sessions. */
public final class BlueprintClientController {
    private static boolean designing;
    private static boolean placing;
    private static BlueprintDesign workingDesign;
    private static BlockPos designOrigin;
    private static final Map<BlockPos, BlockState> originals = new HashMap<>();
    private static final Map<BlockPos, BlockState> fakeBlocks = new HashMap<>();
    private static final Map<BlockPos, BlockState> previewBlocks = new HashMap<>();
    private static BlockPos placementOrigin;
    private static int placementRotation;
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
        placementRotation = 0;
        placementOrigin = Minecraft.getInstance().player.blockPosition();
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
        HitResult hit = minecraft.player.pick(32.0, 0.0f, false);
        placementOrigin = hit instanceof BlockHitResult blockHit
                ? blockHit.getBlockPos().relative(blockHit.getDirection()) : minecraft.player.blockPosition();
        previewBlocks.clear();
        BlueprintPlacement placement = currentPlacement(minecraft);
        for (BlueprintBlock block : workingDesign.blocks()) {
            BlockPos world = placement.worldPosition(workingDesign, block);
            BlockState state = resolveBlockState(block);
            if (state != null) previewBlocks.put(world, state);
        }
    }

    public static void renderPlacementPreview(LevelRenderContext context) {
        if (!placing || previewBlocks.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        var poseStack = context.poseStack();
        for (var entry : previewBlocks.entrySet()) {
            BlockPos position = entry.getKey();
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(entry.getValue());
            List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(position.asLong()), parts);
            poseStack.pushPose();
            poseStack.translate(position.getX() - camera.x, position.getY() - camera.y, position.getZ() - camera.z);
            context.submitNodeCollector().submitBlockModel(
                    poseStack,
                    RenderTypes.translucentMovingBlock(),
                    parts,
                    BlockModelRenderState.EMPTY_TINTS,
                    15728880,
                    OverlayTexture.NO_OVERLAY,
                    ARGB.color(128, 40, 130, 255));
            poseStack.popPose();
        }
    }

    public static BlueprintPlacement currentPlacement(Minecraft minecraft) {
        String dimension = minecraft.level == null ? "minecraft:overworld" : minecraft.level.dimension().identifier().toString();
        return new BlueprintPlacement(dimension, placementOrigin == null ? minecraft.player.blockPosition() : placementOrigin,
                placementRotation, false, false);
    }

    public static void rotatePlacement() {
        placementRotation = (placementRotation + 1) % 4;
        Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Placement rotated " + (placementRotation * 90) + " degrees."));
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
                    entry.getKey().getY() - minY, entry.getKey().getZ() - minZ, id, serializeStateProperties(state)));
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
        clearPlacementPreview();
    }

    private static void clearPlacementPreview() {
        previewBlocks.clear();
    }

    private static BlockState resolveBlockState(BlueprintBlock block) {
        Block resolved = BuiltInRegistries.BLOCK.getValue(Identifier.parse(block.blockId()));
        if (resolved == null) return null;
        BlockState state = resolved.defaultBlockState();
        if (!block.stateProperties().isBlank()) {
            for (String property : block.stateProperties().split(",")) {
                String[] pair = property.split("=", 2);
                if (pair.length == 2) state = applyProperty(state, pair[0], pair[1]);
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static String serializeStateProperties(BlockState state) {
        StringJoiner properties = new StringJoiner(",");
        for (Property<?> property : state.getProperties()) {
            properties.add(serializeProperty(state, (Property) property));
        }
        return properties.toString();
    }

    private static <T extends Comparable<T>> String serializeProperty(BlockState state, Property<T> property) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, String name, String value) {
        Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) return state;
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

}
