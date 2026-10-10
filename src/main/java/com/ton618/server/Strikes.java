package com.ton618.server;

import com.ton618.Ton618Mod;
import com.ton618.common.BlackHole;
import com.ton618.common.StrikeKind;
import com.ton618.common.StrikeTiming;
import com.ton618.config.Ton618Config;
import com.ton618.network.Net;
import com.ton618.network.S2CCutscene;
import com.ton618.network.S2CDebris;
import com.ton618.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * A strike from start to finish, server side. Timelines are in {@link StrikeTiming}.
 *
 * TON 618   falls from high above; on landing it starts swallowing a growing sphere of the world. Blocks are
 *           torn loose and spiral into it (sent to clients as debris), and everything nearby is dragged in
 *           slowly, faster as it gets closer, until it crosses the horizon.
 * THE SUN   comes down on the target and burns out a crater with a molten floor; beyond it the ground is
 *           scorched to blackstone and magma, everything near is set ablaze and blown outward.
 * ARCHANGEL descends, raises its sword and brings it down: a rift opens through the world along the cut,
 *           from the surface into the void, racing out in both directions.
 */
@Mod.EventBusSubscriber(modid = Ton618Mod.MODID)
public final class Strikes {
    private static final List<Strike> ACTIVE = new ArrayList<>();

    private Strikes() {}

    private static final class Strike {
        final ServerLevel level;
        final StrikeKind kind;
        final Vec3 target;
        final long start;
        final BlackHole body;
        Consumption consumption;
        Rift rift;
        boolean struck;
        long collapseAt = -1;
        final List<BlockPos> debrisPos = new ArrayList<>();
        final List<BlockState> debrisState = new ArrayList<>();
        int debrisSeen;

        Strike(ServerLevel level, StrikeKind kind, Vec3 target, long start, BlackHole body) {
            this.level = level;
            this.kind = kind;
            this.target = target;
            this.start = start;
            this.body = body;
        }
    }

    public static double lockRange(ServerPlayer p) {
        int view = p.server.getPlayerList().getViewDistance() * 16 - 24;
        return Math.max(16, Math.min(Ton618Config.SERVER.lockRange.get(), view));
    }

