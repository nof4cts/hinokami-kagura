package com.sunbreathing.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws all live {@link SlashArc}s as procedural meshes after particles, with additive blending.
 *
 * Each arc is drawn in two passes:
 *   1. a wide, faint, redder HALO (fakes bloom without a post-process pass)
 *   2. the bright CORE crescent; the slash_glow shader adds a white-hot cutting edge and
 *      scrolling flame licks driven by the u/v coordinates we write here:
 *        u = 0 at the tail .. 1 at the head, v = 0 inner edge .. 1 outer (cutting) edge
 * Without the shader, a vanilla position_color fallback bakes a simpler profile into vertex alpha.
 */
public final class SlashArcRenderer {
    private static final List<SlashArc> ARCS = new ArrayList<>();
    private static final int SEGMENTS = 40;

    private SlashArcRenderer() {}

    public static void add(SlashArc arc) {
        ARCS.add(arc);
        if (ARCS.size() > 48) ARCS.remove(0);
    }

    public static void clear() {
        ARCS.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ARCS.isEmpty()) return;
        double now = VfxClock.now();
        ARCS.removeIf(a -> a.expired(now));
        // Only start a buffer if something will actually be drawn (avoids uploading empty buffers).
        boolean anyVisible = false;
        for (SlashArc arc : ARCS) {
            if (arc.head(now) - arc.tail(now) >= 0.01f && arc.alpha(now) > 0.01f) {
                anyVisible = true;
                break;
            }
        }
        if (!anyVisible) return;

        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        ShaderInstance glow = ModShaders.slashGlow();
        boolean useGlow = glow != null;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        if (useGlow) {
            RenderSystem.setShader(() -> glow);
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        for (SlashArc arc : ARCS) {
            float head = arc.head(now);
            float tail = arc.tail(now);
            float alpha = arc.alpha(now);
            if (head - tail < 0.01f || alpha <= 0.01f) continue;
            // Halo: thicker, centred a bit outside the core, low alpha, deeper red.
            emitRibbon(bb, pose, cam, arc, tail, head, arc.thickness * 2.3f, arc.thickness * 0.55f,
                    0.95f, 0.22f, 0.05f, alpha * 0.38f, useGlow);
            // Core
            emitRibbon(bb, pose, cam, arc, tail, head, arc.thickness, 0f,
                    arc.r, arc.g, arc.b, alpha, useGlow);
        }

        BufferUploader.drawWithShader(bb.end());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * One crescent ribbon from tail..head. Thickness tapers to a point at both ends with the
     * fattest part near the head, which is what makes it read as a blade trail.
     */
    private static void emitRibbon(BufferBuilder bb, Matrix4f pose, Vec3 cam, SlashArc arc, float tail, float head,
                                   float thickness, float outerPush, float r, float g, float b, float a,
                                   boolean glow) {
        Vec3 prevOuter = null, prevMid = null, prevInner = null;
        float prevQ = 0f, prevW = 0f;
        for (int i = 0; i <= SEGMENTS; i++) {
            float q = i / (float) SEGMENTS;               // 0 tail .. 1 head
            float t = Mth.lerp(q, tail, head);            // position on the full arc
            float w = thickness * (float) Math.pow(q, 0.7) * (1f - (float) Math.pow(q, 8));
            Vec3 outer = arc.point(t, outerPush).subtract(cam);
            Vec3 mid = arc.point(t, outerPush - w * 0.22f).subtract(cam);
            Vec3 inner = arc.point(t, outerPush - w).subtract(cam);
            if (prevOuter != null) {
                // inner band (v 0 -> 0.78) and edge band (v 0.78 -> 1)
                quad(bb, pose, prevInner, inner, mid, prevMid, prevQ, q, 0f, 0.78f, r, g, b, a, glow, prevW, w);
                quad(bb, pose, prevMid, mid, outer, prevOuter, prevQ, q, 0.78f, 1f, r, g, b, a, glow, prevW, w);
            }
            prevOuter = outer;
            prevMid = mid;
            prevInner = inner;
            prevQ = q;
            prevW = w;
        }
    }

    private static void quad(BufferBuilder bb, Matrix4f pose, Vec3 aIn, Vec3 bIn, Vec3 bOut, Vec3 aOut,
                             float uA, float uB, float vIn, float vOut, float r, float g, float b, float alpha,
                             boolean glow, float wA, float wB) {
        if (glow) {
            vtx(bb, pose, aIn, r, g, b, alpha, uA, vIn);
            vtx(bb, pose, bIn, r, g, b, alpha, uB, vIn);
            vtx(bb, pose, bOut, r, g, b, alpha, uB, vOut);
            vtx(bb, pose, aOut, r, g, b, alpha, uA, vOut);
        } else {
            // Fallback profile: transparent inner edge, bright and whiter toward the cutting edge,
            // fading toward the tail.
            vtxC(bb, pose, aIn, r, g, b, alpha * fallbackAlpha(uA, vIn));
            vtxC(bb, pose, bIn, r, g, b, alpha * fallbackAlpha(uB, vIn));
            float wr = Mth.lerp(vOut, r, 1f), wg = Mth.lerp(vOut * 0.8f, g, 0.95f), wb = Mth.lerp(vOut * 0.6f, b, 0.8f);
            vtxC(bb, pose, bOut, wr, wg, wb, alpha * fallbackAlpha(uB, vOut));
            vtxC(bb, pose, aOut, wr, wg, wb, alpha * fallbackAlpha(uA, vOut));
        }
    }

    private static float fallbackAlpha(float u, float v) {
        float along = (float) Math.pow(u, 1.5);
        float across = v <= 0f ? 0f : (v < 0.9f ? 0.85f : 0.6f);
        return along * across;
    }

    private static void vtx(BufferBuilder bb, Matrix4f pose, Vec3 p, float r, float g, float b, float a, float u, float v) {
        bb.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(u, v).endVertex();
    }

    private static void vtxC(BufferBuilder bb, Matrix4f pose, Vec3 p, float r, float g, float b, float a) {
        bb.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }
}
