package com.ton618.client;

import com.ton618.config.Ton618Config;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Time-based screen effects driven by events (impacts) and by the approaching hole:
 * flash + afterimage, an expanding shockwave ring anchored to a world position, chromatic
 * aberration pulses, desaturation and trauma-based camera shake. All decay in real time, so
 * they stay smooth at any frame rate.
 */
public final class ScreenFx {
    private static float flash;
    private static float afterimage;
    private static float trauma;
    private static float aberration;
    private static float desaturate;
    private static double shockStart = -1;
    private static Vec3 shockAt = Vec3.ZERO;
    private static float shockStrength;
    private static long last = System.nanoTime();

    /** Sustained shake/aberration from an approaching hole, set every frame. */
    private static float ambientShake;

    // ------------------------------------------------------------------ impact frames
    /** Impact frame modes (composite.fsh). */
    public static final int INK = 1, NEGATIVE = 2, TWO_TONE = 3, HALFTONE = 4, WHITE_OUT = 5;

    /** A sequence of stark frames: {mode, milliseconds} pairs. Each step re-rolls the speed lines. */
    public enum Hit {
        /** a single sharp beat (a cut, a shot fired) */
        LIGHT(new int[][]{{INK, 45}, {NEGATIVE, 45}}),
        /** a big beat (a star pierced, the blade coming down) */
        HEAVY(new int[][]{{WHITE_OUT, 35}, {INK, 55}, {NEGATIVE, 45}, {TWO_TONE, 60}, {INK, 40}, {NEGATIVE, 70}}),
        /** the end of the world (every final impact) */
        ULTIMATE(new int[][]{{WHITE_OUT, 50}, {INK, 70}, {NEGATIVE, 50}, {TWO_TONE, 70}, {INK, 45}, {HALFTONE, 70},
                {NEGATIVE, 50}, {TWO_TONE, 85}, {INK, 50}, {WHITE_OUT, 40}, {NEGATIVE, 160}});

        final int[][] steps;
        final int total;

        Hit(int[][] steps) {
            this.steps = steps;
            int t = 0;
            for (int[] st : steps) t += st[1];
            this.total = t;
        }
    }

    private static Hit hit;
    private static double hitStart = -1;
    private static Vec3 hitWorld;
    private static float hitU = 0.5f, hitV = 0.5f;
    private static float[] accent = {1f, 0.2f, 0.05f};
    private static float pendingFlash;
    private static double pendingFlashAt = -1;
    private static float punch;
    private static int hitSalt;

    /**
     * Starts an impact frame sequence centred on a world position (projected every frame), or on a fixed
     * screen point when {@code world} is null (space shots). Accent colours the two-tone/halftone frames.
     */
    public static void hit(Hit kind, Vec3 world, float u, float v, float r, float g, float b) {
        hit = kind;
        hitStart = seconds();
        hitWorld = world;
        hitU = u;
        hitV = v;
        accent = new float[]{r, g, b};
        hitSalt = (int) (Math.random() * 1000);
        punch = Math.max(punch, kind == Hit.LIGHT ? 0.5f : 1f);
        trauma = Math.min(1f, trauma + (kind == Hit.LIGHT ? 0.25f : kind == Hit.HEAVY ? 0.55f : 0.9f));
    }

    /** Current impact frame: {mode, amount, seed} or null. */
    public static float[] impactFrame() {
        if (hit == null || hitStart < 0) return null;
        double ms = (seconds() - hitStart) * 1000.0;
        int acc = 0;
        for (int i = 0; i < hit.steps.length; i++) {
            int[] st = hit.steps[i];
            if (ms < acc + st[1]) {
                float amount = 1f;
                if (i == hit.steps.length - 1) amount = 1f - (float) ((ms - acc) / st[1]); // the last one fades out
                return new float[]{st[0], amount, i * 17 + hitSalt};
            }
            acc += st[1];
        }
        hit = null;
        return null;
    }

    public static Vec3 impactWorld() {
        return hitWorld;
    }

    public static float impactU() {
        return hitU;
    }

    public static float impactV() {
        return hitV;
    }

    public static float[] accent() {
        return accent;
    }

    public static float punch() {
        return punch * Ton618Config.CLIENT.cameraShake.get().floatValue();
    }

