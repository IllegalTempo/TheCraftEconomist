package com.jedts.theeconomist.client;

import com.jedts.theeconomist.blueprint.BlueprintBlock;
import com.jedts.theeconomist.blueprint.BlueprintBlockSnapshot;
import com.jedts.theeconomist.blueprint.BlueprintDesign;
import com.jedts.theeconomist.blueprint.BlueprintItemAction;
import com.jedts.theeconomist.blueprint.BlueprintItemBehavior;
import com.jedts.theeconomist.blueprint.BlueprintItems;
import com.jedts.theeconomist.blueprint.BlueprintPlacement;
import com.jedts.theeconomist.blueprint.BlueprintSessionMode;
import com.jedts.theeconomist.blueprint.BlueprintStackData;
import com.jedts.theeconomist.blueprint.ConfirmBlueprintPlacementPayload;
import com.jedts.theeconomist.blueprint.SaveBlueprintDesignPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import com.mojang.blaze3d.platform.InputConstants;
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
    private static boolean escapeWasDown;

    private BlueprintClientController() { }

    public static void handleBlueprintUse(ItemStack stack) {
        BlueprintStackData data = BlueprintStackData.read(stack);
        switch (BlueprintItemBehavior.action(data.state(), session.mode())) {
            case SAVE_DESIGN -> {
                if (session.draft().isEmpty()) {
                    minecraft().player.sendOverlayMessage(Component.literal("Build at least one fake block first."));
                    return;
                }
                ClientPlayNetworking.send(new SaveBlueprintDesignPayload(session.draft().normalize()));
                cancel();
            }
            case CONFIRM_PLACEMENT -> {
                ClientPlayNetworking.send(new ConfirmBlueprintPlacementPayload(currentPlacement()));
                cancel();
            }
            case DESIGN -> startDesign();
            case PLACE -> {
                if (data.design() != null) startPlacement(data.design());
            }
            case NONE -> minecraft().player.sendOverlayMessage(Component.literal("This blueprint is already planned."));
        }
    }

    private static void startDesign() {
        Minecraft minecraft = minecraft();
        session = BlueprintSessionModel.designing();
        startingDimension = minecraft.level.dimension().identifier().toString();
        blueprintSlot = minecraft.player.getInventory().getSelectedSlot();
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
        designRenderBlocks.clear();
        refreshPlacementRenderMap();
        minecraft.player.sendOverlayMessage(Component.literal("Placement preview activated."));
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
        boolean escapeDown = InputConstants.isKeyDown(InputConstants.KEY_ESCAPE);
        boolean disconnected = minecraft.player == null || minecraft.level == null;
        boolean playerAlive = !disconnected && minecraft.player.isAlive();
        boolean sameDimension = !disconnected
                && minecraft.level.dimension().identifier().toString().equals(startingDimension);
        boolean hasRelevantBlueprint = !disconnected
                && blueprintSlot >= 0
                && minecraft.player.getInventory().getItem(blueprintSlot).getItem() == BlueprintItems.EMPTY_BLUEPRINT;
        if ((!escapeWasDown && escapeDown)
                || session.shouldCancel(playerAlive, sameDimension, hasRelevantBlueprint, disconnected)) {
            cancel();
        } else if (designing()) {
            soulCamera.tick(minecraft);
        } else {
            refreshPlacementRenderMap();
        }
        escapeWasDown = escapeDown;
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
        escapeWasDown = false;
    }

    public static boolean designing() {
        return session.mode() == BlueprintSessionMode.DESIGN;
    }

    public static boolean placing() {
        return session.mode() == BlueprintSessionMode.PLACEMENT;
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
