package com.shrine.common;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * One cutting stroke, as a piece of a plane: it starts at {@code origin}, runs {@code length} along
 * {@code dir} and is {@code width} wide along {@code side}; {@code normal} is the plane's normal.
 * Generated from a seed, so the server's cut and every client's drawing are the same stroke.
 */
public record Slash(Vec3 origin, Vec3 dir, Vec3 side, Vec3 normal, double length, double width) {

    /** A stroke along {@code dir}, rolled about it by {@code rollDeg}. */
    public static Slash of(Vec3 origin, Vec3 dir, double rollDeg, double length, double width) {
        dir = dir.normalize();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();
        double r = Math.toRadians(rollDeg);
        Vec3 side = right.scale(Math.cos(r)).add(up.scale(Math.sin(r)));
        Vec3 normal = dir.cross(side).normalize();
        return new Slash(origin, dir, side, normal, length, width);
    }

    /** Roll for stroke {@code i} of a cast with this seed: mostly diagonal, like a hand flicked across. */
    public static double roll(long seed, int i) {
        RandomSource r = RandomSource.create(seed * 31 + i * 7919L);
        double base = r.nextBoolean() ? 25 : 155;
        return base + (r.nextDouble() - 0.5) * 70;
    }

    /** A small aim jitter for barrage strokes. */
    public static Vec3 jitter(long seed, int i, Vec3 dir, double amount) {
        RandomSource r = RandomSource.create(seed * 131 + i * 104729L);
        Vec3 j = new Vec3(r.nextDouble() - 0.5, (r.nextDouble() - 0.5) * 0.6, r.nextDouble() - 0.5).scale(amount);
        return dir.add(j).normalize();
    }

    /** Is world point p inside this stroke's slab (thickness t), within its swept reach? */
    public boolean contains(Vec3 p, double t, double reach) {
        Vec3 d = p.subtract(origin);
        double a = d.dot(dir);
        if (a < 0 || a > Math.min(length, reach)) return false;
        if (Math.abs(d.dot(side)) > width * 0.5) return false;
        return Math.abs(d.dot(normal)) <= t;
    }
}
