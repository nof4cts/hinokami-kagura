package com.sunbreathing.client.fx;

import com.sunbreathing.config.SunConfig;
import com.sunbreathing.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Reusable particle "recipes" shared by every form. All counts pass through {@link #count}, which
 * applies the client's particleDensity setting (LOW/MEDIUM/HIGH), keeping FPS predictable.
 */
public final class SunFx {
    private static final RandomSource RNG = RandomSource.create();

    private SunFx() {}

    public static int count(int high) {
        float m = SunConfig.CLIENT.particleDensity.get().multiplier;
        return Math.max(1, Math.round(high * m));
    }

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    public static void flame(Vec3 p, Vec3 v) {
        ClientLevel l = level();
        if (l != null) l.addParticle(ModParticles.SUN_FLAME.get(), p.x, p.y, p.z, v.x, v.y, v.z);
    }

    public static void ember(Vec3 p, Vec3 v) {
        ClientLevel l = level();
        if (l != null) l.addParticle(ModParticles.SUN_EMBER.get(), p.x, p.y, p.z, v.x, v.y, v.z);
    }

    public static void ray(Vec3 p, Vec3 v) {
        ClientLevel l = level();
        if (l != null) l.addParticle(ModParticles.SUN_RAY.get(), p.x, p.y, p.z, v.x, v.y, v.z);
    }

    public static void flash(Vec3 p, double size) {
        ClientLevel l = level();
        if (l != null) l.addParticle(ModParticles.SUN_FLASH.get(), p.x, p.y, p.z, size, 0, 0);
    }

    public static void glow(Vec3 p, double size) {
        ClientLevel l = level();
        if (l != null) l.addParticle(ModParticles.SUN_GLOW.get(), p.x, p.y, p.z, size, 0, 0);
    }

    public static Vec3 randomUnit() {
        double u = RNG.nextDouble() * 2 - 1;
        double th = RNG.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(th), u, s * Math.sin(th));
    }

    public static double rand(double min, double max) {
        return min + RNG.nextDouble() * (max - min);
    }

    /**
     * Layered fire along a slash arc: flames riding the blade edge (moving along the tangent),
     * sparks flung outward, embers drifting off the trail.
     */
    public static void flamesAlongArc(SlashArc arc, float from, float to, float intensity) {
        int flames = count(Math.round(26 * intensity));
        for (int i = 0; i < flames; i++) {
            float t = Mth.lerp(RNG.nextFloat(), from, to);
            Vec3 pos = arc.point(t, -arc.thickness * (float) rand(0.0, 0.5));
            Vec3 vel = arc.tangent(t).scale(rand(0.05, 0.22)).add(randomUnit().scale(0.02));
            flame(pos, vel);
        }
        int sparks = count(Math.round(10 * intensity));
        for (int i = 0; i < sparks; i++) {
            float t = Mth.lerp(RNG.nextFloat(), from, to);
            Vec3 pos = arc.point(t, 0f);
            Vec3 outward = pos.subtract(arc.center).normalize();
            Vec3 vel = arc.tangent(t).scale(rand(0.25, 0.5)).add(outward.scale(rand(0.05, 0.2)));
            ray(pos, vel);
        }
        int embers = count(Math.round(14 * intensity));
        for (int i = 0; i < embers; i++) {
            float t = Mth.lerp(RNG.nextFloat(), from, to);
            Vec3 pos = arc.point(t, -arc.thickness * (float) rand(0.0, 0.8));
            ember(pos, randomUnit().scale(0.03).add(0, 0.02, 0));
        }
    }

    /**
     * Impact burst: white-hot flash, radial light rays, a ring of flames and lingering embers.
     *
     * @param heaviness 0..1, scales size and particle counts
     */
    public static void impactBurst(Vec3 at, Vec3 slashDir, float heaviness) {
        flash(at, 0.9 + 1.4 * heaviness);
        int rays = count(8 + Math.round(10 * heaviness));
        for (int i = 0; i < rays; i++) {
            // Bias rays along the slash direction so the burst "follows through".
            Vec3 d = randomUnit().add(slashDir.scale(0.8)).normalize();
            ray(at, d.scale(rand(0.35, 0.75) * (0.8 + heaviness)));
        }
        int flames = count(10 + Math.round(14 * heaviness));
        for (int i = 0; i < flames; i++) {
            Vec3 d = randomUnit();
            flame(at.add(d.scale(0.2)), d.scale(rand(0.06, 0.18)).add(slashDir.scale(0.08)));
        }
        int embers = count(12 + Math.round(12 * heaviness));
        for (int i = 0; i < embers; i++) {
            ember(at.add(randomUnit().scale(0.3)), randomUnit().scale(rand(0.05, 0.16)).add(0, 0.05, 0));
        }
    }
}
