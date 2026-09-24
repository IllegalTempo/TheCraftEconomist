package com.jedts.theeconomist.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BlueprintGhostRenderer {
    public void render(LevelRenderContext context, Map<BlockPos, BlockState> blocks) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos position = entry.getKey();
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(entry.getValue());
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(position.asLong()), parts);
            if (parts.isEmpty()) {
                BlockState fallback = BlueprintGhostFallback.renderState(entry.getValue(), true);
                minecraft.getModelManager().getBlockStateModelSet().get(fallback)
                        .collectParts(RandomSource.create(position.asLong()), parts);
            }
            if (parts.isEmpty()) continue;
            context.poseStack().pushPose();
            context.poseStack().translate(position.getX() - camera.x, position.getY() - camera.y,
                    position.getZ() - camera.z);
            context.submitNodeCollector().submitBlockModel(context.poseStack(), RenderTypes.translucentMovingBlock(),
                    parts, BlockModelRenderState.EMPTY_TINTS, 15728880, OverlayTexture.NO_OVERLAY,
                    ARGB.color(128, 40, 130, 255));
            context.poseStack().popPose();
        }
    }
}
