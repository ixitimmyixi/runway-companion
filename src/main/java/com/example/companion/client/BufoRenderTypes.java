package com.example.companion.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom render type for Bufo's aura: untextured, additive, and crucially it does NOT
 * write to the depth buffer (COLOR_WRITE only). A depth-writing glow would occlude the
 * sky/clouds behind its transparent edges; skipping depth write keeps it see-through.
 * Subclassing RenderType is just so we can reach the protected state shards.
 */
public final class BufoRenderTypes extends RenderType {
    private BufoRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int bufSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, fmt, mode, bufSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    public static final RenderType GLOW = create(
        "bufo_glow",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setTransparencyState(LIGHTNING_TRANSPARENCY) // additive glow
            .setWriteMaskState(COLOR_WRITE)               // color only -> no depth occlusion
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .createCompositeState(false));
}
