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

/** Swamp-skinned librarian villager, ~0.45x, with Allay wings, a glow, and a radiant aura. */
public class BufoRenderer extends MobRenderer<BufoEntity, BufoModel> {
    private static final ResourceLocation BASE =
        new ResourceLocation("minecraft", "textures/entity/villager/villager.png");
    private static final ResourceLocation TYPE =
        new ResourceLocation("minecraft", "textures/entity/villager/type/swamp.png");
    private static final ResourceLocation PROFESSION =
        new ResourceLocation("minecraft", "textures/entity/villager/profession/librarian.png");
    private static final float BASE_SCALE = 0.45f;

    // ---- Radiant aura tunables ----
    private static final float GLOW_Y      = 0.45f;  // height of the glow's center (blocks above feet)
    private static final float GLOW_RADIUS = 1.00f;  // how far the light reaches out
    private static final int   GLOW_SEG    = 32;     // smoothness of the disc
    // Warm gold, RGB 0-1. Core alpha = brightness at center; edges fade to 0 (feathered).
    private static final float GLOW_R = 1.00f, GLOW_G = 0.80f, GLOW_B = 0.35f;
    private static final float GLOW_CORE_ALPHA = 0.45f;

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
        renderGlow(entity, partialTick, pose, buffers);
    }

    /** A soft, camera-facing radiant glow emanating from Bufo's body (bright core, feathered edge). */
    private void renderGlow(BufoEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(0.0F, GLOW_Y, 0.0F);
        // Billboard: face the camera so it reads as radiance, not a flat ring.
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        // Gentle breathing pulse.
        float pulse = 0.82f + 0.18f * Mth.sin((entity.tickCount + partialTick) * 0.10f);
        float coreA = GLOW_CORE_ALPHA * pulse;

        for (int i = 0; i < GLOW_SEG; i++) {
            double t0 = (Math.PI * 2.0) * i / GLOW_SEG;
            double t1 = (Math.PI * 2.0) * (i + 1) / GLOW_SEG;
            float x0 = (float) Math.cos(t0) * GLOW_RADIUS, y0 = (float) Math.sin(t0) * GLOW_RADIUS;
            float x1 = (float) Math.cos(t1) * GLOW_RADIUS, y1 = (float) Math.sin(t1) * GLOW_RADIUS;

            // Each wedge is a "quad" with a doubled center vertex (renders as a triangle).
            // Front face:
            core(vc, m, coreA); edge(vc, m, x0, y0); edge(vc, m, x1, y1); core(vc, m, coreA);
            // Back face (reversed) so it shows from any angle:
            core(vc, m, coreA); edge(vc, m, x1, y1); edge(vc, m, x0, y0); core(vc, m, coreA);
        }
        pose.popPose();
    }

    private static void core(VertexConsumer vc, Matrix4f m, float alpha) {
        vc.vertex(m, 0.0F, 0.0F, 0.0F).color(GLOW_R, GLOW_G, GLOW_B, alpha).endVertex();
    }

    private static void edge(VertexConsumer vc, Matrix4f m, float x, float y) {
        vc.vertex(m, x, y, 0.0F).color(GLOW_R, GLOW_G, GLOW_B, 0.0F).endVertex(); // transparent rim = feathered
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
