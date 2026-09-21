package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintBlock;
import com.jedts.theeconomist.blueprint.BlueprintBlockSnapshot;
import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintItemAction;
import com.jedts.theeconomist.blueprint.BlueprintItemBehavior;
import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import com.jedts.theeconomist.blueprint.BlueprintSessionMode;
import com.jedts.theeconomist.blueprint.BlueprintStackData;
import com.jedts.theeconomist.blueprint.ConfirmBlueprintPlacementPayload;
import com.jedts.theeconomist.blueprint.EmptyBlueprintItem;
import com.jedts.theeconomist.blueprint.SaveBlueprintDesignPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Owns the client-only blueprint session. The player's body remains server-controlled and vulnerable. */
public final class BlueprintClientController {
    private static BlueprintSessionModel session = BlueprintSessionModel.idle();
    private static final BlueprintSoulCamera soulCamera = new BlueprintSoulCamera();
    private static final Map<BlockPos, BlockState> designRenderBlocks = new HashMap<>();
    private static final Map<BlockPos, BlockState> placementRenderBlocks = new HashMap<>();
    private static String startingDimension;
    private static int blueprintSlot = -1;
    private static BlockPos designOrigin;

    private BlueprintClientController() { }

    public static void handleBlueprintUse(ItemStack stack) {
        BlueprintStackData data = BlueprintStackData.read(stack);
        if (session.mode() == BlueprintSessionMode.DESIGN) {
            if (session.draft().isEmpty()) {
                minecraft().player.sendOverlayMessage(Component.literal("Build at least one fake block first."));
                return;
            }
            ClientPlayNetworking.send(new SaveBlueprintDesignPayload(session.draft().normalize()));
            cancel();
            return;
        }
        if (session.mode() == BlueprintSessionMode.PLACEMENT) {
            ClientPlayNetworking.send(new ConfirmBlueprintPlacementPayload(currentPlacement()));
            cancel();
            return;
        }
        BlueprintItemAction action = BlueprintItemBehavior.action(data.state());
        if (action == BlueprintItemAction.DESIGN) startDesign();
        else if (action == BlueprintItemAction.PLACE && data.design() != null) startPlacement(data.design());
        else minecraft().player.sendOverlayMessage(Component.literal("This blueprint is already planned."));
    }

    public static void startDesign() {
        Minecraft minecraft = minecraft();
        session = BlueprintSessionModel.designing();
        startingDimension = minecraft.level.dimension().identifier().toString();
        blueprintSlot = minecraft.player.getInventory().getSelectedSlot();
        designOrigin = minecraft.player.blockPosition();
        designRenderBlocks.clear();
        placementRenderBlocks.clear();
        soulCamera.attach(minecraft);
        minecraft.player.sendOverlayMessage(Component.literal("Design mode activated."));
    }

    private static void startPlacement(BlueprintDesign design) {
        Minecraft minecraft = minecraft();
        session = BlueprintSessionModel.placing(design);
        startingDimension = minecraft.level.dimension().identifier().toString();
        blueprintSlot = minecraft.player.getInventory().getSelectedSlot();
        designOrigin = null;
        designRenderBlocks.clear();
        refreshPlacementRenderMap();
        minecraft.player.sendOverlayMessage(Component.literal("Placement preview activated."));
    }

    public static void startPlacement(ItemStack stack) {
        BlueprintDesign design = BlueprintStackData.read(stack).design();
        if (design != null) startPlacement(design);
    }

