package com.sunbreathing.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sunbreathing.client.particle.SunPalette;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Afterimages: glowing, translucent silhouettes of the player left behind during dashes,
 * sidesteps and the Solar Heat Haze decoy.
 *
 * Each ghost is a humanoid made of six boxes (head, torso, arms, legs) drawn additively in the
 * sun palette, so it reads as a figure of heat and light. "Shimmer" ghosts (the decoy) waver
 * sideways and flicker like heat haze before dissolving.
 */
public final class AfterimageRenderer {
    private static final List<Ghost> GHOSTS = new ArrayList<>();

    private AfterimageRenderer() {}

    private record Ghost(Vec3 pos, float yaw, double startMs, float lifeMs, float heat, float strength, boolean shimmer) {}

    public static void spawn(Vec3 feet, float bodyYaw, float lifeMs, float heat, float strength, boolean shimmer) {
        GHOSTS.add(new Ghost(feet, bodyYaw, VfxClock.now(), lifeMs, heat, strength, shimmer));
        if (GHOSTS.size() > 64) GHOSTS.remove(0);
    }

    public static void spawn(Player p, float lifeMs, float heat, float strength) {
        spawn(p.position(), p.yBodyRot, lifeMs, heat, strength, false);
    }

    /** Leaves one ghost per tick for {@code ticks} ticks, following the player. */
    public static void trail(Player p, int ticks, float lifeMs, float heat) {
        for (int i = 0; i < ticks; i++) {
            final float strength = 1f - i / (float) (ticks + 1) * 0.5f;
            ClientScheduler.schedule(i, () -> {
                if (!p.isRemoved()) spawn(p, lifeMs, heat, strength);
            });
        }
    }

    public static void clear() {
        GHOSTS.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || GHOSTS.isEmpty()) return;
        double now = VfxClock.now();
        GHOSTS.removeIf(g -> now - g.startMs > g.lifeMs);
        if (GHOSTS.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float[] rgb = new float[3];
        for (Ghost g : GHOSTS) {
            float life = Mth.clamp((float) ((now - g.startMs) / g.lifeMs), 0f, 1f);
            float alpha = g.strength * (1f - life) * (1f - life) * 0.35f;
            float jitter = 0f;
            if (g.shimmer) {
                double t = now / 1000.0;
                alpha *= 0.75f + 0.25f * (float) Math.sin(t * 31.0 + g.startMs);
                jitter = 0.05f * (float) Math.sin(t * 23.0) * (0.5f + life);
            }
            SunPalette.ramp(g.heat + life * 0.4f, rgb);
            Vec3 base = g.pos.subtract(cam);
            // local +z -> facing (-sin yaw, cos yaw); local +x -> shoulder axis (cos yaw, sin yaw)
            float yawRad = g.yaw * Mth.DEG_TO_RAD;
            float cos = Mth.cos(yawRad), sin = Mth.sin(yawRad);
            float s = 1f + life * 0.08f; // slight swell as it dissolves

            // x = side (left/right), y = up, z = forward depth; sizes in blocks (vanilla player proportions)
            box(bb, pose, base, cos, sin, s, jitter, -0.25f, 1.5f, -0.25f, 0.25f, 2.0f, 0.25f, rgb, alpha);        // head
            box(bb, pose, base, cos, sin, s, jitter, -0.25f, 0.75f, -0.125f, 0.25f, 1.5f, 0.125f, rgb, alpha);     // torso
            box(bb, pose, base, cos, sin, s, jitter, -0.5f, 0.75f, -0.125f, -0.25f, 1.5f, 0.125f, rgb, alpha * 0.85f); // arm
            box(bb, pose, base, cos, sin, s, jitter, 0.25f, 0.75f, -0.125f, 0.5f, 1.5f, 0.125f, rgb, alpha * 0.85f);   // arm
            box(bb, pose, base, cos, sin, s, jitter, -0.25f, 0f, -0.125f, 0f, 0.75f, 0.125f, rgb, alpha * 0.7f);      // leg
            box(bb, pose, base, cos, sin, s, jitter, 0f, 0f, -0.125f, 0.25f, 0.75f, 0.125f, rgb, alpha * 0.7f);       // leg
        }

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void box(BufferBuilder bb, Matrix4f pose, Vec3 base, float cos, float sin, float s, float jitter,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float[] rgb, float a) {
        float[][] c = new float[8][];
        int i = 0;
        for (int yi = 0; yi < 2; yi++) {
            for (int zi = 0; zi < 2; zi++) {
                for (int xi = 0; xi < 2; xi++) {
                    float lx = (xi == 0 ? x0 : x1) * s + jitter;
                    float ly = (yi == 0 ? y0 : y1);
                    float lz = (zi == 0 ? z0 : z1) * s;
                    float wx = lx * cos - lz * sin;
                    float wz = lx * sin + lz * cos;
                    c[i++] = new float[]{(float) base.x + wx, (float) base.y + ly, (float) base.z + wz};
                }
            }
        }
        // corner index = yi*4 + zi*2 + xi
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] f : faces) {
            for (int k : f) {
                // faces are brighter toward the top so the figure reads as rising heat
                float shade = 0.75f + 0.25f * (k >= 4 ? 1f : 0f);
                bb.vertex(pose, c[k][0], c[k][1], c[k][2]).color(rgb[0] * shade, rgb[1] * shade, rgb[2] * shade, a).endVertex();
            }
        }
    }
}
