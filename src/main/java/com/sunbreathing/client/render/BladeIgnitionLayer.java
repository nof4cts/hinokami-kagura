package com.sunbreathing.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.client.ClientConcentration;
import com.sunbreathing.item.NichirinBladeItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The black -> crimson-orange transformation.
 *
 * Re-renders the blade with an EMISSIVE, ADDITIVE texture (RenderType.eyes: full-bright, ONE/ONE
 * blending) whose brightness is the holder's eased "ignite" value. Because the base texture is
 * black, adding the glow mask on top literally fades the steel from black to burning crimson.
 *
 * Heat shimmer: two extra, slightly offset glow passes with flickering intensity make the edges
 * waver like air over a flame, without needing a distortion shader.
 */
public class BladeIgnitionLayer extends GeoRenderLayer<NichirinBladeItem> {
    private static final ResourceLocation GLOW = SunBreathingMod.id("textures/item/nichirin_blade_glow.png");
    private final NichirinBladeRenderer bladeRenderer;

    public BladeIgnitionLayer(NichirinBladeRenderer renderer) {
        super(renderer);
        this.bladeRenderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, NichirinBladeItem animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        LivingEntity holder = bladeRenderer.currentHolder();
        if (holder == null) return;
        float ignite = ClientConcentration.ignite(holder.getId(), partialTick);
        if (ignite <= 0.01f) return;

        float t = (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f;
        float flicker = 0.88f + 0.12f * Mth.sin(t * 13.1f) * Mth.sin(t * 7.3f + 1.7f);
        RenderType glow = RenderType.eyes(GLOW);

        // Main emissive pass.
        float k = ignite * flicker;
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                partialTick, 0xF000F0, OverlayTexture.NO_OVERLAY, k, k, k, 1f);

        // Heat shimmer: faint, wobbling, slightly inflated copies.
        for (int i = 0; i < 2; i++) {
            float phase = i * 2.1f;
            float wobble = 1.0f + 0.018f * ignite * (0.6f + Mth.sin(t * (21f + i * 6f) + phase));
            float shift = 0.004f * Mth.sin(t * (17f + i * 4f) + phase);
            float a = ignite * (0.22f + 0.1f * Mth.sin(t * 9f + phase));
            poseStack.pushPose();
            poseStack.translate(shift, 0, -shift);
            poseStack.scale(wobble, 1.0f + (wobble - 1f) * 0.3f, wobble);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, 0xF000F0, OverlayTexture.NO_OVERLAY, a, a * 0.85f, a * 0.7f, 1f);
            poseStack.popPose();
        }
    }
}
