package com.ton618.client.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * The space shader's coordinate frames, mirrored from space.fsh (keep in sync, and with tools/render_space.py).
 *
 * Scene 0: the cannon sits at the origin, far outside the galaxy, its barrel along AXIS, aimed at the Sun's
 *          place in the galaxy; BX/BY complete the frame.
 * Scene 2: the Earth at EARTH_C (radius 2000); the strike lands at TARGET.
 * Scene 3: the Sun at the origin (radius 1000).
 * Scene 4: the pierced star at the origin (radius 1000); the beam travels along BEAM_DIR.
 */
public final class SpaceFrame {
    private SpaceFrame() {}

    private static final double GAL_SCALE = 9000.0;
    private static final Vec3 GAL_C = new Vec3(-30000.0, 22000.0, 150000.0);
    private static final Vec3 GN = new Vec3(0.25, 1.0, -0.55).normalize();
    private static final Vec3 GX = GN.cross(new Vec3(0, 0, 1)).normalize();
    private static final Vec3 GZ = GX.cross(GN);
    private static final Vec3 SUN_G = GX.scale(Math.cos(2.35)).add(GZ.scale(Math.sin(2.35))).scale(8.5);

    public static final Vec3 AXIS = GAL_C.add(SUN_G.scale(GAL_SCALE)).normalize();
    public static final Vec3 BX = new Vec3(0, 1, 0).cross(AXIS).normalize();
    public static final Vec3 BY = AXIS.cross(BX);

    public static final Vec3 EARTH_C = new Vec3(0.0, -1750.0, 2550.0);
    public static final double EARTH_R = 2000.0;
    public static final Vec3 TARGET_UP = new Vec3(0.05, 1.0, -0.62).normalize();
    public static final Vec3 TARGET = EARTH_C.add(TARGET_UP.scale(EARTH_R));
    public static final Vec3 SUN_DIR = new Vec3(0.78, 0.52, -0.28).normalize();

    public static final Vec3 BEAM_DIR = new Vec3(0.6, -0.25, 0.76).normalize();

    /** A point in the cannon's frame: a along the barrel, x to its side, y over it. */
    public static Vec3 at(double a, double x, double y) {
        return AXIS.scale(a).add(BX.scale(x)).add(BY.scale(y));
    }
}
