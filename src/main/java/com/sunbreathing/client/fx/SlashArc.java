package com.sunbreathing.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A procedural slash: a crescent ribbon swept along a circular arc.
 *
 * Geometry: points p(theta) = center + radius * (forward*cos(theta) + swing*sin(theta)),
 * with theta going from +halfArc (start) to -halfArc (end). "swing" is the right vector rolled
 * around "forward", so roll=0 gives a flat horizontal slash and roll=35 a diagonal one.
 *
 * Timing (in pausable {@link VfxClock} ms): the head sweeps start->end in sweepMs; the tail
 * starts chasing it shortly after; the whole thing fades out by lifeMs.
 */
public final class SlashArc {
    public final Vec3 center;
    public final Vec3 forward;
    public final Vec3 swing;
    public final float radius;
    public final float thickness;
    public final float halfArc;
    public final double startMs;
    public final float sweepMs;
    public final float lifeMs;
    /** Base tint of the core pass (the shader whitens it toward the cutting edge). */
    public final float r, g, b;

    public SlashArc(Vec3 center, Vec3 forward, Vec3 swing, float radius, float thickness, float arcDegrees,
                    float sweepMs, float lifeMs, float r, float g, float b) {
        this.center = center;
        this.forward = forward;
        this.swing = swing;
        this.radius = radius;
        this.thickness = thickness;
        this.halfArc = arcDegrees * 0.5f * Mth.DEG_TO_RAD;
        this.startMs = VfxClock.now();
        this.sweepMs = sweepMs;
        this.lifeMs = lifeMs;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    /**
     * Builds the basis for an arc in front of a viewer.
     *
     * @param yaw     facing yaw (degrees)
     * @param pitch   facing pitch (degrees); scaled down so arcs stay readable when looking up/down
     * @param rollDeg rotation of the swing plane around forward (positive = start high on the right)
     */
    public static SlashArc facing(Vec3 center, float yaw, float pitch, float rollDeg, float radius, float thickness,
                                  float arcDegrees, float sweepMs, float lifeMs, float r, float g, float b) {
        Vec3 fwd = Vec3.directionFromRotation(pitch * 0.4f, yaw).normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = fwd.cross(worldUp);
        if (right.lengthSqr() < 1.0e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(fwd).normalize();
        float roll = rollDeg * Mth.DEG_TO_RAD;
        Vec3 swing = right.scale(Mth.cos(roll)).add(up.scale(Mth.sin(roll)));
        return new SlashArc(center, fwd, swing, radius, thickness, arcDegrees, sweepMs, lifeMs, r, g, b);
    }

    public double age(double now) {
        return now - startMs;
    }

    public boolean expired(double now) {
        return age(now) > lifeMs;
    }

    /** Head position along the arc 0..1. Ease-out so the blade "snaps" through. */
    public float head(double now) {
        float x = Mth.clamp((float) (age(now) / sweepMs), 0f, 1f);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    /** Tail position along the arc 0..1; starts moving once the head is ~35% through. */
    public float tail(double now) {
        float x = Mth.clamp((float) ((age(now) - sweepMs * 0.35f) / (sweepMs * 1.8f)), 0f, 1f);
        return x * x;
    }

    /** Global alpha: full during the sweep, fading out afterwards. */
    public float alpha(double now) {
        double a = age(now);
        double fadeStart = sweepMs * 1.1;
        if (a <= fadeStart) return 1f;
        float x = Mth.clamp((float) ((a - fadeStart) / (lifeMs - fadeStart)), 0f, 1f);
        return 1f - x * x;
    }

    /** World position on the arc at parameter t (0 = start, 1 = end) and radius offset. */
    public Vec3 point(float t, float radiusOffset) {
        float theta = Mth.lerp(t, halfArc, -halfArc);
        float rad = radius + radiusOffset;
        return center.add(forward.scale(Mth.cos(theta) * rad)).add(swing.scale(Mth.sin(theta) * rad));
    }

    /** Unit tangent (direction of travel) at t. */
    public Vec3 tangent(float t) {
        float theta = Mth.lerp(t, halfArc, -halfArc);
        // d/dtheta of p, negated because theta decreases as t increases
        return forward.scale(Mth.sin(theta)).subtract(swing.scale(Mth.cos(theta))).normalize();
    }
}
