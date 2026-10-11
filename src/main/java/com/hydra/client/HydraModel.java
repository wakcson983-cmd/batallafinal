package com.hydra.client;

import com.hydra.entity.HydraEntity;
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

/** Cuerpo de HYDRA (fase 1): torso, 3 cuellos con cabeza, alas, patas y cola. Se dibuja a escala 4. */
public class HydraModel extends EntityModel<HydraEntity> {
    private static final int NECKS = 3;
    private static final int SEGS = 5;
    private static final int TAIL = 7;
    private static final float L = 9.0f;
    private static final float[] NECK_SIZE = {8.0f, 7.5f, 7.0f, 6.5f, 6.0f};
    private static final float[] NECK_PITCH = {-0.5f, -0.35f, -0.1f, 0.25f, 0.35f};
    private static final float[] NECK_X = {-8.0f, 0.0f, 8.0f};
    private static final float[] NECK_YAW = {0.4f, 0.0f, -0.4f};
    private static final float[] NECK_PITCH_ADD = {0.05f, -0.12f, 0.05f};
    private static final float HEAD_PITCH = 0.5f;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart wingL;
    private final ModelPart wingR;
    private final ModelPart wingL2;
    private final ModelPart wingR2;
    private final ModelPart[][] neck = new ModelPart[NECKS][SEGS];
    private final ModelPart[] head = new ModelPart[NECKS];
    private final ModelPart[] jaw = new ModelPart[NECKS];
    private final ModelPart[] tail = new ModelPart[TAIL];

