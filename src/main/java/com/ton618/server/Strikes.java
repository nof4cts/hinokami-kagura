package com.ton618.server;

import com.ton618.Ton618Mod;
import com.ton618.common.BlackHole;
import com.ton618.common.StrikeTiming;
import com.ton618.config.Ton618Config;
import com.ton618.network.Net;
import com.ton618.network.S2CCutscene;
import com.ton618.registry.ModRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A strike from start to finish, server side:
 *   t=0          cutscene starts for everyone near the shooter or the target
 *   t=110..300   TON 618 falls from high orbit onto the target (see {@link StrikeTiming})
 *   t=300        impact: positional boom for everyone, consumption begins
 *   then         the sphere of world is streamed away; entities are dragged in and destroyed
 *   finally      the hole lingers, then collapses and is removed
 */
@Mod.EventBusSubscriber(modid = Ton618Mod.MODID)
public final class Strikes {
    private static final List<Strike> ACTIVE = new ArrayList<>();

    private Strikes() {}

    private static final class Strike {
        final ServerLevel level;
        final Vec3 target;
        final long start;
        final BlackHole hole;
        Consumption consumption;
        long collapseAt = -1;

        Strike(ServerLevel level, Vec3 target, long start, BlackHole hole) {
            this.level = level;
            this.target = target;
            this.start = start;
            this.hole = hole;
        }
    }

    public static double lockRange(ServerPlayer p) {
        int view = p.server.getPlayerList().getViewDistance() * 16 - 24;
        return Math.max(16, Math.min(Ton618Config.SERVER.lockRange.get(), view));
    }

    public static void start(ServerPlayer shooter, Vec3 target) {
        ServerLevel level = shooter.serverLevel();
        long now = level.getGameTime();
        float rs = Ton618Config.SERVER.holeSize.get().floatValue();

        // It comes in steeply from high orbit, from roughly behind the shooter's line of sight.
        Vec3 flat = target.subtract(shooter.position()).multiply(1, 0, 1);
        if (flat.lengthSqr() < 1.0) flat = new Vec3(shooter.getLookAngle().x, 0, shooter.getLookAngle().z);
        if (flat.lengthSqr() < 1.0e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 from = target.add(0, 900, 0).subtract(flat.scale(260));

        BlackHole hole = BlackHoleManager.spawn(level, from, target, rs, StrikeTiming.HOLE_APPEARS, StrikeTiming.DESCENT);
        ACTIVE.add(new Strike(level, target, now, hole));

        double r = Ton618Config.SERVER.cutsceneRadius.get();
        S2CCutscene msg = new S2CCutscene(now, target, from, hole.id(), shooter.getId());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(target) < r * r || p.distanceToSqr(shooter) < r * r || p == shooter) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
            }
        }
        Ton618Mod.LOGGER.info("TON 618 strike by {} at {}", shooter.getGameProfile().getName(), target);
    }

    public static void cancelAll(ServerLevel level) {
        ACTIVE.removeIf(s -> s.level == level);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (ACTIVE.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Strike> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            if (s.level != level) continue;
            long t = now - s.start;
            if (t < StrikeTiming.IMPACT) continue;

            if (s.consumption == null) {
                impact(s, now);
            }
            Consumption c = s.consumption;
            if (Ton618Config.SERVER.destroyBlocks.get()) c.tick(now, Ton618Config.SERVER.blocksPerTick.get());
            double radius = c.currentRadius(now);
            if (Ton618Config.SERVER.pullEntities.get() && s.collapseAt < 0) pull(s, radius);

            boolean grown = now - (s.start + StrikeTiming.IMPACT) >= Ton618Config.SERVER.consumeSeconds.get() * 20L;
            if (s.collapseAt < 0 && grown && (c.done() || !Ton618Config.SERVER.destroyBlocks.get())) {
                s.collapseAt = now + Ton618Config.SERVER.lingerSeconds.get() * 20L + 60;
                BlackHoleManager.collapse(level, s.hole.id(), s.collapseAt);
            }
            if (s.collapseAt >= 0 && now == s.collapseAt - 50) {
                level.playSound(null, s.target.x, s.target.y, s.target.z, ModRegistry.HOLE_COLLAPSE.get(),
                        SoundSource.AMBIENT, 30f, 1f);
            }
            if (s.collapseAt >= 0 && now >= s.collapseAt) it.remove();
        }
    }

    private static void impact(Strike s, long now) {
        int radius = Ton618Config.SERVER.destructionRadius.get();
        int grow = Ton618Config.SERVER.consumeSeconds.get() * 20;
        s.consumption = new Consumption(s.level, s.target, radius, now, grow);
        // volume >> 1 widens the audible range (16 blocks per unit of volume)
        s.level.playSound(null, s.target.x, s.target.y, s.target.z, ModRegistry.HOLE_IMPACT.get(),
                SoundSource.AMBIENT, 48f, 1f);
    }

    /** Everything near the hole is dragged in; what reaches the horizon is destroyed. */
    private static void pull(Strike s, double radius) {
        double reach = radius + 32.0;
        double horizon = s.hole.rs() * 1.15 + 1.0;
        AABB box = new AABB(s.target, s.target).inflate(reach);
        for (Entity e : s.level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 to = s.target.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > reach) continue;
            if (d < horizon) {
                if (e instanceof LivingEntity living) {
                    living.hurt(s.level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                } else {
                    e.discard();
                }
                continue;
            }
            double strength = 0.03 + 0.22 * Math.pow(1.0 - d / reach, 2.0);
            e.setDeltaMovement(e.getDeltaMovement().add(to.scale(strength / d)));
            e.fallDistance = 0;
            e.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