    private static double groundY(ServerLevel level, Vec3 at) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        return h <= level.getMinBuildHeight() ? at.y : h;
    }

    public static void start(ServerPlayer shooter, Vec3 target, StrikeKind kind) {
        ServerLevel level = shooter.serverLevel();
        long now = level.getGameTime();

        Vec3 flat = target.subtract(shooter.position()).multiply(1, 0, 1);
        if (flat.lengthSqr() < 1.0) flat = new Vec3(shooter.getLookAngle().x, 0, shooter.getLookAngle().z);
        if (flat.lengthSqr() < 1.0e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));

        int appear = StrikeTiming.appear(kind);
        int land = StrikeTiming.land(kind);
        long impactTick = now + StrikeTiming.impact(kind);
        Vec3 from, to;
        float size;
        switch (kind) {
            case SUN -> {
                size = Ton618Config.SERVER.sunRadius.get().floatValue();
                to = new Vec3(target.x, groundY(level, target) + size * 0.25, target.z);
                from = to.add(0, 1800, 0).subtract(flat.scale(700));
            }
            case ANGEL -> {
                size = Ton618Config.SERVER.angelHeight.get().floatValue();
                // it stands back from the target so the blade comes down on it
                Vec3 feet = target.subtract(flat.scale(size * 0.62));
                to = new Vec3(feet.x, groundY(level, feet) + size * 0.04, feet.z);
                from = to.add(0, size * 3.2, 0);
            }
            default -> {
                size = Ton618Config.SERVER.holeSize.get().floatValue();
                to = target;
                from = target.add(0, 900, 0).subtract(flat.scale(260));
            }
        }
        BlackHole body = BlackHoleManager.spawn(level, from, to, size, appear, land - appear, kind, yaw, impactTick);
        ACTIVE.add(new Strike(level, kind, target, now, body));

        double r = Ton618Config.SERVER.cutsceneRadius.get();
        S2CCutscene msg = new S2CCutscene(now, target, from, body.id(), shooter.getId(), kind.ordinal());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(target) < r * r || p.distanceToSqr(shooter) < r * r || p == shooter) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
            }
        }
        Ton618Mod.LOGGER.info("{} strike by {} at {}", kind, shooter.getGameProfile().getName(), target);
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
            if (t < StrikeTiming.impact(s.kind)) continue;
            if (!s.struck) {
                s.struck = true;
                impact(s, now);
            }
            boolean finished = switch (s.kind) {
                case HOLE -> tickHole(s, now);
                case SUN -> tickSun(s, now);
                case ANGEL -> tickAngel(s, now);
            };
            if (finished && s.collapseAt < 0) {
                s.collapseAt = now + Ton618Config.SERVER.lingerSeconds.get() * 20L + 60;
                BlackHoleManager.collapse(level, s.body.id(), s.collapseAt);
            }
            if (s.collapseAt >= 0 && now == s.collapseAt - 50 && s.kind == StrikeKind.HOLE) {
                sound(s, ModRegistry.HOLE_COLLAPSE.get(), 30f);
            }
            if (s.collapseAt >= 0 && now >= s.collapseAt) it.remove();
        }
    }

    private static void sound(Strike s, SoundEvent e, float volume) {
        // volume >> 1 widens the audible range (16 blocks per unit of volume)
        s.level.playSound(null, s.target.x, s.target.y, s.target.z, e, SoundSource.AMBIENT, volume, 1f);
    }

    private static void impact(Strike s, long now) {
        boolean destroy = Ton618Config.SERVER.destroyBlocks.get();
        switch (s.kind) {
            case HOLE -> {
                int radius = destroy ? Ton618Config.SERVER.destructionRadius.get() : 0;
                int grow = Ton618Config.SERVER.consumeSeconds.get() * 20;
                s.consumption = new Consumption(s.level, s.target, radius, now, grow).with(Consumption.ERASE, (pos, old) -> {
                    // a sample of what's torn loose is flung visibly into the hole by the clients
                    s.debrisSeen++;
                    int cap = Ton618Config.SERVER.debrisPerTick.get();
                    if (s.debrisPos.size() < cap && (s.debrisSeen % 5 == 0 || s.debrisPos.size() < cap / 4)) {
                        s.debrisPos.add(pos);
                        s.debrisState.add(old);
                    }
                });
                sound(s, ModRegistry.HOLE_IMPACT.get(), 48f);
            }
            case SUN -> {
                int r = destroy ? Ton618Config.SERVER.scorchRadius.get() : 0;
                s.consumption = new Consumption(s.level, s.target, r + 14, now, 20 * 6).with(scorcher(s, r), null);
                sound(s, ModRegistry.SUN_IMPACT.get(), 48f);
                blast(s, r * 1.8 + 40, true);
            }
            case ANGEL -> {
                Vec3 dir = s.body.facing();
                if (destroy) {
                    s.rift = new Rift(s.level, s.target, dir, Ton618Config.SERVER.riftLength.get(),
                            Ton618Config.SERVER.riftWidth.get());
                }
                sound(s, ModRegistry.ANGEL_SLASH.get(), 48f);
                riftBlast(s, dir);
            }
        }
    }

    // ------------------------------------------------------------------ TON 618
    private static boolean tickHole(Strike s, long now) {
        Consumption c = s.consumption;
        c.tick(now, Ton618Config.SERVER.blocksPerTick.get());
        if (!s.debrisPos.isEmpty()) {
            S2CDebris msg = new S2CDebris(s.body.id(), s.target, s.debrisPos, s.debrisState);
            Net.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                    s.target.x, s.target.y, s.target.z, 900, s.level.dimension())), msg);
            s.debrisPos.clear();
            s.debrisState.clear();
        }
        double radius = c.currentRadius(now);
        if (Ton618Config.SERVER.pullEntities.get() && s.collapseAt < 0) pull(s, radius);
        boolean grown = now - (s.start + StrikeTiming.H_IMPACT) >= Ton618Config.SERVER.consumeSeconds.get() * 20L;
        return grown && c.done();
    }

    /**
     * Everything near the hole is dragged in. Slowly at first, a drift you can feel and fight against, but it
     * keeps building, and the closer you get the stronger it pulls. Within the horizon, nothing survives.
     */
    private static void pull(Strike s, double radius) {
        double reach = radius + 48.0;
        double horizon = s.body.rs() * 1.1 + 1.0;
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
            double near = 1.0 - d / reach;
            // gentle: ~0.6 blocks/s of extra drift at the edge, rising steeply close in
            double strength = 0.006 + 0.05 * near * near * near;
            Vec3 pullV = to.scale(strength / d);
            // a little swirl, so things spiral in rather than fall straight
            Vec3 swirl = new Vec3(-to.z, 0, to.x).normalize().scale(strength * 0.35);
            Vec3 v = e.getDeltaMovement().add(pullV).add(swirl);
            double max = 0.25 + 1.2 * near * near;
            if (v.length() > max) v = v.normalize().scale(max);
            e.setDeltaMovement(v);
            e.fallDistance = 0;
            e.hurtMarked = true;
            // the tide pulls you apart a little as you get close
            if (near > 0.75 && e instanceof LivingEntity living && s.level.getGameTime() % 10 == 0) {
                living.hurt(s.level.damageSources().magic(), (float) (1.0 + 6.0 * (near - 0.75) * 4.0));
            }
        }
    }

    // ------------------------------------------------------------------ the Sun
    private static Consumption.Replacer scorcher(Strike s, int r) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState black = Blocks.BLACKSTONE.defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState fire = Blocks.FIRE.defaultBlockState();
        return (pos, old, d, dx, dy, dz) -> {
            if (d <= r) {
                // the crater: emptied, with a floor of molten rock a few blocks deep
                double floor = -Math.sqrt(Math.max(0.0, (double) r * r - dx * dx - dz * dz));
                if (dy < floor + 4 && dy < -r * 0.25) return lava;
                return old.isAir() ? null : air;
            }
            if (old.isAir()) {
                // fire on surfaces just outside the rim
                if (d < r + 10 && s.level.random.nextFloat() < 0.12f) {
                    BlockState below = s.level.getBlockState(pos.below());
                    if (!below.isAir() && below.isFaceSturdy(s.level, pos.below(), net.minecraft.core.Direction.UP)) return fire;
                }
                return null;
            }
            if (!old.getFluidState().isEmpty()) return old.getFluidState().is(net.minecraft.tags.FluidTags.WATER) ? air : null;
            if (old.is(BlockTags.LEAVES) || old.is(BlockTags.LOGS) || old.is(BlockTags.REPLACEABLE) || old.is(BlockTags.FLOWERS))
                return air;
            float f = s.level.random.nextFloat();
            double k = (d - r) / 14.0;     // 0 at the rim .. 1 at the edge of the scorch
            if (f < 0.55 * (1 - k)) return magma;
            if (f < 0.8) return black;
            return basalt;
        };
    }

    private static boolean tickSun(Strike s, long now) {
        s.consumption.tick(now, Ton618Config.SERVER.blocksPerTick.get());
        if (now % 10 == 0) burn(s, Ton618Config.SERVER.scorchRadius.get() * 1.3 + 20);
        return s.consumption.done() && now - (s.start + StrikeTiming.S_IMPACT) > 20L * 8;
    }

    /** The impact's shock: everything is hurled outward, set ablaze; inside the Sun, nothing survives. */
    private static void blast(Strike s, double reach, boolean fire) {
        AABB box = new AABB(s.target, s.target).inflate(reach);
        for (Entity e : s.level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 away = e.position().subtract(s.target);
            double d = away.length();
            if (d > reach) continue;
            double k = 1.0 - d / reach;
            if (d < s.body.rs()) {
                if (e instanceof LivingEntity l) l.hurt(s.level.damageSources().inFire(), Float.MAX_VALUE);
                else e.discard();
                continue;
            }
            Vec3 push = (d < 1e-3 ? new Vec3(0, 1, 0) : away.normalize()).scale(0.6 + 3.2 * k).add(0, 0.6 + 1.4 * k, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
            if (fire) e.setSecondsOnFire((int) (6 + 20 * k));
            if (e instanceof LivingEntity l) l.hurt(s.level.damageSources().explosion(null, null), (float) (4 + 40 * k * k));
        }
    }

    private static void burn(Strike s, double reach) {
        AABB box = new AABB(s.target, s.target).inflate(reach);
        for (Entity e : s.level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e.position().distanceTo(s.target) < reach) e.setSecondsOnFire(8);
        }
    }

    // ------------------------------------------------------------------ the Archangel
    private static boolean tickAngel(Strike s, long now) {
        if (s.rift != null) s.rift.tick(Ton618Config.SERVER.blocksPerTick.get());
        if (now % 5 == 0 && now - (s.start + StrikeTiming.A_SLASH) < 20 * 5) riftBurn(s, s.body.facing());
        boolean riftDone = s.rift == null || s.rift.done();
        return riftDone && now - (s.start + StrikeTiming.A_SLASH) > 20L * 6;
    }

    /** The blow: the shock rolls out from the line of the cut, flinging everything away from it. */
    private static void riftBlast(Strike s, Vec3 dir) {
        double half = Ton618Config.SERVER.riftLength.get() * 0.5;
        double reach = 70;
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        AABB box = new AABB(s.target, s.target).inflate(half + reach, 160, half + reach);
        for (Entity e : s.level.getEntities((Entity) null, box, Entity::isAlive)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 rel = e.position().subtract(s.target);
            double along = rel.dot(dir);
            double off = rel.dot(across);
            if (Math.abs(along) > half || Math.abs(off) > reach) continue;
            double k = 1.0 - Math.abs(off) / reach;
            double sideSign = off >= 0 ? 1 : -1;
            e.setDeltaMovement(e.getDeltaMovement().add(across.scale(sideSign * (0.8 + 2.5 * k))).add(0, 0.8 + 1.6 * k, 0));
            e.hurtMarked = true;
            e.setSecondsOnFire((int) (4 + 10 * k));
            if (e instanceof LivingEntity l) l.hurt(s.level.damageSources().magic(), (float) (6 + 30 * k * k));
        }
    }

    /** Fire keeps rising from the rift for a few seconds: anything standing at its lip burns. */
    private static void riftBurn(Strike s, Vec3 dir) {
        double half = Ton618Config.SERVER.riftLength.get() * 0.5;
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        double w = Ton618Config.SERVER.riftWidth.get() * 0.5 + 6;
        AABB box = new AABB(s.target, s.target).inflate(half, 200, half);
        for (Entity e : s.level.getEntities((Entity) null, box, Entity::isAlive)) {
            Vec3 rel = e.position().subtract(s.target);
            if (Math.abs(rel.dot(dir)) < half && Math.abs(rel.dot(across)) < w) e.setSecondsOnFire(6);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
