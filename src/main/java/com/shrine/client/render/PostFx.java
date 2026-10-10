package com.shrine.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.shrine.ShrineMod;
import com.shrine.client.Casts;
import com.shrine.client.ScreenFx;
import com.shrine.config.ShrineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * The post pipeline, run once per frame after the world is drawn (before hand and GUI):
 *   1. copy     Minecraft's colour buffer -> scene
 *   2. effects  technique.fsh: every stroke, flame, domain and cut, over the frame and its depth -> HDR
 *   3. bloom    6 levels down (13-tap, soft threshold) and back up (tent)
 *   4. final    composite.fsh: impact frames, punch-in, shockwave, aberration, god rays, grade -> Minecraft's buffer
 * It only runs while an effect is alive or a screen effect is fading. GL state is restored afterwards.
 */
public final class PostFx {
    private static final int BLOOM_LEVELS = 6;
    private static final int MAX_SLASH = 48;

    private static GlProgram technique, down, up, composite;
    private static String failure;
    private static boolean warned;
    private static RenderTex scene, hdr;
    private static final RenderTex[] DOWN = new RenderTex[BLOOM_LEVELS];
    private static final RenderTex[] UP = new RenderTex[BLOOM_LEVELS - 1];
    private static int vao = -1;
    private static final long T0 = System.nanoTime();
    private static final float[] SLO = new float[MAX_SLASH * 4], SLD = new float[MAX_SLASH * 4],
            SLS = new float[MAX_SLASH * 4], SLP = new float[MAX_SLASH * 4];

    private PostFx() {}

    public static void reload(ResourceManager rm) {
        closePrograms();
        try {
            technique = GlProgram.load(rm, "fullscreen.vsh", "technique.fsh");
            down = GlProgram.load(rm, "fullscreen.vsh", "bloom_down.fsh");
            up = GlProgram.load(rm, "fullscreen.vsh", "bloom_up.fsh");
            composite = GlProgram.load(rm, "fullscreen.vsh", "composite.fsh");
            failure = null;
            warned = false;
            ShrineMod.LOGGER.info("Shrine effect shaders compiled");
        } catch (Exception e) {
            closePrograms();
            failure = e.getMessage();
            ShrineMod.LOGGER.error("Shrine effect shaders failed to compile; effects disabled", e);
        }
    }

    private static void closePrograms() {
        for (GlProgram p : new GlProgram[]{technique, down, up, composite}) if (p != null) p.close();
        technique = down = up = composite = null;
    }

