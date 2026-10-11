package com.hydra.client;

import com.hydra.entity.HydraEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class HydraRenderer extends MobEntityRenderer<HydraEntity, HydraModel> {
    private static final Identifier TEXTURE = Identifier.of("hydra", "textures/entity/hydra.png");

    public HydraRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HydraModel(ctx.getPart(ModModelLayers.HYDRA)), 4.0f);
    }

    @Override
    public Identifier getTexture(HydraEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HydraEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(4.0f, 4.0f, 4.0f);
    }

    @Override
    protected float getLyingAngle(HydraEntity entity) {
        return 0.0f;
    }
}
