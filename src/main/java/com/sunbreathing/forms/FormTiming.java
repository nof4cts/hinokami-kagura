package com.sunbreathing.forms;

/**
 * Shared timeline constants (ticks, 20 per second) so the server's hit windows, the client's
 * slash meshes and the Player Animator keyframes line up. If you retime an animation in
 * Blockbench, update these numbers to match. Client arcs start one tick before each hit.
 */
public final class FormTiming {
    private FormTiming() {}

    // I. Dance (form_dance, 0.6s)
    public static final int DANCE_LUNGE_TICK = 1;
    public static final int DANCE_ARC_TICK = 2;
    public static final int DANCE_HIT_TICK = 3;

    // II. Clear Blue Sky (0.7s): hop, two rising circular slashes
    public static final int[] CLEAR_SKY_HITS = {3, 6};

    // III. Raging Sun (0.8s): one huge sweep
    public static final int RAGING_SUN_HIT = 5;

    // IV. Burning Bones, Summer Sun (1.2s): five spinning cuts
    public static final int[] BURNING_BONES_HITS = {3, 7, 11, 15, 19};

    // V. Setting Sun Transformation (0.6s): dash, cut at the end of the dash
    public static final int SETTING_SUN_DASH = 1;
    public static final int SETTING_SUN_HIT = 5;

    // VI. Solar Heat Haze (0.5s): instant dodge, decoy stays behind
    public static final int HEAT_HAZE_INVULN_TICKS = 16;

    // VII. Beneficent Radiance (0.8s): overhead lunge, burst of light
    public static final int RADIANCE_LUNGE = 2;
    public static final int RADIANCE_HIT = 6;

    // VIII. Sunflower Thrust (0.5s)
    public static final int SUNFLOWER_HIT = 4;

    // IX. Dragon Sun Halo Head Dance (1.0s): leap, spiral of four cuts in the air
    public static final int[] DRAGON_HITS = {4, 8, 12, 16};

    // X. Fire Wheel (0.8s): forward somersault, wheel slams down
    public static final int FIRE_WHEEL_HIT = 8;

    // XI. Fake Rainbow (1.0s): six weaving cuts with sidesteps
    public static final int[] FAKE_RAINBOW_HITS = {2, 5, 8, 11, 14, 17};

    // XII. Flame Dance: chains I -> III -> X -> VII, then a finale
    public static final int FLAME_CHAIN_RAGING = 10;
    public static final int FLAME_CHAIN_WHEEL = 24;
    public static final int FLAME_CHAIN_RADIANCE = 38;
    public static final int FLAME_FINALE = 50;
    public static final int FLAME_FINALE_HIT = 56;
}
