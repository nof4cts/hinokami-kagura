package com.ton618.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.ton618.Ton618Mod;
import com.ton618.client.ClientHoles;
import com.ton618.client.ScreenFx;
import com.ton618.client.cinematic.Cutscene;
import com.ton618.client.cinematic.Pose;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import com.ton618.config.Ton618Config;
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
 * The post-processing pipeline, run once per frame after the world is drawn (before the hand/GUI):
 *
 *   1. copy    Minecraft's colour buffer -> scene (we can't read and write the same texture)
 *   2. hole    blackhole.fsh: lensing, accretion disk, jets -> HDR target (RGBA16F)
 *   3. bloom   6-level 13-tap downsample with a soft threshold, then tent upsample back up
 *   4. final   composite.fsh: shockwave, aberration, bloom, roll-off, flash, grade -> Minecraft's buffer
 *
 * It only runs while a black hole exists or a screen effect is still fading, so it costs nothing
 * the rest of the time. GL state is restored afterwards so vanilla rendering is unaffected.
 */
public final class PostFx {
    private static final int BLOOM_LEVELS = 6;

    private static GlProgram hole, orbit, down, up, composite;
    private static String failure;
    private static boolean warned;

    private static RenderTex scene, hdr;
    private static final RenderTex[] DOWN = new RenderTex[BLOOM_LEVELS];
    private static final RenderTex[] UP = new RenderTex[BLOOM_LEVELS - 1];
    private static int vao = -1;
    private static final long T0 = System.nanoTime();

    private PostFx() {}

    // ------------------------------------------------------------------ lifecycle
    /** Resource (re)load: compile every pass. Runs on the render thread. */
    public static void reload(ResourceManager rm) {
        closePrograms();
        try {
            hole = GlProgram.load(rm, "fullscreen.vsh", "blackhole.fsh");
            orbit = GlProgram.load(rm, "fullscreen.vsh", "orbit.fsh");
            down = GlProgram.load(rm, "fullscreen.vsh", "bloom_down.fsh");
            up = GlProgram.load(rm, "fullscreen.vsh", "bloom_up.fsh");
            composite = GlProgram.load(rm, "fullscreen.vsh", "composite.fsh");
            failure = null;
            warned = false;
            Ton618Mod.LOGGER.info("TON 618 effect shaders compiled");
        } catch (Exception e) {
            closePrograms();
            failure = e.getMessage();
            Ton618Mod.LOGGER.error("TON 618 effect shaders failed to compile; effects disabled", e);
        }
    }

    private static void closePrograms() {
        for (GlProgram p : new GlProgram[]{hole, orbit, down, up, composite}) {
            if (p != null) p.close();
        }
        hole = orbit = down = up = composite = null;
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

    // ------------------------------------------------------------------ frame
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (hole == null) {
            if (failure != null && !warned && mc.player != null && ClientHoles.any()) {
                warned = true;
                mc.player.displayClientMessage(Component.literal(
                        "§cTON 618: effect shaders failed to compile on this GPU; see latest.log."), false);
            }
            return;
        }

        ScreenFx.update();
        Vec3 cam = event.getCamera().getPosition();
        ClientHoles.Frame frame = ClientHoles.frame(event.getPartialTick(), cam);
        ScreenFx.setAmbient(frame == null ? 0f : ambientShake(frame, cam));
        Pose pose = Cutscene.pose();
        Pose space = pose != null && pose.space() ? pose : null;
        if (frame == null && space == null && !ScreenFx.active()) return;

        try {
            draw(mc, event, frame, cam, space);
        } catch (Throwable t) {
            Ton618Mod.LOGGER.error("TON 618 post pipeline failed; disabling effects until resources reload", t);
            failure = String.valueOf(t.getMessage());
            closePrograms();
        } finally {
            restoreState(mc);
        }
    }

    /** The ground starts trembling as the hole closes in: by distance and by how far along the fall is. */
    private static float ambientShake(ClientHoles.Frame f, Vec3 cam) {
        if (!f.hole().falls() || f.approach() >= 1f) return 0f;
        double d = f.position().distanceTo(cam);
        float near = Mth.clamp(1f - (float) (d / (f.hole().rs() * 60.0)), 0f, 1f);
        return 0.55f * f.approach() * f.approach() * (0.3f + 0.7f * near);
    }

