package com.example.companion.client;

import com.example.companion.entity.BufoEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Swamp-skinned librarian villager, ~0.45x, with Allay wings, a glow, and a golden halo. */
public class BufoRenderer extends MobRenderer<BufoEntity, BufoModel> {
    private static final ResourceLocation BASE =
        new ResourceLocation("minecraft", "textures/entity/villager/villager.png");
    private static final ResourceLocation TYPE =
        new ResourceLocation("minecraft", "textures/entity/villager/type/swamp.png");
    private static final ResourceLocation PROFESSION =
        new ResourceLocation("minecraft", "textures/entity/villager/profession/librarian.png");
    private static final float BASE_SCALE = 0.45f;

    // ---- Halo tunables (blocks) ----
    private static final float HALO_Y      = 0.95f;  // height above the player-visible feet
    private static final float HALO_R_IN   = 0.16f;  // inner radius
    private static final float HALO_R_OUT  = 0.24f;  // outer radius
    private static final float HALO_THICK  = 0.012f; // tiny top/bottom offset so both faces show
    private static final int   HALO_SEG    = 40;     // smoothness
    // Warm gold, RGBA 0-1. Alpha controls glow intensity (additive blend).
    private static final float HALO_R = 1.00f, HALO_G = 0.80f, HALO_B = 0.32f, HALO_A = 0.85f;

    public BufoRenderer(EntityRendererProvider.Context context) {
        super(context, new BufoModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.3f);
        this.addLayer(new BufoOverlayLayer(this, TYPE));
        this.addLayer(new BufoOverlayLayer(this, PROFESSION, 0.60F, 0.40F, 0.90F));
        this.addLayer(new BufoWingsLayer(this, context.getModelSet()));
    }

    @Override
    public void render(BufoEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
        renderHalo(entity, partialTick, pose, buffers);
    }

    /** A flat, glowing golden ring hovering above Bufo's head (drawn in world/entity space). */
    private void renderHalo(BufoEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        // Gentle bob for a little life.
        float bob = Mth.sin((entity.tickCount + partialTick) * 0.08f) * 0.03f;
        pose.translate(0.0F, HALO_Y + bob, 0.0F);

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        for (int i = 0; i < HALO_SEG; i++) {
            double t0 = (Math.PI * 2.0) * i / HALO_SEG;
            double t1 = (Math.PI * 2.0) * (i + 1) / HALO_SEG;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);

            float ix0 = HALO_R_IN * c0,  iz0 = HALO_R_IN * s0;
            float ox0 = HALO_R_OUT * c0, oz0 = HALO_R_OUT * s0;
            float ix1 = HALO_R_IN * c1,  iz1 = HALO_R_IN * s1;
            float ox1 = HALO_R_OUT * c1, oz1 = HALO_R_OUT * s1;

            // Top face (visible from above)
            v(vc, m, ix0, HALO_THICK, iz0);
            v(vc, m, ox0, HALO_THICK, oz0);
            v(vc, m, ox1, HALO_THICK, oz1);
            v(vc, m, ix1, HALO_THICK, iz1);

            // Bottom face (reverse winding, visible from below)
            v(vc, m, ix1, -HALO_THICK, iz1);
            v(vc, m, ox1, -HALO_THICK, oz1);
            v(vc, m, ox0, -HALO_THICK, oz0);
            v(vc, m, ix0, -HALO_THICK, iz0);
        }
        pose.popPose();
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z) {
        vc.vertex(m, x, y, z).color(HALO_R, HALO_G, HALO_B, HALO_A).endVertex();
    }

    @Override
    protected void scale(BufoEntity entity, PoseStack pose, float partialTick) {
        float s = BASE_SCALE;
        if (BufoSpeakingState.isSpeaking()) {
            s *= 1.0f + 0.08f * Mth.sin((entity.tickCount + partialTick) * 0.6f);
        }
        pose.scale(s, s, s);
    }

    @Override
    protected int getBlockLightLevel(BufoEntity entity, BlockPos pos) {
        return 15; // emissive: Bufo self-glows instead of being darkened by ambient light
    }

    @Override
    public ResourceLocation getTextureLocation(BufoEntity entity) {
        return BASE;
    }
}
