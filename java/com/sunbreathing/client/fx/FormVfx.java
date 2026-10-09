package com.sunbreathing.client.fx;

import com.sunbreathing.client.anim.PlayerAnims;
import com.sunbreathing.client.particle.SunPalette;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.forms.Form;
import com.sunbreathing.forms.FormTiming;
import com.sunbreathing.network.S2CFormCast;
import com.sunbreathing.network.S2CImpact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client presentation for every form: body animation, slash meshes, afterimages and particles,
 * all keyed off the server's cast packet, plus the shared impact response (flash, hit-stop,
 * shake, FOV punch).
 *
 * Arcs are spawned from the player's position AT SPAWN TIME (not the cast position), so slashes
 * stay glued to the body during dashes and leaps. Each arc spawns one tick before the matching
 * server hit in {@link FormTiming}.
 */
public final class FormVfx {
    private FormVfx() {}

    // palette shortcuts for arc core tints
    private static final float[] ORANGE = {1.0f, 0.42f, 0.08f};
    private static final float[] GOLD = {1.0f, 0.68f, 0.18f};
    private static final float[] CRIMSON = {0.9f, 0.16f, 0.05f};

    public static void onCast(S2CFormCast msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId);
        if (!(e instanceof AbstractClientPlayer player)) return;
        Form form = Form.byIndex(msg.form);

        PlayerAnims.play(player, form);