    public HydraModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.wingL = body.getChild("wingL");
        this.wingR = body.getChild("wingR");
        this.wingL2 = wingL.getChild("wingL2");
        this.wingR2 = wingR.getChild("wingR2");
        for (int n = 0; n < NECKS; n++) {
            ModelPart parent = body;
            for (int k = 0; k < SEGS; k++) {
                neck[n][k] = parent.getChild("n" + n + "s" + k);
                parent = neck[n][k];
            }
            head[n] = neck[n][SEGS - 1].getChild("h" + n);
            jaw[n] = head[n].getChild("j" + n);
        }
        ModelPart p = body;
        for (int i = 0; i < TAIL; i++) {
            tail[i] = p.getChild("t" + i);
            p = tail[i];
        }
    }

    private static float segYaw(int n, int k) {
        return k == 0 ? NECK_YAW[n] : -NECK_YAW[n] * 0.2f;
    }

    private static float segPitch(int n, int k) {
        return NECK_PITCH[k] + (k == 0 ? NECK_PITCH_ADD[n] : 0.0f);
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        ModelPartData body = root.addChild("body",
                ModelPartBuilder.create().uv(0, 0).cuboid(-12.0f, -8.0f, -18.0f, 24.0f, 16.0f, 36.0f),
                ModelTransform.pivot(0.0f, 10.0f, 0.0f));

        // espinas del lomo
        for (int i = 0; i < 5; i++) {
            body.addChild("spine" + i,
                    ModelPartBuilder.create().uv(40, 80).cuboid(-1.0f, -5.0f, -2.0f, 2.0f, 5.0f, 4.0f),
                    ModelTransform.pivot(0.0f, -8.0f, -12.0f + i * 7.0f));
        }

        // tres cuellos con cabeza
        for (int n = 0; n < NECKS; n++) {
            ModelPartData parent = body;
            for (int k = 0; k < SEGS; k++) {
                float t = NECK_SIZE[k];
                ModelTransform tf = k == 0
                        ? ModelTransform.of(NECK_X[n], -3.0f, -16.0f, segPitch(n, k), segYaw(n, k), 0.0f)
                        : ModelTransform.of(0.0f, 0.0f, -(L - 1.0f), segPitch(n, k), segYaw(n, k), 0.0f);
                ModelPartData seg = parent.addChild("n" + n + "s" + k,
                        ModelPartBuilder.create().uv(0, 80).cuboid(-t / 2.0f, -t / 2.0f, -L, t, t, L), tf);
                if (k == SEGS - 1) {
                    ModelPartData h = seg.addChild("h" + n,
                            ModelPartBuilder.create()
                                    .uv(0, 56).cuboid(-5.0f, -4.5f, -11.0f, 10.0f, 9.0f, 11.0f)
                                    .uv(50, 56).cuboid(-3.5f, -1.5f, -19.0f, 7.0f, 5.0f, 8.0f),
                            ModelTransform.of(0.0f, 0.0f, -(L - 1.0f), HEAD_PITCH, 0.0f, 0.0f));
                    h.addChild("j" + n,
                            ModelPartBuilder.create().uv(90, 56).cuboid(-3.0f, 0.0f, -15.0f, 6.0f, 2.0f, 15.0f),
                            ModelTransform.pivot(0.0f, 3.5f, -4.0f));
                    h.addChild("hl" + n,
                            ModelPartBuilder.create().uv(140, 56).cuboid(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 10.0f),
                            ModelTransform.of(4.0f, -4.0f, -2.0f, 0.6f, 0.35f, 0.0f));
                    h.addChild("hr" + n,
                            ModelPartBuilder.create().uv(140, 56).cuboid(-1.0f, -1.0f, 0.0f, 2.0f, 2.0f, 10.0f),
                            ModelTransform.of(-4.0f, -4.0f, -2.0f, 0.6f, -0.35f, 0.0f));
                    h.addChild("eyes" + n,
                            ModelPartBuilder.create().uv(240, 240)
                                    .cuboid(-5.3f, -2.8f, -9.0f, 0.8f, 2.0f, 3.0f)
                                    .cuboid(4.5f, -2.8f, -9.0f, 0.8f, 2.0f, 3.0f),
                            ModelTransform.NONE);
                }
                parent = seg;
            }
        }

        // alas
        ModelPartData wr = body.addChild("wingR",
                ModelPartBuilder.create()
                        .uv(0, 100).cuboid(0.0f, -2.0f, -2.0f, 30.0f, 4.0f, 4.0f)
                        .uv(0, 198).cuboid(0.0f, 0.5f, 2.0f, 30.0f, 1.0f, 28.0f),
                ModelTransform.pivot(11.0f, -6.0f, -8.0f));
        wr.addChild("wingR2",
                ModelPartBuilder.create()
                        .uv(0, 108).cuboid(0.0f, -1.5f, -1.5f, 28.0f, 3.0f, 3.0f)
                        .uv(0, 198).cuboid(0.0f, 0.5f, 1.0f, 28.0f, 1.0f, 20.0f),
                ModelTransform.pivot(30.0f, 0.0f, 0.0f));
        ModelPartData wl = body.addChild("wingL",
                ModelPartBuilder.create()
                        .uv(0, 100).cuboid(-30.0f, -2.0f, -2.0f, 30.0f, 4.0f, 4.0f)
                        .uv(0, 198).cuboid(-30.0f, 0.5f, 2.0f, 30.0f, 1.0f, 28.0f),
                ModelTransform.pivot(-11.0f, -6.0f, -8.0f));
        wl.addChild("wingL2",
                ModelPartBuilder.create()
                        .uv(0, 108).cuboid(-28.0f, -1.5f, -1.5f, 28.0f, 3.0f, 3.0f)
                        .uv(0, 198).cuboid(-28.0f, 0.5f, 1.0f, 28.0f, 1.0f, 20.0f),
                ModelTransform.pivot(-30.0f, 0.0f, 0.0f));

        // patas
        for (int s = -1; s <= 1; s += 2) {
            String id = s < 0 ? "legL" : "legR";
            ModelPartData leg = body.addChild(id,
                    ModelPartBuilder.create().uv(60, 115).cuboid(-3.0f, 0.0f, -3.0f, 6.0f, 12.0f, 6.0f),
                    ModelTransform.of(s * 8.0f, 6.0f, 10.0f, -0.5f, 0.0f, 0.0f));
            leg.addChild("foot_" + id,
                    ModelPartBuilder.create().uv(60, 134).cuboid(-3.0f, 0.0f, -6.0f, 6.0f, 3.0f, 8.0f),
                    ModelTransform.pivot(0.0f, 11.0f, 0.0f));
        }

        // cola
        ModelPartData tp = body;
        for (int i = 0; i < TAIL; i++) {
            float t = 11.0f - i * 1.3f;
            ModelPartBuilder b = ModelPartBuilder.create().uv(0, 115).cuboid(-t / 2.0f, -t / 2.0f, 0.0f, t, t, L);
            if (i == TAIL - 1) {
                b = b.uv(0, 198).cuboid(-8.0f, -0.5f, 3.0f, 16.0f, 1.0f, 10.0f);
            }
            ModelTransform tf = i == 0 ? ModelTransform.pivot(0.0f, 1.0f, 17.0f) : ModelTransform.pivot(0.0f, 0.0f, L - 1.0f);
            tp = tp.addChild("t" + i, b, tf);
        }

        return TexturedModelData.of(data, 256, 256);
    }

    @Override
    public void setAngles(HydraEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        float t = age * 0.11f;
        boolean dying = e.isDyingAnim();
        float flap = dying ? 0.9f : MathHelper.sin(t) * 0.55f + 0.1f;
        float elbow = dying ? 0.4f : MathHelper.sin(t - 0.9f) * 0.35f;
        wingR.roll = flap;
        wingL.roll = -flap;
        wingR2.roll = elbow;
        wingL2.roll = -elbow;
        body.pivotY = 10.0f + (dying ? 0.0f : MathHelper.sin(t) * 1.2f);

        float roar = e.getRoarProgress();
        boolean open = e.isBreathing() || roar > 0.0f;
        for (int n = 0; n < NECKS; n++) {
            for (int k = 0; k < SEGS; k++) {
                float sway = MathHelper.sin(age * 0.05f + n * 2.1f + k * 0.6f);
                neck[n][k].pitch = segPitch(n, k) + MathHelper.cos(age * 0.045f + n * 1.7f + k * 0.5f) * 0.045f
                        - (roar > 0.0f ? 0.08f : 0.0f);
                neck[n][k].yaw = segYaw(n, k) + sway * 0.07f;
            }
            head[n].pitch = HEAD_PITCH - 0.5f * roar;
            jaw[n].pitch = open ? 0.65f : 0.05f + MathHelper.sin(age * 0.1f + n) * 0.03f;
        }
        for (int i = 0; i < TAIL; i++) {
            tail[i].yaw = MathHelper.sin(age * 0.07f - i * 0.45f) * 0.14f;
            tail[i].pitch = (i == 0 ? 0.1f : 0.05f) + MathHelper.cos(age * 0.06f - i * 0.4f) * 0.04f;
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
