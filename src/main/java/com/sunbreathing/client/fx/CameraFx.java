package com.sunbreathing.client.fx;

import com.sunbreathing.config.SunConfig;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Trauma-based camera shake and FOV punch.
 *
 * Shake: impacts add "trauma" (0..1); the actual offset is trauma^2 times smooth noise, so small
 * hits barely move the view and heavy hits really kick. Trauma decays linearly over ~0.6s.
 * FOV punch: a quick zoom-in that eases back out over ~0.2s, only for heavy hits.
 */
public final class CameraFx {
    private static float trauma;
    private static float fovPunch;
    private static long lastNanos = System.nanoTime();

    private static final float MAX_YAW = 2.6f, MAX_PITCH = 2.2f, MAX_ROLL = 3.5f;

    private CameraFx() {}

    public static void addTrauma(float amount) {
        float scale = SunConfig.CLIENT.cameraShake.get().floatValue();
        trauma = Mth.clamp(trauma + amount * scale, 0f, 1f);
    }

    public static void punchFov(float amount) {
        if (SunConfig.CLIENT.fovPunch.get()) fovPunch = Math.max(fovPunch, Mth.clamp(amount, 0f, 1f));
    }

    private static float noise(double t, int seed) {
        return (float) (Math.sin(t * 1.00 + seed * 12.9898) * 0.55
                + Math.sin(t * 2.31 + seed * 7.233) * 0.30
                + Math.sin(t * 5.17 + seed * 3.719) * 0.15);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        trauma = Math.max(0f, trauma - dt * 1.7f);
        fovPunch = Math.max(0f, fovPunch - dt * 5.0f);

        if (trauma <= 0f) return;
        float s = trauma * trauma;
        double t = now / 1.0e9 * 32.0;
        event.setYaw(event.getYaw() + s * MAX_YAW * noise(t, 1));
        event.setPitch(event.getPitch() + s * MAX_PITCH * noise(t, 2));
        event.setRoll(event.getRoll() + s * MAX_ROLL * noise(t * 0.8, 3));
    }

    public static void onFov(ViewportEvent.ComputeFov event) {
        if (fovPunch <= 0f || !event.usedConfiguredFov()) return;
        // Ease-out: strongest right after impact.
        float k = fovPunch * fovPunch;
        event.setFOV(event.getFOV() * (1.0 - 0.07 * k));
    }

    public static void reset() {
        trauma = 0f;
        fovPunch = 0f;
    }
}
