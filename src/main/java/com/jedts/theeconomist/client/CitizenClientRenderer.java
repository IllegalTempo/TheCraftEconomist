package com.jedts.theeconomist.client;

import com.jedts.theeconomist.citizen.entity.CitizenEntities;
import com.jedts.theeconomist.citizen.entity.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;

public final class CitizenClientRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenRenderState, HumanoidModel<CitizenRenderState>> {
    private static final Identifier FALLBACK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    private CitizenClientRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    public static void register() {
        EntityRenderers.register(CitizenEntities.CITIZEN, CitizenClientRenderer::new);
    }

    @Override
    public CitizenRenderState createRenderState() {
        return new CitizenRenderState();
    }

    @Override
    public void extractRenderState(CitizenEntity entity, CitizenRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.child = entity.isChildCitizen();
        state.skinTextureValue = entity.skinTextureForRender();
    }

    @Override
    public Identifier getTextureLocation(CitizenRenderState state) {
        return CitizenSkinTextureManager.get(state.skinTextureValue, FALLBACK_TEXTURE);
    }

    @Override
    protected void scale(CitizenRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        if (state.child) poseStack.scale(0.6f, 0.6f, 0.6f);
    }
}
