package com.ton618.common;

/**
 * The strike timelines, in ticks from the moment the remote fires. Shared by the server (when the body
 * appears, lands, and does its damage) and the client cutscene director (which shot is on screen, every
 * sound and impact frame).
 */
public final class StrikeTiming {
    private StrikeTiming() {}

    // ---------------------------------------------------------------- TON 618
    public static final int H_WIDE = 0;            // the cannon, beyond the galaxy; coils wake
    public static final int H_BARREL = 40;         // along the barrel as the charge races up it
    public static final int H_FIRE_SHOT = 72;      // from behind: the shot
    public static final int H_FIRE = 84;
    public static final int H_CROSS = 98;          // riding the beam across the galaxy
    public static final int H_PIERCE = 138;        // the beam runs a star through        (P 0 .. 0.6)
    public static final int H_PIERCE_HIT = 141;    //   the moment it strikes
    public static final int H_COLLAPSE = 196;      //   the star implodes                  (P = 0.6)
    public static final int H_BIRTH = 200;         // behind the newborn hole as it spins up (P 0.6 .. 1)
    public static final int H_LAUNCH = 228;        //   the second pulse drives it out     (P = 0.86)
    public static final int H_HOME = 244;          // it falls across the galaxy toward us
    public static final int H_EARTH = 266;         // over the Earth: it comes down through the atmosphere
    public static final int H_APPEAR = 300;        // cut to the world: in the sky above the target
    public static final int H_SHOOTER = 362;
    public static final int H_GROUND = 418;
    public static final int H_IMPACT = 452;
    public static final int H_END = 545;

    // ---------------------------------------------------------------- the Sun
    public static final int S_WIDE = 0;            // the cannon wakes
    public static final int S_FIRE_SHOT = 34;
    public static final int S_FIRE = 46;
    public static final int S_CROSS = 60;          // the beam crosses to the Sun
    public static final int S_TEAR = 96;           // the Sun, seized, is torn from its place
    public static final int S_TEAR_HIT = 99;
    public static final int S_FALL = 156;          // over the Earth: the Sun swells in the sky
    public static final int S_APPEAR = 222;        // cut to the world
    public static final int S_SHOOTER = 286;
    public static final int S_GROUND = 336;
    public static final int S_IMPACT = 372;
    public static final int S_END = 470;

    // ---------------------------------------------------------------- the Archangel
    public static final int A_HEAVENS = 0;         // over the Earth: the heavens open above the target
    public static final int A_CLOSE = 52;
    public static final int A_APPEAR = 92;         // cut to the world: it descends the pillar of light
    public static final int A_LAND = 168;          // it comes to rest above the ground
    public static final int A_HERO = 172;          // low, looking up at it
    public static final int A_RAISE = 176;         // the sword goes up
    public static final int A_RAISED = 206;
    public static final int A_SWING = 216;         // and comes down
    public static final int A_SLASH = 229;         // contact: the world splits
    public static final int A_END = 340;

    public static int appear(StrikeKind k) {
        return switch (k) {
            case HOLE -> H_APPEAR;
            case SUN -> S_APPEAR;
            case ANGEL -> A_APPEAR;
        };
    }

    /** Ticks from the strike until the body reaches the ground (end of its descent). */
    public static int land(StrikeKind k) {
        return switch (k) {
            case HOLE -> H_IMPACT;
            case SUN -> S_IMPACT;
            case ANGEL -> A_LAND;
        };
    }

    /** Ticks from the strike until the blow lands and destruction begins. */
    public static int impact(StrikeKind k) {
        return switch (k) {
            case HOLE -> H_IMPACT;
            case SUN -> S_IMPACT;
            case ANGEL -> A_SLASH;
        };
    }

    public static int end(StrikeKind k) {
        return switch (k) {
            case HOLE -> H_END;
            case SUN -> S_END;
            case ANGEL -> A_END;
        };
    }
}