        final float yaw = msg.yaw, pitch = msg.pitch;
        switch (form) {
            case DANCE -> dance(player, yaw, pitch);
            case CLEAR_BLUE_SKY -> clearBlueSky(player, yaw);
            case RAGING_SUN -> ragingSun(player, yaw, pitch);
            case BURNING_BONES -> burningBones(player, yaw);
            case SETTING_SUN -> settingSun(player, yaw);
            case SOLAR_HEAT_HAZE -> solarHeatHaze(player, msg);
            case BENEFICENT_RADIANCE -> beneficentRadiance(player, yaw, pitch);
            case SUNFLOWER_THRUST -> sunflowerThrust(player, yaw, pitch);
            case DRAGON_SUN_HALO -> dragonSunHalo(player, yaw);
            case FIRE_WHEEL -> fireWheel(player, yaw);
            case FAKE_RAINBOW -> fakeRainbow(player, yaw, pitch);
            case FLAME_DANCE -> flameDanceFinale(player, yaw);
        }
    }

    // ================================================================== helpers
    private static Vec3 chest(AbstractClientPlayer p) {
        return p.position().add(0, p.getBbHeight() * 0.62, 0);
    }

    private static Vec3 fwd(float yaw) {
        return Vec3.directionFromRotation(0f, yaw);
    }

    private static Vec3 right(float yaw) {
        return fwd(yaw).cross(new Vec3(0, 1, 0)).normalize();
    }

    /** Spawns an arc around the player (when the scheduled tick arrives) plus fire along it. */
    private static void arcAt(int tick, AbstractClientPlayer p, float yaw, float pitch, float roll, float radius,
                              float thickness, float arcDeg, float sweepMs, float lifeMs, float[] tint,
                              float fire, Vec3 offset) {
        ClientScheduler.schedule(tick, () -> {
            if (p.isRemoved()) return;
            Vec3 c = chest(p).add(offset);
            SlashArc arc = SlashArc.facing(c, yaw, pitch, roll, radius, thickness, arcDeg, sweepMs, lifeMs,
                    tint[0], tint[1], tint[2]);
            SlashArcRenderer.add(arc);
            if (fire > 0f) SunFx.flamesAlongArc(arc, 0f, 1f, fire);
        });
    }

    private static void ringOfFlame(Vec3 centre, double radius, int count, double rise) {
        int n = SunFx.count(count);
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            SunFx.flame(centre.add(dir.scale(radius)), dir.scale(0.05).add(0, rise, 0));
        }
    }

    // ================================================================== I. Dance
    private static void dance(AbstractClientPlayer p, float yaw, float pitch) {
        Vec3 hand = chest(p).add(right(yaw).scale(0.4)).add(fwd(yaw).scale(0.3));
        for (int i = 0; i < SunFx.count(6); i++) SunFx.ember(hand.add(SunFx.randomUnit().scale(0.25)), new Vec3(0, 0.03, 0));
        Vec3 off = fwd(yaw).scale(0.15);
        arcAt(FormTiming.DANCE_ARC_TICK, p, yaw, pitch, 38f, 2.35f, 0.95f, 165f, 120f, 430f, ORANGE, 1.0f, off);
        arcAt(FormTiming.DANCE_ARC_TICK, p, yaw, pitch, 30f, 1.75f, 0.45f, 140f, 150f, 380f, CRIMSON, 0f, off);
    }

    // ================================================================== II. Clear Blue Sky
    /** Two near-horizontal full circles that rise with the hop; the second turns the other way. */
    private static void clearBlueSky(AbstractClientPlayer p, float yaw) {
        int[] hits = FormTiming.CLEAR_SKY_HITS;
        arcAt(hits[0] - 1, p, yaw, 0f, 10f, 2.7f, 0.9f, 330f, 170f, 520f, GOLD, 0.8f, new Vec3(0, -0.1, 0));
        arcAt(hits[1] - 1, p, yaw + 180f, 0f, -14f, 2.9f, 0.95f, 330f, 170f, 560f, ORANGE, 0.8f, new Vec3(0, 0.35, 0));
        ClientScheduler.schedule(1, () -> {
            if (!p.isRemoved()) ringOfFlame(p.position().add(0, 0.1, 0), 0.6, 14, 0.08);
        });
    }

    // ================================================================== III. Raging Sun
    /** One enormous low sweep, an inner echo, and a fan of fire racing across the ground. */
    private static void ragingSun(AbstractClientPlayer p, float yaw, float pitch) {
        int t = FormTiming.RAGING_SUN_HIT - 1;
        arcAt(t, p, yaw, pitch * 0.5f, -6f, 4.6f, 1.6f, 250f, 170f, 650f, ORANGE, 1.6f, new Vec3(0, -0.25, 0));
        arcAt(t, p, yaw, pitch * 0.5f, -2f, 3.3f, 0.7f, 220f, 200f, 560f, CRIMSON, 0f, new Vec3(0, -0.2, 0));
        ClientScheduler.schedule(t + 1, () -> {
            if (p.isRemoved()) return;
            Vec3 base = p.position().add(0, 0.15, 0);
            int n = SunFx.count(40);
            for (int i = 0; i < n; i++) {
                double a = (yaw + Mth.lerp(i / (double) n, -125, 125)) * Mth.DEG_TO_RAD;
                Vec3 dir = new Vec3(-Math.sin(a), 0, Math.cos(a));
                double r = SunFx.rand(1.5, 5.5);
                SunFx.flame(base.add(dir.scale(r)), dir.scale(0.08).add(0, SunFx.rand(0.03, 0.09), 0));
            }
        });
    }

    // ================================================================== IV. Burning Bones, Summer Sun
    /** Five spinning cuts, each rotated a further 72 degrees and tilted the opposite way. */
    private static void burningBones(AbstractClientPlayer p, float yaw) {
        int[] hits = FormTiming.BURNING_BONES_HITS;
        for (int i = 0; i < hits.length; i++) {
            boolean last = i == hits.length - 1;
            float roll = (i % 2 == 0 ? 18f : -18f);
            float[] tint = last ? GOLD : (i % 2 == 0 ? ORANGE : CRIMSON);
            arcAt(hits[i] - 1, p, yaw + i * 72f, 0f, roll, last ? 3.4f : 2.8f, last ? 1.2f : 0.8f, 330f,
                    110f, 380f, tint, last ? 1.2f : 0.55f, Vec3.ZERO);
        }
        // a pillar of embers rising off the spin
        for (int k = 0; k < 4; k++) {
            ClientScheduler.schedule(3 + k * 4, () -> {
                if (p.isRemoved()) return;
                for (int i = 0; i < SunFx.count(8); i++) {
                    SunFx.ember(p.position().add(SunFx.randomUnit().multiply(0.9, 0, 0.9)).add(0, SunFx.rand(0.2, 1.8), 0),
                            new Vec3(0, SunFx.rand(0.05, 0.12), 0));
                }
            });
        }
    }

    // ================================================================== V. Setting Sun Transformation
    /** Afterimages fill the dash path, a burning line follows it, then a horizontal cut where you land. */
    private static void settingSun(AbstractClientPlayer p, float yaw) {
        final Vec3 start = p.position();
        AfterimageRenderer.spawn(p, 650f, 0.2f, 1.0f);
        ClientScheduler.schedule(FormTiming.SETTING_SUN_DASH, () -> {
            if (!p.isRemoved()) AfterimageRenderer.trail(p, 4, 520f, 0.3f);
        });
        arcAt(FormTiming.SETTING_SUN_HIT - 1, p, yaw, 0f, -8f, 3.0f, 1.1f, 200f, 110f, 480f, ORANGE, 1.0f, Vec3.ZERO);
        ClientScheduler.schedule(FormTiming.SETTING_SUN_HIT, () -> {
            if (p.isRemoved()) return;
            Vec3 end = p.position();
            Vec3 path = end.subtract(start);
            int n = SunFx.count(30);
            for (int i = 0; i < n; i++) {
                Vec3 at = start.add(path.scale(i / (double) n)).add(0, SunFx.rand(0.1, 1.4), 0);
                SunFx.flame(at, SunFx.randomUnit().scale(0.03).add(0, 0.04, 0));
                if (i % 3 == 0) SunFx.ray(at, path.normalize().scale(SunFx.rand(0.4, 0.8)));
            }
        });
    }

    // ================================================================== VI. Solar Heat Haze
    /** The real you blinks away; a shimmering heat decoy stands where you were, then dissolves. */
    private static void solarHeatHaze(AbstractClientPlayer p, S2CFormCast msg) {
        Vec3 origin = new Vec3(msg.x, msg.y, msg.z);
        AfterimageRenderer.spawn(origin, p.yBodyRot, 1600f, 0.15f, 1.6f, true); // the decoy
        AfterimageRenderer.trail(p, 3, 380f, 0.45f);
        SunFx.flash(origin.add(0, 1.0, 0), 1.6);
        for (int i = 0; i < SunFx.count(16); i++) {
            SunFx.glow(origin.add(SunFx.randomUnit().multiply(0.4, 0, 0.4)).add(0, SunFx.rand(0.1, 1.8), 0), SunFx.rand(0.4, 0.8));
        }
        // the decoy keeps radiating heat for a moment
        for (int k = 1; k <= 6; k++) {
            ClientScheduler.schedule(k * 4, () -> {
                for (int i = 0; i < SunFx.count(4); i++) {
                    SunFx.glow(origin.add(SunFx.randomUnit().multiply(0.35, 0, 0.35)).add(0, SunFx.rand(0.2, 1.8), 0), SunFx.rand(0.3, 0.6));
                    SunFx.ember(origin.add(0, SunFx.rand(0.2, 1.8), 0), new Vec3(0, 0.05, 0));
                }
            });
        }
    }

    // ================================================================== VII. Beneficent Radiance
    /** A vertical overhead cut that detonates into a sunburst of light in front of you. */
    private static void beneficentRadiance(AbstractClientPlayer p, float yaw, float pitch) {
        ClientScheduler.schedule(FormTiming.RADIANCE_LUNGE, () -> {
            if (!p.isRemoved()) AfterimageRenderer.trail(p, 3, 400f, 0.25f);
        });
        arcAt(FormTiming.RADIANCE_HIT - 1, p, yaw, pitch, 88f, 2.8f, 1.2f, 200f, 120f, 520f, GOLD, 1.0f,
                fwd(yaw).scale(0.3));
        ClientScheduler.schedule(FormTiming.RADIANCE_HIT, () -> {
            if (p.isRemoved()) return;
            Vec3 burst = chest(p).add(fwd(yaw).scale(2.4));
            SunFx.flash(burst, 4.5);
            SunFx.flash(burst, 2.2);
            int rays = SunFx.count(42);
            for (int i = 0; i < rays; i++) {
                Vec3 d = SunFx.randomUnit().add(fwd(yaw).scale(0.6)).normalize();
                SunFx.ray(burst, d.scale(SunFx.rand(0.6, 1.3)));
            }
            ringOfFlame(burst, 0.8, 24, 0.02);
            CameraFx.addTrauma(0.15f);
        });
    }

    // ================================================================== VIII. Sunflower Thrust
    /** A straight, needle-thin beam of fire with a blooming sunflower of petals at its tip. */
    private static void sunflowerThrust(AbstractClientPlayer p, float yaw, float pitch) {
        ClientScheduler.schedule(FormTiming.SUNFLOWER_HIT - 1, () -> {
            if (p.isRemoved()) return;
            Vec3 from = p.getEyePosition().add(0, -0.3, 0);
            Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
            float length = 8f;
            // A "straight" arc: a huge radius with a tiny angle is effectively a line.
            float bigR = 60f;
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
            side = side.normalize();
            Vec3 mid = from.add(dir.scale(length / 2));
            Vec3 centre = mid.add(side.scale(bigR));
            float arcDeg = (float) Math.toDegrees(length / bigR);
            SlashArcRenderer.add(new SlashArc(centre, side.scale(-1), dir.scale(-1), bigR, 0.35f, arcDeg,
                    80f, 420f, GOLD[0], GOLD[1], GOLD[2]));
            SlashArcRenderer.add(new SlashArc(centre, side.scale(-1), dir.scale(-1), bigR + 0.12f, 0.8f, arcDeg,
                    95f, 380f, CRIMSON[0], CRIMSON[1], CRIMSON[2]));

            Vec3 tip = from.add(dir.scale(length));
            for (int i = 0; i < SunFx.count(16); i++) {
                Vec3 at = from.add(dir.scale(SunFx.rand(0.5, length)));
                SunFx.ray(at, dir.scale(SunFx.rand(0.6, 1.2)));
            }
            // sunflower: petals radiating in the plane perpendicular to the thrust
            Vec3 up = side.cross(dir).normalize();
            int petals = SunFx.count(16);
            for (int i = 0; i < petals; i++) {
                double a = i / (double) petals * Math.PI * 2;
                Vec3 radial = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                SunFx.flame(tip.add(radial.scale(0.3)), radial.scale(0.14).add(dir.scale(0.05)));
            }
            SunFx.flash(tip, 1.6);
        });
    }

    // ================================================================== IX. Dragon Sun Halo Head Dance
    /** A rising spiral: four tilted halos around you in the air, linked by a twisting ribbon of fire. */
    private static void dragonSunHalo(AbstractClientPlayer p, float yaw) {
        int[] hits = FormTiming.DRAGON_HITS;
        for (int i = 0; i < hits.length; i++) {
            float tilt = 22f * (i % 2 == 0 ? 1 : -1);
            arcAt(hits[i] - 1, p, yaw + i * 90f, -10f, tilt, 3.1f, 1.0f, 340f, 150f, 520f,
                    i == hits.length - 1 ? GOLD : ORANGE, 0.7f, Vec3.ZERO);
        }
        // the dragon: a helix of flame spiralling up around the player
        for (int k = 0; k < 16; k++) {
            final int step = k;
            ClientScheduler.schedule(step + 1, () -> {
                if (p.isRemoved()) return;
                for (int j = 0; j < 2; j++) {
                    double a = step * 0.75 + j * Math.PI;
                    Vec3 at = p.position().add(Math.cos(a) * 1.6, 0.2 + step * 0.12, Math.sin(a) * 1.6);
                    SunFx.flame(at, new Vec3(-Math.sin(a), 0.3, Math.cos(a)).scale(0.12));
                    SunFx.ember(at, new Vec3(0, 0.04, 0));
                }
            });
        }
    }

    // ================================================================== X. Fire Wheel
    /** A vertical wheel of fire rolling over you and crashing down in front. */
    private static void fireWheel(AbstractClientPlayer p, float yaw) {
        Vec3 ahead = fwd(yaw).scale(0.9);
        arcAt(1, p, yaw, 0f, 90f, 2.2f, 0.9f, 300f, 260f, 650f, ORANGE, 0.8f, ahead);
        arcAt(4, p, yaw, 0f, 90f, 1.7f, 0.6f, 280f, 220f, 520f, CRIMSON, 0f, ahead);
        ClientScheduler.schedule(FormTiming.FIRE_WHEEL_HIT, () -> {
            if (p.isRemoved()) return;
            Vec3 impact = p.position().add(fwd(yaw).scale(2.2)).add(0, 0.1, 0);
            SunFx.flash(impact.add(0, 0.4, 0), 2.6);
            ringOfFlame(impact, 0.4, 26, 0.1);
            for (int i = 0; i < SunFx.count(18); i++) {
                Vec3 d = new Vec3(SunFx.rand(-1, 1), SunFx.rand(0.5, 1.2), SunFx.rand(-1, 1)).normalize();
                SunFx.ray(impact, d.scale(SunFx.rand(0.4, 0.9)));
            }
            CameraFx.addTrauma(0.12f);
        });
    }

    // ================================================================== XI. Fake Rainbow
    /** Six crossing cuts while weaving; each has a slightly different hue and leaves a ghost. */
    private static void fakeRainbow(AbstractClientPlayer p, float yaw, float pitch) {
        int[] hits = FormTiming.FAKE_RAINBOW_HITS;
        float[] rgb = new float[3];
        for (int i = 0; i < hits.length; i++) {
            SunPalette.ramp(0.12f + i * 0.13f, rgb); // gold -> orange -> crimson: the "rainbow"
            float[] tint = {rgb[0], rgb[1], rgb[2]};
            float roll = (i % 2 == 0 ? 42f : -42f);
            float yawOff = (i % 2 == 0 ? 14f : -14f);
            arcAt(hits[i] - 1, p, yaw + yawOff, pitch, roll, 2.4f, 0.7f, 150f, 85f, 330f, tint, 0.45f, Vec3.ZERO);
            ClientScheduler.schedule(hits[i] - 1, () -> {
                if (!p.isRemoved()) AfterimageRenderer.spawn(p, 360f, 0.2f, 0.8f);
            });
        }
    }

    // ================================================================== XII. Flame Dance (finale)
    /** The chain's other forms arrive as their own cast packets; this is the closing eruption. */
    private static void flameDanceFinale(AbstractClientPlayer p, float yaw) {
        int t = FormTiming.FLAME_FINALE_HIT - FormTiming.FLAME_FINALE - 1;
        AfterimageRenderer.spawn(p, 900f, 0.1f, 1.4f);
        // gathering: flames spiral inwards
        for (int k = 0; k < t; k++) {
            final int step = k;
            ClientScheduler.schedule(step, () -> {
                if (p.isRemoved()) return;
                double r = 4.0 - step * 0.6;
                ringOfFlame(p.position().add(0, 0.3 + step * 0.2, 0), Math.max(0.6, r), 10, 0.04);
            });
        }
        arcAt(t, p, yaw, 0f, 0f, 5.2f, 1.8f, 359f, 200f, 800f, ORANGE, 1.5f, Vec3.ZERO);
        arcAt(t, p, yaw + 60f, 0f, 32f, 4.0f, 1.3f, 359f, 220f, 760f, GOLD, 0.8f, Vec3.ZERO);
        arcAt(t, p, yaw - 60f, 0f, -32f, 4.0f, 1.3f, 359f, 220f, 760f, CRIMSON, 0.8f, Vec3.ZERO);
        ClientScheduler.schedule(t + 1, () -> {
            if (p.isRemoved()) return;
            Vec3 c = chest(p);
            SunFx.flash(c, 6.0);
            SunFx.flash(c, 3.0);
            ringOfFlame(p.position().add(0, 0.15, 0), 1.0, 48, 0.12);
            for (int i = 0; i < SunFx.count(60); i++) {
                SunFx.ray(c, SunFx.randomUnit().scale(SunFx.rand(0.8, 1.6)));
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.distanceToSqr(p) < 32 * 32) {
                CameraFx.addTrauma(0.5f);
                CameraFx.punchFov(1.0f);
            }
        });
    }

    // ================================================================== impacts
    public static void onImpact(S2CImpact msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 at = new Vec3(msg.x, msg.y, msg.z);
        float heaviness = Mth.clamp(msg.damage / 15f, 0.15f, 1f);
        Vec3 dir = Vec3.directionFromRotation(0f, msg.yaw);

        SunFx.impactBurst(at, dir, heaviness);

        boolean iAmAttacker = msg.attackerId == mc.player.getId();
        boolean iAmVictim = msg.targetId == mc.player.getId();
        if (!iAmAttacker && !iAmVictim) return;

        // Hit-stop: ~2 frames for light hits up to ~4 frames (at 60 fps) for heavy ones.
        if (SunConfig.CLIENT.hitStop.get()) {
            VfxClock.freeze(33.0 + 34.0 * heaviness);
        }
        CameraFx.addTrauma(iAmVictim ? 0.35f + 0.5f * heaviness : 0.18f + 0.42f * heaviness);
        if (heaviness >= 0.55f) CameraFx.punchFov(heaviness);
    }
}
