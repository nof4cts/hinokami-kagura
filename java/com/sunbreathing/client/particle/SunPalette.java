package com.sunbreathing.client.particle;

import net.minecraft.util.Mth;

/**
 * The one palette every effect samples, so flames, slashes, HUD and blade glow all agree:
 * white-hot core -> gold -> burning orange -> deep red.
 */
public final class SunPalette {
    private SunPalette() {}

    private static final float[] SCRATCH = new float[3];

    private static final float[][] STOPS = {
            {0.00f, 1.00f, 0.97f, 0.90f}, // white-hot
            {0.22f, 1.00f, 0.80f, 0.30f}, // gold
            {0.50f, 1.00f, 0.45f, 0.08f}, // burning orange
            {0.80f, 0.78f, 0.12f, 0.04f}, // crimson
            {1.00f, 0.42f, 0.04f, 0.03f}, // deep red / ember
    };

    /** Writes r,g,b for heat position t (0 = hottest, 1 = coolest) into out[0..2]. */
    public static void ramp(float t, float[] out) {
        t = Mth.clamp(t, 0f, 1f);
        for (int i = 1; i < STOPS.length; i++) {
            if (t <= STOPS[i][0]) {
                float[] a = STOPS[i - 1], b = STOPS[i];
                float k = (t - a[0]) / (b[0] - a[0]);
                out[0] = Mth.lerp(k, a[1], b[1]);
                out[1] = Mth.lerp(k, a[2], b[2]);
                out[2] = Mth.lerp(k, a[3], b[3]);
                return;
            }
        }
        float[] last = STOPS[STOPS.length - 1];
        out[0] = last[1];
        out[1] = last[2];
        out[2] = last[3];
    }

    /** Packed ARGB for GUI drawing. */
    public static int argb(float t, float alpha) {
        float[] c = SCRATCH; // render thread only; avoids allocating per HUD column
        ramp(t, c);
        return ((int) (Mth.clamp(alpha, 0f, 1f) * 255) << 24) | ((int) (c[0] * 255) << 16)
                | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }
}
