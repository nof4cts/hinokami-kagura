package com.ton618.common;

import net.minecraft.util.Mth;

/**
 * The Archangel's performance, as smooth functions of time (ticks since the strike, fractional), so every
 * client animates it identically and at any frame rate. Animated with real animation principles:
 * anticipation (the sword dips back before it rises, and coils further back before the blow), slow-in /
 * slow-out on every hold, a fast accelerating strike, follow-through past the contact point, overlapping
 * action (wings and body lag the arm), and a constant breathing hover so it is never perfectly still.
 */
public final class AngelAnim {
    private AngelAnim() {}

    /** Everything the shader needs for one frame. swordT: 0 = raised high overhead, 1 = brought fully down. */
    public record State(float swordT, float slashAge, float lean, float wingBeat, float hover, float glow) {}

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    private static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    public static State at(float t) {
        final float raise = StrikeTiming.A_RAISE, raised = StrikeTiming.A_RAISED;
        final float swing = StrikeTiming.A_SWING, slash = StrikeTiming.A_SLASH;

        // ---- sword: rest low -> anticipation dip -> raise with overshoot -> settle -> coil -> strike -> follow-through -> recover
        float sword;
        if (t < raise) {
            sword = 0.92f;                                                        // held low, blade forward
        } else if (t < raised) {
            float u = span(t, raise, raised);
            float dip = Mth.sin(Math.min(u / 0.18f, 1f) * Mth.PI) * 0.06f;        // a small dip forward first
            float up = smooth(span(u, 0.12f, 1f));
            float over = Mth.sin(up * Mth.PI) * 0.0f;
            sword = Mth.lerp(up, 0.92f + dip, -0.08f) + over;                      // overshoots past overhead
        } else if (t < swing) {
            float u = span(t, raised, swing);
            sword = Mth.lerp(smooth(u), -0.08f, -0.16f);                           // coil back further, gathering
        } else if (t < slash) {
            float u = span(t, swing, slash);
            sword = Mth.lerp(u * u * u, -0.16f, 1.0f);                             // accelerates all the way in
        } else {
            float a = (t - slash);
            float follow = 0.08f * Mth.sin(Math.min(a / 6f, 1f) * Mth.PI * 0.5f) * Mth.clamp(1f - (a - 6f) / 40f, 0f, 1f);
            sword = 1.0f + follow;                                                 // follow-through, then holds
        }

        // ---- body lean: leans back into the raise, throws forward with the strike (lags the arm slightly)
        float lean;
        if (t < raise) lean = 0f;
        else if (t < swing) lean = -0.10f * smooth(span(t, raise, raised));
        else if (t < slash + 3) lean = Mth.lerp(smooth(span(t, swing + 2, slash + 3)), -0.12f, 0.22f);
        else lean = Mth.lerp(smooth(span(t, slash + 3, slash + 50)), 0.22f, 0.08f);

        // ---- wings: big slow beats while descending, a powerful downbeat on the strike, then slow
        float wing;
        if (t < StrikeTiming.A_LAND) wing = t / 34f;
        else if (t < swing) wing = StrikeTiming.A_LAND / 34f + (t - StrikeTiming.A_LAND) / 60f;
        else wing = StrikeTiming.A_LAND / 34f + (swing - StrikeTiming.A_LAND) / 60f
                + smooth(span(t, swing, slash + 6)) * 0.5f + Math.max(0f, t - slash - 6) / 70f;

        // ---- hover: gentle breathing bob, and a dip as it puts its weight into the blow
        float hover = Mth.sin(t / 20f * 1.7f) * 0.15f - 0.35f * smooth(span(t, swing, slash)) * (1f - smooth(span(t, slash, slash + 40)));

        // ---- the sword's fire builds while raised and blazes on the strike
        float glow = 0.5f + 0.5f * smooth(span(t, raise, raised)) + 1.0f * smooth(span(t, swing, slash))
                - 0.6f * smooth(span(t, slash + 10, slash + 60));

        float slashAge = (t - slash) / 20f;
        return new State(sword, slashAge, lean, wing, hover, glow);
    }
}
