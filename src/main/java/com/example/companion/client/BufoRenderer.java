package com.example.companion.client;

import com.example.companion.entity.BufoEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Swamp-skinned librarian villager, ~0.3x, with Allay wings, pulsing while speaking. */
public class BufoRenderer extends MobRenderer<BufoEntity, BufoModel> {
    private static final ResourceLocation BASE =
        new ResourceLocation("minecraft", "textures/entity/villager/villager.png");
    private static final ResourceLocation TYPE =
        new ResourceLocation("minecraft", "textures/entity/villager/type/swamp.png");
    private static final ResourceLocation PROFESSION =
        new ResourceLocation("minecraft", "textures/entity/villager/profession/librarian.png");
    private static final float BASE_SCALE = 0.45f;

    public BufoRenderer(EntityRendererProvider.Context context) {
        super(context, new BufoModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.3f);
        this.addLayer(new BufoOverlayLayer(this, TYPE));
        this.addLayer(new BufoOverlayLayer(this, PROFESSION, 0.60F, 0.40F, 0.90F));
        this.addLayer(new BufoWingsLayer(this, context.getModelSet()));
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
    public ResourceLocation getTextureLocation(BufoEntity entity) {
        return BASE;
    }
}
