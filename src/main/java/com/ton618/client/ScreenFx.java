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

    private ScreenFx() {}

    private static double seconds() {
        return System.nanoTime() / 1.0e9;
    }

    /** The hole has arrived. Strength falls off with distance from the impact. */
    public static void impact(Vec3 at, float rs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.position().distanceTo(at);
        float k = Mth.clamp(1f - (float) (dist / (600.0 + rs * 30.0)), 0.15f, 1f);
        flash = Math.max(flash, k);
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
                || desaturate > 0.001f || shockStart >= 0;
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
        flash = afterimage = trauma = aberration = desaturate = ambientShake = 0f;
        shockStart = -1;
    }
}
