package com.sunbreathing.forms;

import com.sunbreathing.config.SunConfig;
import com.sunbreathing.network.ModNetwork;
import com.sunbreathing.network.S2CImpact;
import com.sunbreathing.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Server-side hit detection and damage. Shapes are analytic (arc / cone / capsule) rather than
 * raycasts so they are cheap, deterministic and easy to match with the client slash mesh.
 */
public final class Combat {
    private Combat() {}

    /**
     * Targets inside a horizontal arc in front of {@code origin}.
     *
     * @param yawDeg   facing used for the arc (the yaw captured at cast time, same one sent to clients)
     * @param range    horizontal reach in blocks, measured to the target's bounding box edge
     * @param arcDeg   total arc width in degrees (e.g. 160 = 80 either side)
     * @param vertical how far above/below origin a target's centre may be
     */
    public static List<LivingEntity> arc(ServerPlayer attacker, Vec3 origin, float yawDeg,
                                         double range, double arcDeg, double vertical) {
        Vec3 forward = Vec3.directionFromRotation(0f, yawDeg);
        double cosHalf = Math.cos(Math.toRadians(arcDeg * 0.5));
        AABB box = new AABB(origin, origin).inflate(range + 2.0, vertical + 2.0, range + 2.0);

        return attacker.level().getEntitiesOfClass(LivingEntity.class, box, e -> canHit(attacker, e)).stream()
                .filter(e -> {
                    Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
                    if (Math.abs(to.y) > vertical + e.getBbHeight() * 0.5) return false;
                    Vec3 flat = new Vec3(to.x, 0, to.z);
                    double dist = flat.length() - e.getBbWidth() * 0.5;
                    if (dist > range) return false;
                    if (flat.lengthSqr() < 0.5) return true; // practically inside us
                    return flat.normalize().dot(forward) >= cosHalf;
                })
                .filter(attacker::hasLineOfSight)
                .toList();
    }

    /** Targets within {@code radius} of the segment from..to (a capsule): thrusts and dashes. */
    public static List<LivingEntity> line(ServerPlayer attacker, Vec3 from, Vec3 to, double radius) {
        AABB box = new AABB(from, to).inflate(radius + 1.0);
        Vec3 seg = to.subtract(from);
        double lenSq = Math.max(seg.lengthSqr(), 1.0e-6);
        return attacker.level().getEntitiesOfClass(LivingEntity.class, box, e -> canHit(attacker, e)).stream()
                .filter(e -> {
                    Vec3 c = e.getBoundingBox().getCenter();
                    double t = Mth.clamp(c.subtract(from).dot(seg) / lenSq, 0.0, 1.0);
                    Vec3 closest = from.add(seg.scale(t));
                    double reach = radius + e.getBbWidth() * 0.5 + (e.getBbHeight() > 2.5 ? 0.6 : 0.0);
                    return c.distanceToSqr(closest) <= reach * reach;
                })
                .toList();
    }

    /** Everything within a sphere: finales and spins. */
    public static List<LivingEntity> sphere(ServerPlayer attacker, Vec3 centre, double radius) {
        AABB box = new AABB(centre, centre).inflate(radius + 1.0);
        return attacker.level().getEntitiesOfClass(LivingEntity.class, box, e -> canHit(attacker, e)).stream()
                .filter(e -> e.getBoundingBox().getCenter().distanceToSqr(centre)
                        <= (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5))
                .toList();
    }

    public static boolean canHit(ServerPlayer attacker, LivingEntity e) {
        if (e == attacker || !e.isAlive() || e.isSpectator() || !e.isPickable()) return false;
        if (attacker.isAlliedTo(e)) return false;
        if (e instanceof ArmorStand stand && stand.isMarker()) return false;
        if (e instanceof TamableAnimal pet && attacker.getUUID().equals(pet.getOwnerUUID())) return false;
        if (e instanceof Player && !SunConfig.SERVER.hitPlayers.get()) return false;
        return true;
    }

    /**
     * Applies one Sun Breathing hit and broadcasts the impact so clients can play hit-stop,
     * shake and impact VFX. Returns true if damage actually landed.
     */
    public static boolean strike(ServerPlayer attacker, LivingEntity target, float damage, Form form,
                                 float yawDeg, double knockback) {
        // Forms are fast multi-hit combos; vanilla i-frames (10 ticks) would swallow follow-up hits.
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(attacker.damageSources().playerAttack(attacker), damage);
        if (!hurt) return false;

        int fire = SunConfig.SERVER.fireSeconds.get();
        if (fire > 0) target.setSecondsOnFire(fire);
        if (knockback > 0) {
            float r = yawDeg * Mth.DEG_TO_RAD;
            target.knockback(knockback, Mth.sin(r), -Mth.cos(r));
        }

        ServerLevel level = attacker.serverLevel();
        Vec3 c = target.getBoundingBox().getCenter();
        ModNetwork.sendNear(level, c, 64.0,
                new S2CImpact(c.x, c.y, c.z, damage, attacker.getId(), target.getId(), form.ordinal(), yawDeg));
        return true;
    }

    /** Layered impact audio: bright slash + low sub-hit + flame crackle. */
    public static void playImpactLayers(ServerLevel level, Vec3 at, float heaviness) {
        float pitchJitter = 0.92f + level.random.nextFloat() * 0.16f;
        level.playSound(null, at.x, at.y, at.z, ModSounds.IMPACT_SLASH.get(), SoundSource.PLAYERS,
                1.0f, pitchJitter);
        level.playSound(null, at.x, at.y, at.z, ModSounds.IMPACT_SUB.get(), SoundSource.PLAYERS,
                0.6f + 0.5f * heaviness, 1.0f - 0.15f * heaviness);
        level.playSound(null, at.x, at.y, at.z, ModSounds.FLAME_CRACKLE.get(), SoundSource.PLAYERS,
                0.7f, pitchJitter);
    }
}
