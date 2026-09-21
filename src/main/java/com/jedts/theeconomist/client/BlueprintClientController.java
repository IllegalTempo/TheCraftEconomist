package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintCaptureCornerPayload;
import com.jedts.theeconomist.blueprint.BlueprintCaptureFeedbackPayload;
import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintDesignFingerprint;
import com.jedts.theeconomist.blueprint.BlueprintItems;
import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import com.jedts.theeconomist.blueprint.BlueprintSessionMode;
import com.jedts.theeconomist.blueprint.BlueprintStackData;
import com.jedts.theeconomist.blueprint.BlueprintState;
import com.jedts.theeconomist.blueprint.BlueprintTransitionFeedbackPayload;
import com.jedts.theeconomist.blueprint.ConfirmBlueprintPlacementPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;

/** Client-only selection and preview state; region contents are captured by the server. */
public final class BlueprintClientController {
    private static BlueprintSessionModel session = BlueprintSessionModel.idle();
    private static final BlueprintGhostRenderer ghostRenderer = new BlueprintGhostRenderer();
    private static final Map<BlockPos, BlockState> selectionRenderBlocks = new HashMap<>();
    private static final Map<BlockPos, BlockState> placementRenderBlocks = new HashMap<>();
    private static final Map<BlockPos, BlockState> plannedRenderBlocks = new HashMap<>();
    private static ItemStack plannedPreviewStack;
    private static CustomData plannedPreviewData;
    private static String plannedPreviewDimension;
    private static String startingDimension;
    private static int blueprintSlot = -1;
    private static ItemStack sessionStack;
    private static boolean escapeWasDown;
    private static int nextRequestId = 1;

    private BlueprintClientController() { }

    public static void handleCaptureCorner(BlockPos corner) {
        Minecraft minecraft = minecraft();
        if (minecraft.player == null || minecraft.level == null) return;
        ItemStack stack = minecraft.player.getMainHandItem();
        if (stack.getItem() != BlueprintItems.EMPTY_BLUEPRINT
                || BlueprintStackData.read(stack).state() != BlueprintState.EMPTY) return;
        if (!selecting()) {
            if (session.mode() != BlueprintSessionMode.NONE) cancel();
            session = BlueprintSessionModel.selecting();
            rememberHeldBlueprint(minecraft);
            selectionRenderBlocks.clear();
            placementRenderBlocks.clear();
            clearPlannedPreview();
        }
        ClientPlayNetworking.send(new BlueprintCaptureCornerPayload(corner, false));
    }

    public static void handleCaptureFeedback(BlueprintCaptureFeedbackPayload feedback) {
        if (!selecting()) return;
        Minecraft minecraft = minecraft();
        if (feedback.status() == 1) {
            session.firstCorner(feedback.firstCorner());
            selectionRenderBlocks.clear();
            if (minecraft.level != null && minecraft.level.hasChunkAt(feedback.firstCorner())) {
                BlockState state = minecraft.level.getBlockState(feedback.firstCorner());
                if (!state.isAir()) selectionRenderBlocks.put(feedback.firstCorner(), state);
            }
        } else if (feedback.status() == 2) {
            finishSession(false);
        } else {
            session.firstCorner(null);
            selectionRenderBlocks.clear();
        }
        if (minecraft.player != null && !feedback.reason().isBlank()) {
            minecraft.player.sendOverlayMessage(Component.literal(feedback.reason()));
        }
    }

    public static void handleBlueprintUse(ItemStack stack) {
        Minecraft minecraft = minecraft();
        if (minecraft.player == null || minecraft.level == null) return;
        BlueprintStackData data = BlueprintStackData.read(stack);
        if (placing()) {
            if (stack != sessionStack || minecraft.player.getInventory().getSelectedSlot() != blueprintSlot
                    || !session.design().equals(data.design())) {
                cancel();
                return;
            }
            int requestId = nextRequestId();
            if (session.beginRequest(requestId)) {
                ClientPlayNetworking.send(new ConfirmBlueprintPlacementPayload(requestId,
                        BlueprintDesignFingerprint.of(session.design()), currentPlacement()));
            }
        } else if (data.state() == BlueprintState.DESIGNED && data.design() != null) {
            if (selecting()) cancel();
            startPlacement(data.design());
        } else if (data.state() == BlueprintState.EMPTY) {
            minecraft.player.sendOverlayMessage(Component.literal("Right-click a block for each blueprint corner."));
        } else {
            minecraft.player.sendOverlayMessage(Component.literal("This blueprint is already planned."));
        }
    }

    private static void startPlacement(BlueprintDesign design) {
        Minecraft minecraft = minecraft();
        session = BlueprintSessionModel.placing(design);
        rememberHeldBlueprint(minecraft);
        selectionRenderBlocks.clear();
        clearPlannedPreview();
        refreshPlacementRenderMap();
        minecraft.player.sendOverlayMessage(Component.literal("Placement preview activated."));
    }

