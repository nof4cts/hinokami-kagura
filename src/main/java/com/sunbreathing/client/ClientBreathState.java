package com.sunbreathing.client;

import com.sunbreathing.forms.Form;
import com.sunbreathing.network.S2CSyncBreath;
import net.minecraft.util.Mth;

/**
 * The local player's breath state as last reported by the server, plus smoothing for the HUD.
 * Cooldowns are counted down locally between syncs so the HUD animates every tick without
 * the server having to send a packet per tick.
 */
public final class ClientBreathState {
    public static float breath = 100f;
    public static float maxBreath = 100f;
    public static boolean concentration;
    public static Form selected = Form.DANCE;
    public static final int[] cooldowns = new int[Form.COUNT];
    /** Cooldown length at the moment it started, so the HUD can draw a fraction. */
    public static final int[] cooldownTotals = new int[Form.COUNT];

    /** Smoothed values for rendering. */
    private static float displayBreath = 100f, prevDisplayBreath = 100f;
    /** 0..1 eased Total Concentration intensity (for HUD glow). */
    private static float concentrationFade, prevConcentrationFade;
    /** Bumped whenever breath is spent, so the HUD can flash. */
    public static int spendFlashTicks;

    private ClientBreathState() {}

    public static void apply(S2CSyncBreath msg) {
        if (msg.breath < breath - 2f) spendFlashTicks = 6; // a real spend, not upkeep drift
        breath = msg.breath;
        maxBreath = Math.max(1f, msg.maxBreath);
        concentration = msg.concentration;
        selected = Form.byIndex(msg.selected);
        for (int i = 0; i < Math.min(msg.cooldowns.length, cooldowns.length); i++) {
            if (msg.cooldowns[i] > cooldowns[i] + 1) cooldownTotals[i] = msg.cooldowns[i];
            cooldowns[i] = msg.cooldowns[i];
        }
    }

    public static void tick() {
        for (int i = 0; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        prevDisplayBreath = displayBreath;
        displayBreath += (breath - displayBreath) * 0.35f;
        prevConcentrationFade = concentrationFade;
        concentrationFade = Mth.approach(concentrationFade, concentration ? 1f : 0f, 0.12f);
        if (spendFlashTicks > 0) spendFlashTicks--;
    }

    public static float displayBreath(float partial) {
        return Mth.lerp(partial, prevDisplayBreath, displayBreath);
    }

    public static float concentrationFade(float partial) {
        return Mth.lerp(partial, prevConcentrationFade, concentrationFade);
    }

    public static float cooldownFraction(Form f) {
        int total = cooldownTotals[f.ordinal()];
        return total <= 0 ? 0f : Mth.clamp(cooldowns[f.ordinal()] / (float) total, 0f, 1f);
    }

    public static void reset() {
        breath = maxBreath = displayBreath = prevDisplayBreath = 100f;
        concentration = false;
        concentrationFade = prevConcentrationFade = 0f;
        selected = Form.DANCE;
        java.util.Arrays.fill(cooldowns, 0);
        java.util.Arrays.fill(cooldownTotals, 0);
    }
}
