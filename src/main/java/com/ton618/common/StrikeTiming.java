package com.ton618.common;

/**
 * The strike timeline, in ticks from the moment the remote fires. Shared by the server (when the hole
 * appears and lands) and the client cutscene (which shot is on screen, when each sound plays).
 */
public final class StrikeTiming {
    private StrikeTiming() {}

    // --- space shots
    public static final int SHOT_WIDE = 0;          // the cannon above the Earth, coils start to wake
    public static final int SHOT_BARREL = 38;       // tracking along the barrel as the charge races up it
    public static final int SHOT_FIRE = 70;         // down the line of fire toward the planet
    public static final int FIRE = 88;              // the shot
    public static final int PROJECTILE_TICKS = 22;  // projectile travel time to the atmosphere

    // --- world shots
    public static final int HOLE_APPEARS = 110;     // the hole enters the sky above the target
    public static final int SHOT_SKY = 110;         // from near the target, looking up at it coming
    public static final int SHOT_SHOOTER = 180;     // over the shooter's shoulder
    public static final int SHOT_GROUND = 262;      // low and close: the last seconds
    public static final int IMPACT = 300;
    public static final int SHOT_AFTERMATH = 300;   // crane up and away as the world is eaten
    public static final int END = 380;

    public static final int DESCENT = IMPACT - HOLE_APPEARS;
}
