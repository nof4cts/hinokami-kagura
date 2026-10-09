package com.sunbreathing.client.fx;

/**
 * Millisecond clock for VFX that can be paused ("hit-stop").
 *
 * A server can't freeze game ticks for one client without desyncing everyone, so hit-stop is a
 * local presentation trick: on impact this clock stops for 2-4 frames, which freezes slash meshes
 * mid-sweep and (via a SpeedModifier) the attacker's body animation. Real game time keeps running.
 */
public final class VfxClock {
    private static double frozenTotal;
    private static double freezeStart = -1;
    private static double freezeEnd;

    private VfxClock() {}

    private static double real() {
        return System.nanoTime() / 1.0e6;
    }

    private static void update() {
        if (freezeStart >= 0 && real() >= freezeEnd) {
            frozenTotal += freezeEnd - freezeStart;
            freezeStart = -1;
        }
    }

    /** Freeze for {@code ms} milliseconds (extends an ongoing freeze instead of stacking). */
    public static void freeze(double ms) {
        double now = real();
        update();
        if (freezeStart < 0) {
            freezeStart = now;
            freezeEnd = now + ms;
        } else {
            freezeEnd = Math.max(freezeEnd, now + ms);
        }
    }

    public static boolean isFrozen() {
        update();
        return freezeStart >= 0;
    }

    /** Pausable time in ms. */
    public static double now() {
        update();
        return (freezeStart >= 0 ? freezeStart : real()) - frozenTotal;
    }
}
