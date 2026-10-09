package com.ton618.client.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * The orbit shader's coordinate frame, mirrored from orbit.fsh (EARTH_C). The cannon sits at the
 * origin with its barrel along AXIS, pointing at the Earth's centre; BX/BY complete the frame.
 * Keep these in sync with the shader (and tools/render_orbit.py).
 */
public final class SpaceFrame {
    private SpaceFrame() {}

    public static final Vec3 EARTH_C = new Vec3(0.0, -1750.0, 2550.0);
    public static final Vec3 AXIS = EARTH_C.normalize();
    public static final Vec3 BX = new Vec3(0, 1, 0).cross(AXIS).normalize();
    public static final Vec3 BY = AXIS.cross(BX);

    /** A point in the cannon's frame: a along the barrel, x to its side, y over it. */
    public static Vec3 at(double a, double x, double y) {
        return AXIS.scale(a).add(BX.scale(x)).add(BY.scale(y));
    }
}