    private static void ensureTargets(int w, int h) {
        if (scene != null && scene.width == w && scene.height == h) return;
        closeTargets();
        scene = new RenderTex(w, h, false);
        hdr = new RenderTex(w, h, true);
        int lw = w, lh = h;
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            lw = Math.max(1, lw / 2);
            lh = Math.max(1, lh / 2);
            DOWN[i] = new RenderTex(lw, lh, true);
            if (i < BLOOM_LEVELS - 1) UP[i] = new RenderTex(lw, lh, true);
        }
        if (vao < 0) vao = GL30.glGenVertexArrays();
    }

    private static void closeTargets() {
        if (scene != null) scene.close();
        if (hdr != null) hdr.close();
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            if (DOWN[i] != null) DOWN[i].close();
            DOWN[i] = null;
            if (i < BLOOM_LEVELS - 1) {
                if (UP[i] != null) UP[i].close();
                UP[i] = null;
            }
        }
        scene = hdr = null;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (technique == null) {
            if (failure != null && !warned && mc.player != null && Casts.any()) {
                warned = true;
                mc.player.displayClientMessage(Component.literal(
                        "§cShrine: effect shaders failed to compile on this GPU; see latest.log."), false);
            }
            return;
        }
        ScreenFx.update();
        Casts.Frame fx = Casts.frame(event.getPartialTick());
        if (!fx.any() && !ScreenFx.active()) return;
        try {
            draw(mc, event, fx);
        } catch (Throwable t) {
            ShrineMod.LOGGER.error("Shrine post pipeline failed; disabling effects until resources reload", t);
            failure = String.valueOf(t.getMessage());
            closePrograms();
        } finally {
            restoreState(mc);
        }
    }

    private static void rel(GlProgram p, String name, Vec3 v, Vec3 cam, float w) {
        if (v == null) {
            p.set(name, 0f, 0f, 0f, w);
            return;
        }
        p.set(name, (float) (v.x - cam.x), (float) (v.y - cam.y), (float) (v.z - cam.z), w);
    }

    private static void dir(GlProgram p, String name, Vec3 v, float w) {
        if (v == null) v = new Vec3(0, 0, 1);
        p.set(name, (float) v.x, (float) v.y, (float) v.z, w);
    }

    private static void draw(Minecraft mc, RenderLevelStageEvent event, Casts.Frame fx) {
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        ensureTargets(w, h);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f proj = new Matrix4f(event.getProjectionMatrix());
        Matrix4f invProj = new Matrix4f(proj).invert();
        Matrix3f invViewRot = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        Matrix3f viewRot = new Matrix3f(invViewRot).invert();
        float time = ((System.nanoTime() - T0) / 1.0e9f) % 3600f;

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GL30.glBindVertexArray(vao);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        // ---- 2. effects
        hdr.bindWrite();
        GlProgram p = technique;
        p.use();
        bindTexture(0, scene.texture);
        bindTexture(1, main.getDepthTextureId());
        p.set("SceneTex", 0);
        p.set("DepthTex", 1);
        p.set("Proj", proj);
        p.set("InvProj", invProj);
        p.set("ViewRot", viewRot);
        p.set("InvViewRot", invViewRot);
        p.set("Time", time);
        p.set("Steps", ShrineConfig.CLIENT.volumeSteps.get());

        int n = Math.min(MAX_SLASH, fx.slashes.size());
        for (int i = 0; i < n; i++) {
            float[] s = fx.slashes.get(i);
            SLO[i * 4] = (float) (s[0] - cam.x);
            SLO[i * 4 + 1] = (float) (s[1] - cam.y);
            SLO[i * 4 + 2] = (float) (s[2] - cam.z);
            SLO[i * 4 + 3] = s[3];
            System.arraycopy(s, 4, SLD, i * 4, 4);
            System.arraycopy(s, 8, SLS, i * 4, 4);
            System.arraycopy(s, 12, SLP, i * 4, 4);
        }
        p.set("NSlash", n);
        if (n > 0) {
            p.setVec4Array("SlO", SLO, n);
            p.setVec4Array("SlD", SLD, n);
            p.setVec4Array("SlS", SLS, n);
            p.setVec4Array("SlP", SLP, n);
        }
        rel(p, "WebC", fx.webC, cam, fx.webR);
        p.set("WebP", fx.webAge, fx.webSeed, fx.webAge >= 0 ? 1f : 0f, 0f);
        rel(p, "BfC", fx.bfC, cam, fx.bfAge);
        p.set("BfSeed", fx.bfSeed);
        p.set("FOn", fx.fuga ? 1f : 0f);
        if (fx.fuga) {
            rel(p, "FHand", fx.fHand, cam, fx.fHandAmt);
            rel(p, "FBow", fx.fBow, cam, fx.fBowAmt);
            dir(p, "FDir", fx.fDir, fx.fDraw);
            dir(p, "FUp", fx.fUp, fx.fFlight);
            rel(p, "FFrom", fx.fFrom, cam, 1f);
            rel(p, "FTarget", fx.fTarget, cam, fx.fBlast);
            p.set("FRadius", fx.fRadius);
        }
        rel(p, "DomC", fx.domC, cam, fx.domR);
        p.set("DomP", fx.domAge, fx.domStrength, fx.domFull, fx.domain ? 1f : 0f);
        rel(p, "WsO", fx.wsO, cam, fx.wsAge);
        dir(p, "WsD", fx.wsD, fx.wsLen);
        p.set("WsP", fx.wsSplit, 3f, fx.wsSky, fx.ws ? 1f : 0f);
        drawTriangle();

        // god rays from the fire pillar
        float[] rays = null;
        if (fx.fuga && fx.fBlast >= 0 && fx.fTarget != null) {
            Vector4f s = project(fx.fTarget.add(0, fx.fRadius * 1.5, 0).subtract(cam), viewRot, proj);
            if (s != null) rays = new float[]{s.x, s.y, 0.9f * (1f - Mth.clamp((fx.fBlast - 3f) / 5f, 0f, 1f)), 1f, 0.55f, 0.2f};
        } else if (fx.fuga && fx.fBowAmt > 0.05f && fx.fBow != null) {
            Vector4f s = project(fx.fBow.subtract(cam), viewRot, proj);
            if (s != null) rays = new float[]{s.x, s.y, 0.5f * fx.fBowAmt, 1f, 0.6f, 0.25f};
        }
        bloomAndComposite(main, w, h, time, cam, viewRot, proj, rays, fx);
    }

    private static void bloomAndComposite(RenderTarget main, int w, int h, float time, Vec3 cam, Matrix3f viewRot,
                                          Matrix4f proj, float[] rays, Casts.Frame fx) {
        RenderTex src = hdr;
        down.use();
        down.set("Src", 0);
        down.set("Threshold", 1.3f);
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            DOWN[i].bindWrite();
            bindTexture(0, src.texture);
            down.set("SrcTexel", 1f / src.width, 1f / src.height);
            down.set("Prefilter", i == 0 ? 1f : 0f);
            drawTriangle();
            src = DOWN[i];
        }
        up.use();
        up.set("Low", 0);
        up.set("Cur", 1);
        up.set("Radius", 1f);
        up.set("LowWeight", 0.6f);
        for (int i = BLOOM_LEVELS - 2; i >= 0; i--) {
            RenderTex low = i == BLOOM_LEVELS - 2 ? DOWN[BLOOM_LEVELS - 1] : UP[i + 1];
            UP[i].bindWrite();
            bindTexture(0, low.texture);
            bindTexture(1, DOWN[i].texture);
            up.set("LowTexel", 1f / low.width, 1f / low.height);
            drawTriangle();
        }

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._viewport(0, 0, w, h);
        composite.use();
        bindTexture(0, hdr.texture);
        bindTexture(1, UP[0].texture);
        composite.set("Hdr", 0);
        composite.set("Bloom", 1);
        composite.set("ScreenSize", (float) w, (float) h);
        composite.set("Time", time);
        composite.set("BloomStrength", 0.4f * ShrineConfig.CLIENT.bloom.get().floatValue());
        composite.set("Exposure", 1f);
        composite.set("Aberration", ScreenFx.aberration(fx.domain ? 0.25f * fx.domStrength : 0f));
        composite.set("Flash", 1f, 0.97f, 0.94f, ScreenFx.flash());
        composite.set("Afterimage", ScreenFx.afterimage());
        composite.set("Desaturate", ScreenFx.desaturate());
        composite.set("Vignette", 0.25f + (fx.domain ? 0.35f * fx.domStrength : 0f));
        composite.set("Grain", 0.3f * ShrineConfig.CLIENT.filmGrain.get().floatValue());
        composite.set("Shake", 0f, 0f);
        float shock = ScreenFx.shockStrength();
        Vector4f clip = shock > 0f ? project(ScreenFx.shockAt().subtract(cam), viewRot, proj) : null;
        if (clip != null) composite.set("Shockwave", clip.x, clip.y, ScreenFx.shockRadius(), shock);
        else composite.set("Shockwave", 0.5f, 0.5f, 0f, 0f);

        float[] imp = ShrineConfig.CLIENT.impactFrames.get() ? ScreenFx.impactFrame() : null;
        float iu = ScreenFx.impactU(), iv = ScreenFx.impactV();
        if (imp != null && ScreenFx.impactWorld() != null) {
            Vector4f c = project(ScreenFx.impactWorld().subtract(cam), viewRot, proj);
            if (c != null) {
                iu = Mth.clamp(c.x, 0.05f, 0.95f);
                iv = Mth.clamp(c.y, 0.05f, 0.95f);
            }
        }
        if (imp != null) {
            composite.set("Impact", imp[0], imp[1], iu, iv);
            composite.set("ImpactSeed", imp[2]);
        } else {
            composite.set("Impact", 0f, 0f, 0.5f, 0.5f);
            composite.set("ImpactSeed", 0f);
        }
        float[] acc = ScreenFx.accent();
        composite.set("Accent", acc[0], acc[1], acc[2]);
        composite.set("Punch", ScreenFx.punch());
        if (rays != null && rays[2] > 0.001f) {
            composite.set("GodRays", rays[0], rays[1], rays[2], 0f);
            composite.set("GodRayColor", rays[3], rays[4], rays[5]);
        } else {
            composite.set("GodRays", 0.5f, 0.5f, 0f, 0f);
            composite.set("GodRayColor", 1f, 1f, 1f);
        }
        drawTriangle();
    }

    private static Vector4f project(Vec3 rel, Matrix3f viewRot, Matrix4f proj) {
        Vector3f v = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
        viewRot.transform(v);
        Vector4f clip = new Vector4f(v, 1f);
        proj.transform(clip);
        if (clip.w <= 0f) return null;
        return new Vector4f(clip.x / clip.w * 0.5f + 0.5f, clip.y / clip.w * 0.5f + 0.5f, 0f, 0f);
    }

    private static void bindTexture(int unit, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(texture);
    }

    private static void drawTriangle() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void restoreState(Minecraft mc) {
        GL30.glBindVertexArray(0);
        BufferUploader.reset();
        bindTexture(1, 0);
        bindTexture(0, 0);
        GlStateManager._glUseProgram(0);
        ShaderInstance any = GameRenderer.getPositionTexShader();
        if (any != null) any.clear();
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }
}