    private static void rememberHeldBlueprint(Minecraft minecraft) {
        startingDimension = minecraft.level.dimension().identifier().toString();
        blueprintSlot = minecraft.player.getInventory().getSelectedSlot();
        sessionStack = minecraft.player.getMainHandItem();
    }

    public static void tick(Minecraft minecraft) {
        boolean escapeDown = InputConstants.isKeyDown(InputConstants.KEY_ESCAPE);
        if (session.mode() == BlueprintSessionMode.NONE) {
            updatePlannedPreview(minecraft);
            escapeWasDown = escapeDown;
            return;
        }
        clearPlannedPreview();
        boolean disconnected = minecraft.player == null || minecraft.level == null;
        boolean playerAlive = !disconnected && minecraft.player.isAlive();
        boolean sameDimension = !disconnected
                && minecraft.level.dimension().identifier().toString().equals(startingDimension);
        boolean hasRelevantBlueprint = !disconnected && blueprintSlot >= 0
                && minecraft.player.getInventory().getSelectedSlot() == blueprintSlot
                && minecraft.player.getInventory().getItem(blueprintSlot) == sessionStack
                && sessionStack.getItem() == BlueprintItems.EMPTY_BLUEPRINT
                && (selecting() ? BlueprintStackData.read(sessionStack).state() == BlueprintState.EMPTY
                : session.design().equals(BlueprintStackData.read(sessionStack).design()));
        if ((!escapeWasDown && escapeDown)
                || session.shouldCancel(playerAlive, sameDimension, hasRelevantBlueprint, disconnected)) {
            cancel();
        } else if (placing()) {
            refreshPlacementRenderMap();
        }
        escapeWasDown = escapeDown;
    }

    public static void render(LevelRenderContext context) {
        ghostRenderer.render(context, selectionRenderBlocks);
        ghostRenderer.render(context, placementRenderBlocks);
        ghostRenderer.render(context, plannedRenderBlocks);
    }

    public static void rotatePlacement() {
        if (!placing()) return;
        session.rotate();
        refreshPlacementRenderMap();
        minecraft().player.sendOverlayMessage(Component.literal(
                "Placement rotated " + (session.rotation() * 90) + " degrees."));
    }

    public static void cancel() {
        finishSession(true);
    }

    private static void finishSession(boolean tellServer) {
        if (session.mode() == BlueprintSessionMode.NONE) return;
        if (tellServer && selecting() && ClientPlayNetworking.canSend(BlueprintCaptureCornerPayload.TYPE)) {
            ClientPlayNetworking.send(BlueprintCaptureCornerPayload.cancelSelection());
        }
        session.cancel();
        selectionRenderBlocks.clear();
        placementRenderBlocks.clear();
        clearPlannedPreview();
        startingDimension = null;
        blueprintSlot = -1;
        sessionStack = null;
        escapeWasDown = false;
    }

    public static void handleTransitionFeedback(BlueprintTransitionFeedbackPayload feedback) {
        if (!placing() || !feedback.placement() || !session.settleRequest(feedback.requestId())) return;
        if (feedback.accepted()) {
            cancel();
        } else if (minecraft().player != null) {
            minecraft().player.sendOverlayMessage(Component.literal("Blueprint rejected: " + feedback.reason()));
        }
    }

    private static int nextRequestId() {
        int requestId = nextRequestId++;
        if (requestId == 0 || nextRequestId == 0) nextRequestId = 1;
        return requestId == 0 ? nextRequestId++ : requestId;
    }

    public static boolean selecting() {
        return session.mode() == BlueprintSessionMode.SELECTING;
    }

    public static BlockPos firstCorner() {
        return session.firstCorner();
    }

    public static boolean placing() {
        return session.mode() == BlueprintSessionMode.PLACEMENT;
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
        placementRenderBlocks.putAll(BlueprintPlannedPreview.project(session.design(), currentPlacement()));
    }

    private static void updatePlannedPreview(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            clearPlannedPreview();
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.getItem() != BlueprintItems.EMPTY_BLUEPRINT) {
            clearPlannedPreview();
            return;
        }
        CustomData customData = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String dimension = minecraft.level.dimension().identifier().toString();
        if (held == plannedPreviewStack && customData == plannedPreviewData
                && dimension.equals(plannedPreviewDimension)) return;
        plannedRenderBlocks.clear();
        plannedPreviewStack = held;
        plannedPreviewData = customData;
        plannedPreviewDimension = dimension;
        try {
            plannedRenderBlocks.putAll(BlueprintPlannedPreview.blocksFor(BlueprintStackData.read(held), dimension));
        } catch (IllegalArgumentException ignored) {
            // An invalid item component must not crash the client render loop.
        }
    }

    private static void clearPlannedPreview() {
        plannedRenderBlocks.clear();
        plannedPreviewStack = null;
        plannedPreviewData = null;
        plannedPreviewDimension = null;
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