    /** A flash that goes off when the current impact frames finish. */
    public static void flashAfterHit(float amount) {
        pendingFlash = Math.max(pendingFlash, amount);
        pendingFlashAt = hitStart + (hit == null ? 0 : hit.total / 1000.0);
    }

    private ScreenFx() {}

    private static double seconds() {
        return System.nanoTime() / 1.0e9;
    }

    /** The blow has landed. Strength falls off with distance from the impact. */
    public static void impact(Vec3 at, float rs, float r, float g, float b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        float k = Mth.clamp(1f - (float) (dist / (900.0 + rs * 30.0)), 0.15f, 1f);
        hit(Hit.ULTIMATE, at, 0.5f, 0.5f, r, g, b);
        flashAfterHit(k);
        afterimage = Math.max(afterimage, k);
        trauma = Math.min(1f, trauma + k);
        aberration = Math.max(aberration, k);
        desaturate = Math.max(desaturate, 0.6f * k);
        shockStart = seconds();
        shockAt = at;
        shockStrength = k;
    }

    /** A hard cut through white (e.g. dropping from orbit into the world). */
    public static void cutFlash(float amount) {
        flash = Math.max(flash, amount);
    }

    public static void setAmbient(float shake) {
        ambientShake = shake;
    }

    public static boolean active() {
        return flash > 0.001f || afterimage > 0.001f || trauma > 0.001f || aberration > 0.001f
                || desaturate > 0.001f || shockStart >= 0 || hit != null || punch > 0.001f || pendingFlashAt >= 0;
    }

    /** Advance decays; call once per rendered frame. */
    public static void update() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        flash = Math.max(0f, flash - dt * 1.4f);
        afterimage = Math.max(0f, afterimage - dt * 0.35f);
        trauma = Math.max(0f, trauma - dt * 0.33f);
        aberration = Math.max(0f, aberration - dt * 0.6f);
        desaturate = Math.max(0f, desaturate - dt * 0.12f);
        if (shockStart >= 0 && seconds() - shockStart > 4.0) shockStart = -1;
        punch = Math.max(0f, punch - dt * (hit != null ? 0.6f : 3.0f));
        if (pendingFlashAt >= 0 && seconds() >= pendingFlashAt) {
            flash = Math.max(flash, pendingFlash);
            pendingFlash = 0f;
            pendingFlashAt = -1;
        }
    }

    public static float flash() {
        float f = Ton618Config.CLIENT.flash.get().floatValue();
        float x = flash;
        return x * x * f;
    }

    public static float afterimage() {
        return afterimage * Ton618Config.CLIENT.flash.get().floatValue();
    }

    public static float aberration(float base) {
        return (base + aberration * 1.6f + ambientShake * 0.8f) * Ton618Config.CLIENT.aberration.get().floatValue();
    }

    public static float desaturate() {
        return desaturate;
    }

    /** Shockwave radius (screen heights) and strength; strength 0 when inactive. */
    public static float shockRadius() {
        return shockStart < 0 ? 0f : (float) ((seconds() - shockStart) * 0.75);
    }

    public static float shockStrength() {
        if (shockStart < 0) return 0f;
        float age = (float) (seconds() - shockStart);
        return shockStrength * Math.max(0f, 1f - age / 3.0f);
    }

    public static Vec3 shockAt() {
        return shockAt;
    }

    private static float noise(double t, int seed) {
        return (float) (Math.sin(t * 1.0 + seed * 12.9898) * 0.55 + Math.sin(t * 2.31 + seed * 7.233) * 0.30
                + Math.sin(t * 5.17 + seed * 3.719) * 0.15);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float s = Math.min(1f, trauma + ambientShake);
        if (s <= 0.001f) return;
        s = s * s * Ton618Config.CLIENT.cameraShake.get().floatValue();
        double t = seconds() * 26.0;
        event.setYaw(event.getYaw() + s * 3.2f * noise(t, 1));
        event.setPitch(event.getPitch() + s * 2.6f * noise(t, 2));
        event.setRoll(event.getRoll() + s * 4.5f * noise(t * 0.7, 3));
    }

    public static void reset() {
        flash = afterimage = trauma = aberration = desaturate = ambientShake = punch = pendingFlash = 0f;
        shockStart = -1;
        pendingFlashAt = -1;
        hit = null;
    }
}
