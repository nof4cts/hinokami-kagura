package com.sunbreathing.forms;

import com.sunbreathing.breath.BreathData;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.network.ModNetwork;
import com.sunbreathing.network.S2CFormCast;
import com.sunbreathing.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Server timelines for all twelve forms: movement, hit windows and sounds.
 *
 * Every timeline starts by broadcasting {@link S2CFormCast} (the client plays the matching
 * animation + VFX) and then schedules its movement and hit checks with {@link ServerScheduler}
 * using the tick numbers in {@link FormTiming}, so visuals and hits stay in sync.
 * Hits are always evaluated from the player's CURRENT position, so dashes carry the blade with them.
 */
public final class ServerForms {
    private ServerForms() {}

    public static void cast(Form form, ServerPlayer p, BreathData data, SunConfig.FormStats stats) {
        float dmg = stats.damage.get().floatValue();
        switch (form) {
            case DANCE -> dance(p, dmg);
            case CLEAR_BLUE_SKY -> clearBlueSky(p, dmg);
            case RAGING_SUN -> ragingSun(p, dmg);
            case BURNING_BONES -> burningBones(p, dmg);
            case SETTING_SUN -> settingSun(p, dmg);
            case SOLAR_HEAT_HAZE -> solarHeatHaze(p);
            case BENEFICENT_RADIANCE -> beneficentRadiance(p, dmg);
            case SUNFLOWER_THRUST -> sunflowerThrust(p, dmg);
            case DRAGON_SUN_HALO -> dragonSunHalo(p, dmg);
            case FIRE_WHEEL -> fireWheel(p, dmg);
            case FAKE_RAINBOW -> fakeRainbow(p, dmg);
            case FLAME_DANCE -> flameDance(p, dmg);
        }
    }