    public static InteractionResult useBlock(BlockItem blockItem) {
        if (!designing()) return InteractionResult.PASS;
        BlockPos position = BlockPos.containing(soulCamera.eyePosition()
                .add(soulCamera.lookDirection().normalize().scale(4.0)));
        BlockState state = blockItem.getBlock().defaultBlockState();
        session.draft().put(position, encodeState(state));
        designRenderBlocks.put(position, state);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult attack() {
        if (!designing()) return InteractionResult.PASS;
        BlockPos position = BlockPos.containing(soulCamera.eyePosition()
                .add(soulCamera.lookDirection().normalize().scale(4.0)));
        session.draft().remove(position);
        designRenderBlocks.remove(position);
        return InteractionResult.SUCCESS;
    }

    public static void tick(Minecraft minecraft) {
        if (session.mode() == BlueprintSessionMode.NONE) return;
        boolean disconnected = minecraft.getConnection() == null || minecraft.player == null || minecraft.level == null;
        boolean playerAlive = !disconnected && minecraft.player.isAlive();
        boolean sameDimension = !disconnected
                && minecraft.level.dimension().identifier().toString().equals(startingDimension);
        boolean hasRelevantBlueprint = !disconnected
                && minecraft.player.getInventory().getSelectedSlot() == blueprintSlot
                && minecraft.player.getMainHandItem().getItem() instanceof EmptyBlueprintItem;
        if (session.shouldCancel(playerAlive, sameDimension, hasRelevantBlueprint, disconnected)) {
            cancel();
            return;
        }
        if (designing()) soulCamera.tick(minecraft);
        if (placing()) refreshPlacementRenderMap();
    }

    public static void render(LevelRenderContext context) {
        renderBlocks(context, designRenderBlocks);
        renderBlocks(context, placementRenderBlocks);
    }

    public static void rotatePlacement() {
        if (!placing()) return;
        session.rotate();
        refreshPlacementRenderMap();
        minecraft().player.sendOverlayMessage(Component.literal(
                "Placement rotated " + (session.rotation() * 90) + " degrees."));
    }

    public static void cancel() {
        if (!session.cancel()) return;
        soulCamera.detach(minecraft());
        designRenderBlocks.clear();
        placementRenderBlocks.clear();
        startingDimension = null;
        blueprintSlot = -1;
        designOrigin = null;
    }

    public static boolean designing() {
        return session.mode() == BlueprintSessionMode.DESIGN;
    }

    public static boolean placing() {
        return session.mode() == BlueprintSessionMode.PLACEMENT;
    }

    public static BlockPos designOrigin() {
        return designOrigin;
    }

    public static void captureDesign(ItemStack stack) {
        handleBlueprintUse(stack);
    }

    public static void confirmDesign(BlueprintDesign design) {
        ClientPlayNetworking.send(new SaveBlueprintDesignPayload(design));
        cancel();
    }

    public static void confirmPlacement(BlueprintPlacement placement) {
        ClientPlayNetworking.send(new ConfirmBlueprintPlacementPayload(placement));
        cancel();
    }

    public static BlueprintPlacement currentPlacement(Minecraft ignored) {
        return currentPlacement();
    }

    public static void updatePlacementPreview(Minecraft minecraft) {
        tick(minecraft);
    }

    public static void renderPlacementPreview(LevelRenderContext context) {
        render(context);
    }

    public static void placeFake(Minecraft ignored, BlockPos position, BlockState state) {
        if (!designing()) return;
        session.draft().put(position, encodeState(state));
        designRenderBlocks.put(position, state);
    }

    public static void removeFake(Minecraft ignored, BlockPos position) {
        if (!designing()) return;
        session.draft().remove(position);
        designRenderBlocks.remove(position);
    }

    private static BlueprintPlacement currentPlacement() {
        Minecraft minecraft = minecraft();
        HitResult hit = minecraft.player.pick(32.0, 0.0F, false);
        BlockPos origin = hit instanceof BlockHitResult blockHit
                ? blockHit.getBlockPos().relative(blockHit.getDirection())
                : minecraft.player.blockPosition();
        return new BlueprintPlacement(minecraft.level.dimension().identifier().toString(), origin,
                session.rotation(), false, false);
    }

    private static void refreshPlacementRenderMap() {
        placementRenderBlocks.clear();
        BlueprintPlacement placement = currentPlacement();
        for (BlueprintBlock block : session.design().blocks()) {
            decodeState(new BlueprintBlockSnapshot(block.blockId(), block.stateProperties()))
                    .ifPresent(state -> placementRenderBlocks.put(
                            placement.worldPosition(session.design(), block), state));
        }
    }

    private static void renderBlocks(LevelRenderContext context, Map<BlockPos, BlockState> blocks) {
        Minecraft minecraft = minecraft();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos position = entry.getKey();
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(entry.getValue());
            List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(position.asLong()), parts);
            context.poseStack().pushPose();
            context.poseStack().translate(position.getX() - camera.x, position.getY() - camera.y,
                    position.getZ() - camera.z);
            context.submitNodeCollector().submitBlockModel(context.poseStack(), RenderTypes.translucentMovingBlock(),
                    parts, BlockModelRenderState.EMPTY_TINTS, 15728880, OverlayTexture.NO_OVERLAY,
                    ARGB.color(128, 40, 130, 255));
            context.poseStack().popPose();
        }
    }

    private static BlueprintBlockSnapshot encodeState(BlockState state) {
        StringJoiner properties = new StringJoiner(",");
        for (Property<?> property : state.getProperties()) properties.add(encodeProperty(state, property));
        return new BlueprintBlockSnapshot(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                properties.toString());
    }

    @SuppressWarnings("unchecked")
    private static String encodeProperty(BlockState state, Property<?> property) {
        return encodeTypedProperty(state, (Property<? extends Comparable>) property);
    }

    private static <T extends Comparable<T>> String encodeTypedProperty(BlockState state, Property<T> property) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }

    private static java.util.Optional<BlockState> decodeState(BlueprintBlockSnapshot snapshot) {
        Identifier id = Identifier.tryParse(snapshot.blockId());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return java.util.Optional.empty();
        BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        for (String encoded : snapshot.stateProperties().split(",")) {
            if (encoded.isBlank()) continue;
            String[] pair = encoded.split("=", 2);
            if (pair.length != 2) return java.util.Optional.empty();
            state = applyProperty(state, pair[0], pair[1]);
        }
        return java.util.Optional.of(state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, String name, String value) {
        Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) return state;
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
