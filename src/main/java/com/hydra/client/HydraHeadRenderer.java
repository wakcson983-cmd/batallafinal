package com.hydra.client;

import com.hydra.entity.HydraHeadEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class HydraHeadRenderer extends MobEntityRenderer<HydraHeadEntity, HydraHeadModel> {
    private static final Identifier TEXTURE = Identifier.of("hydra", "textures/entity/hydra.png");

    public HydraHeadRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HydraHeadModel(ctx.getPart(ModModelLayers.HYDRA_HEAD)), 2.0f);
    }

    @Override
    public Identifier getTexture(HydraHeadEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HydraHeadEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(5.0f, 5.0f, 5.0f);
    }

    @Override
    protected float getLyingAngle(HydraHeadEntity entity) {
        return 0.0f;
    }
}
