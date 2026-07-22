package com.example.companion.client;

import com.example.companion.entity.BufoEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Re-renders the villager model with a vanilla overlay texture (biome type or
 * profession). An optional color multiply lets us tint the robe (e.g. purple).
 */
public class BufoOverlayLayer extends RenderLayer<BufoEntity, BufoModel> {
    private final ResourceLocation texture;
    private final float red;
    private final float green;
    private final float blue;

    public BufoOverlayLayer(RenderLayerParent<BufoEntity, BufoModel> parent, ResourceLocation texture) {
        this(parent, texture, 1.0F, 1.0F, 1.0F);
    }

    public BufoOverlayLayer(RenderLayerParent<BufoEntity, BufoModel> parent, ResourceLocation texture,
                            float red, float green, float blue) {
        super(parent);
        this.texture = texture;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, BufoEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        this.getParentModel().renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY,
            this.red, this.green, this.blue, 1.0F);
    }
}