    // ================================================================== helpers
    private static void begin(ServerPlayer p, Form form, SoundEvent swing, float pitch) {
        ModNetwork.sendToTrackingAndSelf(p, new S2CFormCast(p.getId(), form.ordinal(),
                p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), swing, SoundSource.PLAYERS,
                1.0f, pitch * (0.95f + level.random.nextFloat() * 0.1f));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FLAME_IGNITE.get(), SoundSource.PLAYERS,
                0.7f, 1.0f + level.random.nextFloat() * 0.2f);
    }

    private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Runs {@code action} after {@code tick} ticks if the player is still alive and online. */
    private static void at(int tick, ServerPlayer p, Runnable action) {
        ServerScheduler.schedule(tick, () -> {
            if (p.isAlive() && !p.hasDisconnected()) action.run();
        });
    }

    /** Adds velocity relative to a yaw: forward/side in blocks per tick, plus vertical. */
    private static void push(ServerPlayer p, float yaw, double forward, double side, double up, boolean replace) {
        Vec3 fwd = Vec3.directionFromRotation(0f, yaw);
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 v = fwd.scale(forward).add(right.scale(side)).add(0, up, 0);
        p.setDeltaMovement(replace ? v : p.getDeltaMovement().add(v));
        p.hurtMarked = true; // send the velocity change to the client
    }

    private static Vec3 chest(ServerPlayer p) {
        return p.position().add(0, p.getBbHeight() * 0.55, 0);
    }

    private static void finishHits(ServerPlayer p, List<LivingEntity> targets, float damage, Form form,
                                   float yaw, double knockback) {
        LivingEntity first = null;
        for (LivingEntity t : targets) {
            if (Combat.strike(p, t, damage, form, yaw, knockback) && first == null) first = t;
        }
        if (first != null) Combat.playImpactLayers(p.serverLevel(), first.position(), Math.min(1f, damage / 15f));
    }

    private static void hitArc(ServerPlayer p, Form form, float yaw, float damage, double range, double arcDeg,
                               double vertical, double knockback) {
        finishHits(p, Combat.arc(p, chest(p), yaw, range, arcDeg, vertical), damage, form, yaw, knockback);
    }

    private static float statDamage(Form f) {
        return SunConfig.SERVER.form(f).damage.get().floatValue();
    }

    // ================================================================== I. Dance
    /** A single fast diagonal slash with a short forward step. */
    static void dance(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.DANCE, ModSounds.DANCE_SWING.get(), 1.0f);
        at(FormTiming.DANCE_LUNGE_TICK, p, () -> push(p, yaw, p.onGround() ? 0.9 : 0.45, 0, 0.04, false));
        at(FormTiming.DANCE_HIT_TICK, p, () -> hitArc(p, Form.DANCE, yaw, dmg, 4.6, 160, 2.4, 0.45));
    }

    // ================================================================== II. Clear Blue Sky
    /** A small hop and two rising, full-circle slashes. */
    static void clearBlueSky(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.CLEAR_BLUE_SKY, ModSounds.SPIN.get(), 1.15f);
        at(1, p, () -> push(p, yaw, 0.15, 0, 0.42, false));
        for (int hit : FormTiming.CLEAR_SKY_HITS) {
            at(hit, p, () -> {
                hitArc(p, Form.CLEAR_BLUE_SKY, yaw, dmg, 3.9, 360, 3.2, 0.3);
                sound(p, ModSounds.DANCE_SWING.get(), 0.8f, 1.25f);
            });
        }
    }

    // ================================================================== III. Raging Sun
    /** One enormous, wide sweep in front: the crowd-clearing form. */
    static void ragingSun(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.RAGING_SUN, ModSounds.SWING_HEAVY.get(), 0.9f);
        at(FormTiming.RAGING_SUN_HIT - 1, p, () -> push(p, yaw, 0.25, 0, 0.0, false));
        at(FormTiming.RAGING_SUN_HIT, p, () -> hitArc(p, Form.RAGING_SUN, yaw, dmg, 6.5, 250, 2.8, 0.8));
    }

    // ================================================================== IV. Burning Bones, Summer Sun
    /** Spinning multi-hit combo: five cuts all around while drifting forward. */
    static void burningBones(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.BURNING_BONES, ModSounds.SPIN.get(), 0.95f);
        int[] hits = FormTiming.BURNING_BONES_HITS;
        for (int i = 0; i < hits.length; i++) {
            final boolean last = i == hits.length - 1;
            at(hits[i], p, () -> {
                push(p, yaw, 0.18, 0, 0.0, false);
                hitArc(p, Form.BURNING_BONES, yaw, last ? dmg * 1.8f : dmg, 3.8, 360, 2.6, last ? 0.9 : 0.05);
                sound(p, ModSounds.DANCE_SWING.get(), 0.7f, 1.1f + (last ? -0.2f : 0f));
            });
        }
    }

    // ================================================================== V. Setting Sun Transformation
    /** A blinding dash; everything along the path is cut, with a final slash where you stop. */
    static void settingSun(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        final Vec3 start = chest(p);
        begin(p, Form.SETTING_SUN, ModSounds.DASH.get(), 1.0f);
        at(FormTiming.SETTING_SUN_DASH, p, () -> push(p, yaw, p.onGround() ? 2.1 : 1.3, 0, 0.08, true));
        at(FormTiming.SETTING_SUN_HIT, p, () -> {
            Vec3 end = chest(p);
            List<LivingEntity> path = Combat.line(p, start, end, 1.4);
            finishHits(p, path, dmg, Form.SETTING_SUN, yaw, 0.4);
            // Final cut at the arrival point catches anything the path missed.
            List<LivingEntity> arrival = Combat.arc(p, end, yaw, 3.6, 200, 2.4).stream()
                    .filter(e -> !path.contains(e)).toList();
            finishHits(p, arrival, dmg * 0.8f, Form.SETTING_SUN, yaw, 0.6);
            sound(p, ModSounds.DANCE_SWING.get(), 0.9f, 0.9f);
        });
    }

    // ================================================================== VI. Solar Heat Haze
    /**
     * Evasive dodge: a burst backwards, brief immunity, and a heat-shimmer decoy left behind that
     * makes nearby mobs lose track of you.
     */
    static void solarHeatHaze(ServerPlayer p) {
        final float yaw = p.getYRot();
        begin(p, Form.SOLAR_HEAT_HAZE, ModSounds.DASH.get(), 1.3f);
        push(p, yaw, -1.5, 0, 0.25, true);
        // Resistance V = full damage immunity for the dodge window.
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, FormTiming.HEAT_HAZE_INVULN_TICKS, 4,
                false, false, false));
        ServerLevel level = p.serverLevel();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(24))) {
            if (mob.getTarget() == p) mob.setTarget(null);
        }
    }

    // ================================================================== VII. Beneficent Radiance
    /** Overhead lunge ending in a burst of light that blasts enemies back. */
    static void beneficentRadiance(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.BENEFICENT_RADIANCE, ModSounds.SWING_HEAVY.get(), 1.05f);
        at(FormTiming.RADIANCE_LUNGE, p, () -> push(p, yaw, p.onGround() ? 1.2 : 0.7, 0, 0.1, false));
        at(FormTiming.RADIANCE_HIT, p, () -> {
            sound(p, ModSounds.RADIANCE.get(), 1.2f, 1.0f);
            hitArc(p, Form.BENEFICENT_RADIANCE, yaw, dmg, 5.5, 110, 3.0, 1.4);
        });
    }

    // ================================================================== VIII. Sunflower Thrust
    /** A precise, long-reaching piercing thrust that hits everything in a line. */
    static void sunflowerThrust(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        final float pitch = p.getXRot();
        begin(p, Form.SUNFLOWER_THRUST, ModSounds.THRUST.get(), 1.0f);
        at(2, p, () -> push(p, yaw, 0.6, 0, 0.0, false));
        at(FormTiming.SUNFLOWER_HIT, p, () -> {
            Vec3 from = p.getEyePosition().add(0, -0.3, 0);
            Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
            List<LivingEntity> targets = Combat.line(p, from, from.add(dir.scale(8.0)), 0.9);
            finishHits(p, targets, dmg, Form.SUNFLOWER_THRUST, yaw, 0.7);
        });
    }

    // ================================================================== IX. Dragon Sun Halo Head Dance
    /** Leap into the air and spiral down through four cuts; slow-falling keeps you aloft. */
    static void dragonSunHalo(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.DRAGON_SUN_HALO, ModSounds.SPIN.get(), 0.85f);
        push(p, yaw, 0.35, 0, 0.95, true);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, false));
        for (int hit : FormTiming.DRAGON_HITS) {
            at(hit, p, () -> {
                hitArc(p, Form.DRAGON_SUN_HALO, yaw, dmg, 4.4, 360, 4.5, 0.35);
                sound(p, ModSounds.DANCE_SWING.get(), 0.8f, 0.95f);
            });
        }
        at(FormTiming.DRAGON_HITS[FormTiming.DRAGON_HITS.length - 1] + 2, p, () -> p.fallDistance = 0);
    }

    // ================================================================== X. Fire Wheel
    /** Forward somersault: the blade rolls over the top and slams down in front. */
    static void fireWheel(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.FIRE_WHEEL, ModSounds.SPIN.get(), 0.8f);
        push(p, yaw, 0.75, 0, 0.62, true);
        at(FormTiming.FIRE_WHEEL_HIT, p, () -> {
            sound(p, ModSounds.SWING_HEAVY.get(), 1.0f, 0.75f);
            hitArc(p, Form.FIRE_WHEEL, yaw, dmg, 4.8, 120, 4.0, 0.6);
            p.fallDistance = 0;
        });
    }

    // ================================================================== XI. Fake Rainbow
    /** Six rapid cuts while weaving left and right, too fast to follow. */
    static void fakeRainbow(ServerPlayer p, float dmg) {
        final float yaw = p.getYRot();
        begin(p, Form.FAKE_RAINBOW, ModSounds.DASH.get(), 1.2f);
        int[] hits = FormTiming.FAKE_RAINBOW_HITS;
        for (int i = 0; i < hits.length; i++) {
            final double side = (i % 2 == 0 ? 1 : -1) * 0.55;
            at(hits[i] - 1, p, () -> push(p, yaw, 0.2, side, 0.0, false));
            at(hits[i], p, () -> {
                hitArc(p, Form.FAKE_RAINBOW, yaw, dmg, 4.0, 150, 2.4, 0.08);
                sound(p, ModSounds.DANCE_SWING.get(), 0.6f, 1.3f);
            });
        }
    }

    // ================================================================== XII. Flame Dance
    /**
     * The finisher: chains Dance -> Raging Sun -> Fire Wheel -> Beneficent Radiance (each with its own
     * animation and VFX, using each form's configured damage), then a finale that erupts in a full circle.
     */
    static void flameDance(ServerPlayer p, float finaleDamage) {
        dance(p, statDamage(Form.DANCE));
        at(FormTiming.FLAME_CHAIN_RAGING, p, () -> ragingSun(p, statDamage(Form.RAGING_SUN)));
        at(FormTiming.FLAME_CHAIN_WHEEL, p, () -> fireWheel(p, statDamage(Form.FIRE_WHEEL)));
        at(FormTiming.FLAME_CHAIN_RADIANCE, p, () -> beneficentRadiance(p, statDamage(Form.BENEFICENT_RADIANCE)));
        at(FormTiming.FLAME_FINALE, p, () -> {
            final float yaw = p.getYRot();
            begin(p, Form.FLAME_DANCE, ModSounds.FINALE.get(), 1.0f);
            at(FormTiming.FLAME_FINALE_HIT - FormTiming.FLAME_FINALE, p, () -> {
                sound(p, ModSounds.RADIANCE.get(), 1.5f, 0.7f);
                finishHits(p, Combat.sphere(p, chest(p), 7.5), finaleDamage, Form.FLAME_DANCE, yaw, 1.6);
            });
        });
    }
}
