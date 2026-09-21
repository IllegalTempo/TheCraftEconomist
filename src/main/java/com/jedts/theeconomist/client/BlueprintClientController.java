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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Owns the client-only blueprint session. The player's body remains server-controlled and vulnerable. */
public final class BlueprintClientController {
    private static BlueprintSessionModel session = BlueprintSessionModel.idle();
    private static final BlueprintSoulCamera soulCamera = new BlueprintSoulCamera();
    private static final BlueprintGhostRenderer ghostRenderer = new BlueprintGhostRenderer();
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
        BlueprintTarget target = currentDesignTarget();
        BlockState state = blockItem.getBlock().defaultBlockState();
        session.draft().put(target.addPosition(), BlueprintBlockStateCodec.encode(state));
        refreshDesignRenderMap();
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult attack() {
        if (!designing()) return InteractionResult.PASS;
        BlueprintTarget target = currentDesignTarget();
        if (target.removePosition() != null) session.draft().remove(target.removePosition());
        refreshDesignRenderMap();
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
        ghostRenderer.render(context, designRenderBlocks);
        ghostRenderer.render(context, placementRenderBlocks);
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
        session.draft().put(position, BlueprintBlockStateCodec.encode(state));
        refreshDesignRenderMap();
    }

    public static void removeFake(Minecraft ignored, BlockPos position) {
        if (!designing()) return;
        session.draft().remove(position);
        refreshDesignRenderMap();
    }

    private static BlueprintTarget currentDesignTarget() {
        Minecraft minecraft = minecraft();
        Vec3 eye = soulCamera.eyePosition();
        Vec3 look = soulCamera.lookDirection();
        Vec3 end = eye.add(look.normalize().scale(6.0));
        BlockHitResult worldHit = minecraft.level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        Optional<BlockHitResult> realHit = worldHit.getType() == HitResult.Type.BLOCK
                ? Optional.of(worldHit) : Optional.empty();
        return BlueprintTargeting.select(eye, look, 6.0, session.draft().blocks().keySet(), realHit);
    }

    private static void refreshDesignRenderMap() {
        designRenderBlocks.clear();
        for (Map.Entry<BlockPos, BlueprintBlockSnapshot> entry : session.draft().blocks().entrySet()) {
            BlueprintBlockStateCodec.decode(entry.getValue())
                    .ifPresent(state -> designRenderBlocks.put(entry.getKey(), state));
        }
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
            BlueprintBlockStateCodec.decode(new BlueprintBlockSnapshot(block.blockId(), block.stateProperties()))
                    .ifPresent(state -> placementRenderBlocks.put(
                            placement.worldPosition(session.design(), block), state));
        }
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