    private static void draw(Minecraft mc, RenderLevelStageEvent event, ClientHoles.Frame frame, Vec3 cam,
                             Pose space) {
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        ensureTargets(w, h);

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

        // 1. copy the finished world frame
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.fbo);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

        // 2a. orbit shots replace the whole image
        if (space != null) {
            hdr.bindWrite();
            drawOrbit(mc, space, w, h, time);
        } else {
            drawHole(main, frame, cam, proj, invProj, viewRot, invViewRot, time);
        }
        bloomAndComposite(main, w, h, time, frame, cam, viewRot, proj, space != null);
    }

    private static final ResourceLocation EARTH_DAY = Ton618Mod.id("textures/space/earth_day_clouds.png");
    private static final ResourceLocation EARTH_NIGHT = Ton618Mod.id("textures/space/earth_night_ocean.png");

    private static int earthTexture(Minecraft mc, ResourceLocation loc) {
        AbstractTexture tex = mc.getTextureManager().getTexture(loc);
        tex.setFilter(true, false);
        GlStateManager._bindTexture(tex.getId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        return tex.getId();
    }

    private static void drawOrbit(Minecraft mc, Pose pose, int w, int h, float time) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int day = earthTexture(mc, EARTH_DAY);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        int night = earthTexture(mc, EARTH_NIGHT);
        orbit.use();
        bindTexture(0, day);
        bindTexture(1, night);
        Vec3 fwd = pose.fwd();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(fwd).normalize();
        float roll = pose.roll() * Mth.DEG_TO_RAD;
        Vec3 r2 = right.scale(Mth.cos(roll)).add(up.scale(Mth.sin(roll)));
        Vec3 u2 = up.scale(Mth.cos(roll)).subtract(right.scale(Mth.sin(roll)));
        orbit.set("EarthDay", 0);
        orbit.set("EarthNight", 1);
        orbit.set("CamPos", (float) pose.pos().x, (float) pose.pos().y, (float) pose.pos().z);
        orbit.set("CamFwd", (float) fwd.x, (float) fwd.y, (float) fwd.z);
        orbit.set("CamRight", (float) r2.x, (float) r2.y, (float) r2.z);
        orbit.set("CamUp", (float) u2.x, (float) u2.y, (float) u2.z);
        orbit.set("TanHalfFov", (float) Math.tan(Math.toRadians(pose.fov()) / 2.0));
        orbit.set("Aspect", w / (float) h);
        orbit.set("Time", time);
        orbit.set("Charge", pose.charge());
        orbit.set("FireAge", pose.fireAge());
        orbit.set("ProjT", pose.projT());
        orbit.set("Steps", Math.min(256, Ton618Config.CLIENT.quality.get().steps));
        drawTriangle();
    }

    private static void drawHole(RenderTarget main, ClientHoles.Frame frame, Vec3 cam, Matrix4f proj,
                                 Matrix4f invProj, Matrix3f viewRot, Matrix3f invViewRot, float time) {
        // 2b. the black hole over the world
        hdr.bindWrite();
        hole.use();
        bindTexture(0, scene.texture);
        bindTexture(1, main.getDepthTextureId());
        hole.set("SceneTex", 0);
        hole.set("DepthTex", 1);
        hole.set("Proj", proj);
        hole.set("InvProj", invProj);
        hole.set("ViewRot", viewRot);
        hole.set("InvViewRot", invViewRot);
        hole.set("Time", time);
        hole.set("Steps", Ton618Config.CLIENT.quality.get().steps);
        if (frame != null) {
            Vec3 rel = frame.position().subtract(cam);
            Matrix3f disk = new Matrix3f()
                    .rotateX(frame.hole().tiltX() * Mth.DEG_TO_RAD)
                    .rotateZ(frame.hole().tiltZ() * Mth.DEG_TO_RAD);
            hole.set("HasHole", 1f);
            hole.set("HoleRel", (float) rel.x, (float) rel.y, (float) rel.z);
            hole.set("Rs", frame.hole().rs());
            hole.set("DiskBasis", disk);
            hole.set("Presence", frame.presence());
            hole.set("Eclipse", frame.eclipse());
            hole.set("Jets", frame.jets());
            hole.set("DiskGain", 1.4f);
            hole.set("SpaceMix", 0f);
        } else {
            hole.set("HasHole", 0f);
        }
        drawTriangle();
    }

    private static void bloomAndComposite(RenderTarget main, int w, int h, float time, ClientHoles.Frame frame,
                                          Vec3 cam, Matrix3f viewRot, Matrix4f proj, boolean space) {
        // 3. bloom: down...
        RenderTex src = hdr;
        down.use();
        down.set("Src", 0);
        down.set("Threshold", 1.4f);
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            DOWN[i].bindWrite();
            bindTexture(0, src.texture);
            down.set("SrcTexel", 1f / src.width, 1f / src.height);
            down.set("Prefilter", i == 0 ? 1f : 0f);
            drawTriangle();
            src = DOWN[i];
        }
        // ...and back up
        up.use();
        up.set("Low", 0);
        up.set("Cur", 1);
        up.set("Radius", 1f);
        up.set("LowWeight", 0.55f);
        for (int i = BLOOM_LEVELS - 2; i >= 0; i--) {
            RenderTex low = i == BLOOM_LEVELS - 2 ? DOWN[BLOOM_LEVELS - 1] : UP[i + 1];
            UP[i].bindWrite();
            bindTexture(0, low.texture);
            bindTexture(1, DOWN[i].texture);
            up.set("LowTexel", 1f / low.width, 1f / low.height);
            drawTriangle();
        }

        // 4. composite back into Minecraft's frame
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._viewport(0, 0, w, h);
        composite.use();
        bindTexture(0, hdr.texture);
        bindTexture(1, UP[0].texture);
        composite.set("Hdr", 0);
        composite.set("Bloom", 1);
        composite.set("ScreenSize", (float) w, (float) h);
        composite.set("Time", time);
        composite.set("BloomStrength", 0.35f * Ton618Config.CLIENT.bloom.get().floatValue());
        composite.set("Exposure", 1f);
        float approachAb = space ? 0.15f : (frame == null ? 0f : 0.25f * frame.eclipse());
        composite.set("Aberration", ScreenFx.aberration(approachAb));
        composite.set("Flash", 1f, 0.97f, 0.92f, ScreenFx.flash());
        composite.set("Afterimage", ScreenFx.afterimage());
        composite.set("Desaturate", space ? 0f : Math.max(ScreenFx.desaturate(), frame == null ? 0f : 0.15f * frame.eclipse()));
        composite.set("Vignette", space ? 0.4f : 0.25f + (frame == null ? 0f : 0.25f * frame.eclipse()));
        composite.set("Grain", 0.35f * Ton618Config.CLIENT.filmGrain.get().floatValue());
        composite.set("Shake", 0f, 0f);
        float shock = ScreenFx.shockStrength();
        Vector4f clip = shock > 0f && !space ? project(ScreenFx.shockAt().subtract(cam), viewRot, proj) : null;
        if (clip != null) {
            composite.set("Shockwave", clip.x, clip.y, ScreenFx.shockRadius(), shock);
        } else {
            composite.set("Shockwave", 0.5f, 0.5f, 0f, 0f);
        }
        drawTriangle();
    }

    /** World offset -> screen uv (x, y), or null if behind the camera. */
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

    /** Put everything back the way Minecraft expects it. */
    private static void restoreState(Minecraft mc) {
        GL30.glBindVertexArray(0);
        BufferUploader.reset();
        bindTexture(1, 0);
        bindTexture(0, 0);
        GlStateManager._glUseProgram(0);
        // Minecraft caches the last shader program it bound; invalidate that cache.
        ShaderInstance any = GameRenderer.getPositionTexShader();
        if (any != null) any.clear();
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }
}
