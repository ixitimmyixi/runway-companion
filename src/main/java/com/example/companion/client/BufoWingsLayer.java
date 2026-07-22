package com.example.companion.client;

import com.example.companion.entity.BufoEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renders Allay wings on Bufo's back, flapping. Placement constants are meant to be tuned by eye. */
public class BufoWingsLayer extends RenderLayer<BufoEntity, BufoModel> {
    private static final ResourceLocation ALLAY_TEX =
        new ResourceLocation("minecraft", "textures/entity/allay/allay.png");

    // ---- Tunable placement knobs ----
    private static final float WING_Y = -0.15f;   // moved down ~1/3 height   // up/down on the back (more negative = higher)
    private static final float WING_Z = 0.10f;    // fore/aft (more positive = further back)
    private static final float WING_SCALE = 1.75f; // overall wing size
    private static final float FLAP_SPEED = 0.55f;
    private static final float FLAP_AMOUNT = 0.9f;
    private static final float WING_R = 0.80f;
    private static final float WING_G = 0.00f;
    private static final float WING_B = 0.30f;
    // ----------------------------------

    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public BufoWingsLayer(RenderLayerParent<BufoEntity, BufoModel> parent, EntityModelSet models) {
        super(parent);
        ModelPart allay = models.bakeLayer(ModelLayers.ALLAY);
        this.rightWing = find(allay, "right_wing");
        this.leftWing = find(allay, "left_wing");
    }

    private static ModelPart find(ModelPart allay, String wing) {
        ModelPart p = tryPath(allay, "body", wing);
        if (p == null) p = tryPath(allay, "root", "body", wing);
        if (p == null) p = tryPath(allay, wing);
        return p;
    }

    private static ModelPart tryPath(ModelPart base, String... path) {
        ModelPart p = base;
        try {
            for (String n : path) p = p.getChild(n);
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, BufoEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (rightWing == null || leftWing == null) return;
        float flap = (Mth.cos(ageInTicks * FLAP_SPEED) * 0.5f + 0.5f) * FLAP_AMOUNT;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(ALLAY_TEX));
        pose.pushPose();
        pose.translate(0.0D, WING_Y, WING_Z);
        pose.scale(WING_SCALE, WING_SCALE, WING_SCALE);
        rightWing.yRot = -flap;
        leftWing.yRot = flap;
        rightWing.render(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, WING_R, WING_G, WING_B, 1.0F);
        leftWing.render(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, WING_R, WING_G, WING_B, 1.0F);
        pose.popPose();
    }
}
