package com.hydra.client;

import com.hydra.entity.HydraHeadEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/** Cabeza flotante de la fase 2: cabeza grande + un cuello que se pierde en el cielo. Escala 5. */
public class HydraHeadModel extends EntityModel<HydraHeadEntity> {
    private static final int SEGS = 6;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart[] neck = new ModelPart[SEGS];

    public HydraHeadModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.jaw = head.getChild("jaw");
        ModelPart p = root;
        for (int i = 0; i < SEGS; i++) {
            ModelPart c = p.getChild(i == 0 ? "neck0" : "neck" + i);
            neck[i] = c;
            p = c;
        }
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        ModelPartData head = root.addChild("head",
                ModelPartBuilder.create()
                        .uv(0, 56).cuboid(-5.0f, -4.5f, -11.0f, 10.0f, 9.0f, 11.0f)
                        .uv(50, 56).cuboid(-3.5f, -1.5f, -19.0f, 7.0f, 5.0f, 8.0f),
                ModelTransform.pivot(0.0f, 14.0f, 0.0f));
        head.addChild("jaw",
                ModelPartBuilder.create().uv(90, 56).cuboid(-3.0f, 0.0f, -15.0f, 6.0f, 2.0f, 15.0f),
                ModelTransform.pivot(0.0f, 3.5f, -4.0f));
        head.addChild("hornL",
                ModelPartBuilder.create().uv(140, 56).cuboid(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 10.0f),
                ModelTransform.of(4.0f, -4.0f, -2.0f, 0.6f, 0.35f, 0.0f));
        head.addChild("hornR",
                ModelPartBuilder.create().uv(140, 56).cuboid(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 10.0f),
                ModelTransform.of(-4.0f, -4.0f, -2.0f, 0.6f, -0.35f, 0.0f));
        head.addChild("eyes",
                ModelPartBuilder.create().uv(240, 240)
                        .cuboid(-5.3f, -2.8f, -9.0f, 0.8f, 2.0f, 3.0f)
                        .cuboid(4.5f, -2.8f, -9.0f, 0.8f, 2.0f, 3.0f),
                ModelTransform.NONE);

        // cuello hacia atras y hacia arriba (se pierde en el cielo)
        ModelPartData parent = root;
        for (int i = 0; i < SEGS; i++) {
            float t = Math.max(3.0f, 8.0f - i * 1.0f);
            ModelTransform tf = i == 0
                    ? ModelTransform.of(0.0f, 14.0f, 1.0f, 0.3f, 0.0f, 0.0f)
                    : ModelTransform.of(0.0f, 0.0f, 8.0f, 0.28f, 0.0f, 0.0f);
            parent = parent.addChild("neck" + i,
                    ModelPartBuilder.create().uv(0, 80).cuboid(-t / 2.0f, -t / 2.0f, 0.0f, t, t, 9.0f), tf);
        }
        return TexturedModelData.of(data, 256, 256);
    }

    @Override
    public void setAngles(HydraHeadEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
        head.yaw = 0.0f;
        float roar = e.getRoarProgress();
        boolean open = e.isBreathing() || roar > 0.0f;
        jaw.pitch = open ? 0.7f : 0.06f + MathHelper.sin(age * 0.1f) * 0.03f;
        for (int i = 0; i < SEGS; i++) {
            neck[i].yaw = MathHelper.sin(age * 0.05f + i * 0.6f) * 0.08f;
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
