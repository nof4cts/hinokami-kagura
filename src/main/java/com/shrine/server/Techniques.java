package com.shrine.server;

import com.shrine.ShrineMod;
import com.shrine.common.Move;
import com.shrine.common.Slash;
import com.shrine.common.Timing;
import com.shrine.config.ShrineConfig;
import com.shrine.network.Net;
import com.shrine.network.S2CCast;
import com.shrine.network.S2CCooldowns;
import com.shrine.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The technique, server side: validates every cast, runs each move's timeline (see {@link Timing}), deals the
 * damage and cuts the world. The look of everything is the clients' job; they get one {@link S2CCast} per cast.
 */
@Mod.EventBusSubscriber(modid = ShrineMod.MODID)
public final class Techniques {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final Map<UUID, long[]> READY = new HashMap<>();
    private static final Map<UUID, Long> BUSY = new HashMap<>();
    private static final List<Cast> ACTIVE = new ArrayList<>();

    private Techniques() {}

    private static final class Cast {
        final Move move;
        final ServerPlayer caster;
        final ServerLevel level;
        final long start;
        final Vec3 origin, dir, point;
        final long seed;
        final float size;
        final Entity target;
        Consumption blast;
        Leveler leveler;
        PlaneCut cut;
        long endsAt;

        Cast(Move move, ServerPlayer caster, long start, Vec3 origin, Vec3 dir, Vec3 point, long seed, float size, Entity target) {
            this.move = move;
            this.caster = caster;
            this.level = caster.serverLevel();
            this.start = start;
            this.origin = origin;
            this.dir = dir;
            this.point = point;
            this.seed = seed;
            this.size = size;
            this.target = target;
            this.endsAt = start + Timing.end(move);
        }
    }

    private static int cooldown(Move m) {
        return (int) Math.round(m.cooldown * ShrineConfig.SERVER.cooldownScale.get());
    }

    // ------------------------------------------------------------------ casting
    public static void cast(ServerPlayer p, Move move) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (ShrineConfig.SERVER.opOnly.get() && !p.hasPermissions(2)) return;
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
        Long busy = BUSY.get(p.getUUID());
        if (now < ready[move.ordinal()] || (busy != null && now < busy) || p.isSpectator()) {
            p.playNotifySound(ModSounds.DENY.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
            return;
        }

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        long seed = level.random.nextLong();
        Vec3 point = eye;
        float size = 0f;
        Entity target = null;
        switch (move) {
            case DISMANTLE, BARRAGE -> size = ShrineConfig.SERVER.dismantleLength.get();
            case CLEAVE -> {
                target = pick(p, 7.0);
                if (target == null) {
                    p.playNotifySound(ModSounds.DENY.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
                    return;
                }
                point = target.position().add(0, target.getBbHeight() * 0.5, 0);
                size = Math.max(target.getBbWidth(), target.getBbHeight());
            }
            case BLACK_FLASH -> {
                target = pick(p, 5.0);
                point = target != null ? target.position().add(0, target.getBbHeight() * 0.55, 0) : eye.add(look.scale(2.2));
            }
            case SPIDERWEB -> {
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(6)), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, p));
                point = hit.getType() == HitResult.Type.MISS ? p.position() : hit.getLocation();
                size = 24f;
            }
            case FUGA -> {
                double range = ShrineConfig.SERVER.fugaRange.get();
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, p));
                point = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : hit.getLocation();
                Entity e = pick(p, eye.distanceTo(point));
                if (e != null) point = e.position().add(0, e.getBbHeight() * 0.5, 0);
                size = ShrineConfig.SERVER.fugaRadius.get();
            }
            case DOMAIN -> {
                point = p.position();
                size = ShrineConfig.SERVER.domainRadius.get();
            }
            case WORLD_SLASH -> {
                Vec3 flat = new Vec3(look.x, 0, look.z);
                look = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
                point = p.position();
                size = ShrineConfig.SERVER.worldSlashLength.get();
            }
        }

        ready[move.ordinal()] = now + cooldown(move);
        if (move.cutscene) BUSY.put(p.getUUID(), now + (move == Move.DOMAIN ? Timing.DOMAIN_CUT_END : Timing.end(move)));
        Cast c = new Cast(move, p, now, eye, look, point, seed, size, target);
        if (move == Move.DOMAIN) c.endsAt = now + Timing.DOMAIN_OPEN + ShrineConfig.SERVER.domainSeconds.get() * 20L;
        ACTIVE.add(c);

        S2CCast msg = new S2CCast(move, p.getId(), now, eye, look, point, seed, size,
                move == Move.DOMAIN ? ShrineConfig.SERVER.domainSeconds.get() : target == null ? -1 : target.getId(),
                ShrineConfig.SERVER.cutsceneRadius.get());
        double reach = move == Move.WORLD_SLASH ? Math.max(512, size) : 512;
        Net.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                eye.x, eye.y, eye.z, reach, level.dimension())), msg);
        sendCooldowns(p);
    }

    private static void sendCooldowns(ServerPlayer p) {
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
        int[] total = new int[Move.values().length];
        for (Move m : Move.values()) total[m.ordinal()] = cooldown(m);
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new S2CCooldowns(ready.clone(), total));
    }

    /** The living thing the player is looking at, within reach (generous aim assist). */
    private static Entity pick(ServerPlayer p, double reach) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(reach));
        AABB box = p.getBoundingBox().expandTowards(p.getLookAngle().scale(reach)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
                e -> e.isAlive() && e.isPickable() && e != p, 0.8f);
        return hit == null ? null : hit.getEntity();
    }

    // ------------------------------------------------------------------ timelines
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (ACTIVE.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Cast> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cast c = it.next();
            if (c.level != level) continue;
            int t = (int) (now - c.start);
            if (c.caster.isRemoved() && c.move.cutscene && t < 2) {
                it.remove();
                continue;
            }
            switch (c.move) {
                case DISMANTLE -> {
                    if (t == Timing.DISMANTLE_CUT) {
                        slash(c, Slash.of(c.origin.subtract(c.dir.scale(0.5)), c.dir, Slash.roll(c.seed, 0), c.size, 16));
                        sound(c, c.origin, ModSounds.DISMANTLE.get(), 2.5f, 1f);
                    }
                }
                case BARRAGE -> {
                    int k = t - Timing.BARRAGE_FIRST;
                    if (k >= 0 && k % Timing.BARRAGE_EVERY == 0 && k / Timing.BARRAGE_EVERY < Timing.BARRAGE_COUNT) {
                        int i = k / Timing.BARRAGE_EVERY;
                        // follows the caster's aim while it rains
                        Vec3 eye = c.caster.getEyePosition();
                        Vec3 d = Slash.jitter(c.seed, i, c.caster.getLookAngle(), 0.35);
                        slash(c, Slash.of(eye.subtract(d.scale(0.5)), d, Slash.roll(c.seed, i), c.size * 0.75, 11));
                        if (i == 0) sound(c, eye, ModSounds.BARRAGE.get(), 2.5f, 1f);
                    }
                }
                case CLEAVE -> {
                    if (t == Timing.CLEAVE_HIT && c.target != null && c.target.isAlive()) {
                        hit(c, c.target, 1.0);
                        sound(c, c.point, ModSounds.CLEAVE.get(), 2f, 1f);
                    }
                }
                case BLACK_FLASH -> {
                    if (t == Timing.FLASH_HIT) blackFlash(c);
                }
                case SPIDERWEB -> {
                    if (t == Timing.WEB_HIT) spiderweb(c);
                }
                case FUGA -> fuga(c, t);
                case DOMAIN -> domain(c, t, now);
                case WORLD_SLASH -> worldSlash(c, t);
            }
            if (now >= c.endsAt) {
                it.remove();
                if (c.caster.isAlive()) sendCooldowns(c.caster);
            }
        }
    }

    private static void sound(Cast c, Vec3 at, SoundEvent e, float volume, float pitch) {
        c.level.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, volume, pitch);
    }

    private static DamageSource source(Cast c) {
        return c.level.damageSources().indirectMagic(c.caster, c.caster);
    }

    /** One blow. Ignores invulnerability frames; things that can't be hurt (items, boats...) are simply cut apart. */
    private static void hit(Cast c, Entity e, double scale) {
        if (e == c.caster || !e.isAlive()) return;
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return;
        double dmg = ShrineConfig.SERVER.damage.get() * scale;
        if (e instanceof LivingEntity l) {
            l.invulnerableTime = 0;
            l.hurt(source(c), (float) Math.min(dmg, Float.MAX_VALUE));
            // anything that shrugs it off (bosses with caps) is cut again, directly
            if (l.isAlive() && scale >= 1.0 && dmg >= 1000) {
                l.invulnerableTime = 0;
                l.hurt(c.level.damageSources().genericKill(), Float.MAX_VALUE);
            }
        } else if (!(e instanceof Player)) {
            e.discard();
        }
    }

    // ------------------------------------------------------------------ Dismantle
    /** One stroke: everything in its slab is cut, and the world along it is cut through, one block thick. */
    private static void slash(Cast c, Slash s) {
        AABB box = new AABB(s.origin(), s.origin().add(s.dir().scale(s.length()))).inflate(s.width() * 0.5 + 2);
        for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
            double t = Math.max(e.getBbWidth(), 0.6) * 0.5 + 0.9;
            Vec3 base = e.position();
            if (s.contains(base.add(0, e.getBbHeight() * 0.5, 0), t + e.getBbHeight() * 0.3, s.length())
                    || s.contains(base.add(0, e.getBbHeight() * 0.9, 0), t, s.length())
                    || s.contains(base.add(0, 0.1, 0), t, s.length())) {
                hit(c, e, 1.0);
            }
        }
        if (!ShrineConfig.SERVER.cutBlocks.get()) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double hw = s.width() * 0.5;
        for (double a = 1.5; a <= s.length(); a += 0.45) {
            for (double w = -hw; w <= hw; w += 0.45) {
                Vec3 p = s.origin().add(s.dir().scale(a)).add(s.side().scale(w));
                pos.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
                BlockState st = c.level.getBlockState(pos);
                if (st.isAir() || st.is(Blocks.BEDROCK) || !st.getFluidState().isEmpty()) continue;
                if (st.getDestroySpeed(c.level, pos) < 0) continue;
                c.level.setBlock(pos, AIR, FLAGS);
            }
        }
    }

    // ------------------------------------------------------------------ Black Flash
    private static void blackFlash(Cast c) {
        sound(c, c.point, ModSounds.BLACK_FLASH.get(), 3f, 1f);
        AABB box = new AABB(c.point, c.point).inflate(2.5);
        for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
            Vec3 push = e.position().subtract(c.caster.position()).multiply(1, 0, 1);
            push = push.lengthSqr() < 1e-4 ? c.dir : push.normalize();
            hit(c, e, 1.0);
            e.setDeltaMovement(push.scale(4.5).add(0, 0.8, 0));
            e.hurtMarked = true;
        }
        // the shockwave dents the ground around the point of impact
        if (ShrineConfig.SERVER.cutBlocks.get()) crater(c.level, BlockPos.containing(c.point), 3);
    }

    private static void crater(ServerLevel level, BlockPos centre, int r) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz > r * r) continue;
            pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
            BlockState st = level.getBlockState(pos);
            if (!st.isAir() && st.getDestroySpeed(level, pos) >= 0) level.setBlock(pos, AIR, FLAGS);
        }
    }

    // ------------------------------------------------------------------ Spiderweb
    /** A palm to the ground: cuts run out radially and in rings, and the ground between them collapses. */
    private static void spiderweb(Cast c) {
        sound(c, c.point, ModSounds.SPIDERWEB.get(), 4f, 1f);
        double r = c.size;
        AABB box = new AABB(c.point, c.point).inflate(r, 10, r);
        for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
            if (e.position().distanceTo(c.point) > r) continue;
            hit(c, e, 1.0);
            e.setDeltaMovement(e.getDeltaMovement().add(0, -1.0, 0));
            e.hurtMarked = true;
        }
        if (!ShrineConfig.SERVER.cutBlocks.get()) return;
        RandomSource rand = RandomSource.create(c.seed);
        BlockPos centre = BlockPos.containing(c.point);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int R = (int) r;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R) continue;
                double ang = Math.atan2(dz, dx);
                double spoke = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang) * 1.0 + 15) % 30.0);
                boolean onSpoke = Math.min(spoke, 30 - spoke) * Math.PI / 180 * d < 0.9;
                boolean onRing = Math.abs(d - 7) < 0.7 || Math.abs(d - 14) < 0.7 || Math.abs(d - 21) < 0.7;
                int depth;
                if (onSpoke || onRing) depth = 9;
                else depth = rand.nextFloat() < 0.55 * (1 - d / R) + 0.2 ? 2 + rand.nextInt(3) : 0;
                if (depth == 0) continue;
                for (int dy = 2; dy >= -depth; dy--) {
                    pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState st = c.level.getBlockState(pos);
                    if (st.isAir() || st.getDestroySpeed(c.level, pos) < 0) continue;
                    c.level.setBlock(pos, AIR, FLAGS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Open
    private static void fuga(Cast c, int t) {
        if (t == Timing.FUGA_IGNITE) sound(c, c.origin, ModSounds.FUGA_IGNITE.get(), 3f, 1f);
        if (t == Timing.FUGA_RELEASE) sound(c, c.origin, ModSounds.FUGA_RELEASE.get(), 4f, 1f);
        if (t > Timing.FUGA_RELEASE && t <= Timing.FUGA_IMPACT) {
            // the arrow burns through anything in its path
            float u0 = (t - 1 - Timing.FUGA_RELEASE) / (float) Timing.FUGA_FLIGHT;
            float u1 = (t - Timing.FUGA_RELEASE) / (float) Timing.FUGA_FLIGHT;
            Vec3 from = c.origin.lerp(c.point, u0), to = c.origin.lerp(c.point, u1);
            for (Entity e : c.level.getEntities(c.caster, new AABB(from, to).inflate(2.5), Entity::isAlive)) {
                hit(c, e, 1.0);
                e.setSecondsOnFire(30);
            }
        }
        if (t == Timing.FUGA_IMPACT) {
            sound(c, c.point, ModSounds.FUGA_IMPACT.get(), 40f, 1f);
            double r = c.size;
            AABB box = new AABB(c.point, c.point).inflate(r * 1.8);
            for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
                double d = e.position().distanceTo(c.point);
                if (d > r * 1.8) continue;
                double k = 1 - d / (r * 1.8);
                if (d < r * 1.1) hit(c, e, 1.0);
                else if (e instanceof LivingEntity l) l.hurt(source(c), (float) (20 + 200 * k));
                e.setSecondsOnFire(30);
                Vec3 away = e.position().subtract(c.point);
                away = away.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : away.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(away.scale(1 + 3 * k)).add(0, 1 + 2 * k, 0));
                e.hurtMarked = true;
            }
            if (ShrineConfig.SERVER.cutBlocks.get()) {
                c.blast = new Consumption(c.level, c.point, (int) r + 12, c.level.getGameTime(), 30).with(scorcher(c, (int) r), null);
            }
        }
        if (c.blast != null) c.blast.tick(c.level.getGameTime(), ShrineConfig.SERVER.blocksPerTick.get());
        if (t > Timing.FUGA_IMPACT && t % 10 == 0) {
            // the pillar keeps burning: anything that comes near it catches
            for (Entity e : c.level.getEntities(c.caster, new AABB(c.point, c.point).inflate(c.size * 1.3, c.size * 4, c.size * 1.3), Entity::isAlive)) {
                e.setSecondsOnFire(10);
            }
        }
        if (t == Timing.FUGA_END - 1 && c.blast != null && !c.blast.done()) c.endsAt += 200;  // finish the crater
    }

    private static Consumption.Replacer scorcher(Cast c, int r) {
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState black = Blocks.BLACKSTONE.defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState fire = Blocks.FIRE.defaultBlockState();
        return (pos, old, d, dx, dy, dz) -> {
            if (d <= r) {
                double floor = -Math.sqrt(Math.max(0.0, (double) r * r - dx * dx - dz * dz));
                if (dy < floor + 3 && dy < -r * 0.3) return lava;
                if (old.is(Blocks.BEDROCK)) return null;
                return old.isAir() ? null : AIR;
            }
            if (old.isAir()) {
                if (d < r + 8 && c.level.random.nextFloat() < 0.15f) {
                    BlockState below = c.level.getBlockState(pos.below());
                    if (!below.isAir() && below.isFaceSturdy(c.level, pos.below(), Direction.UP)) return fire;
                }
                return null;
            }
            if (!old.getFluidState().isEmpty()) return old.getFluidState().is(FluidTags.WATER) ? AIR : null;
            if (old.is(Blocks.BEDROCK)) return null;
            if (old.is(BlockTags.LEAVES) || old.is(BlockTags.LOGS) || old.is(BlockTags.REPLACEABLE) || old.is(BlockTags.FLOWERS))
                return AIR;
            float f = c.level.random.nextFloat();
            double k = (d - r) / 12.0;
            if (f < 0.55 * (1 - k)) return magma;
            if (f < 0.8) return black;
            return basalt;
        };
    }

    // ------------------------------------------------------------------ Domain Expansion
    private static void domain(Cast c, int t, long now) {
        if (t == Timing.DOMAIN_SIGN) sound(c, c.origin, ModSounds.DOMAIN_SIGN.get(), 6f, 1f);
        if (t == Timing.DOMAIN_OPEN - 20) sound(c, c.origin, ModSounds.DOMAIN_OPEN.get(), 30f, 1f);   // the inhale leads the hit
        if (t < Timing.DOMAIN_OPEN) return;
        double grow = Mth.clamp((t - Timing.DOMAIN_OPEN) / (double) Timing.DOMAIN_SPREAD, 0, 1);
        double radius = c.size * (1 - Math.pow(1 - grow, 2.5));
        if (t % 4 == 0) {
            // sure-hit: everything alive inside is cut, over and over
            Vec3 centre = c.point;
            AABB box = new AABB(centre, centre).inflate(radius, radius, radius);
            for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
                if (e.position().distanceTo(centre) <= radius) hit(c, e, 1.0);
            }
        }
        if ((t - Timing.DOMAIN_OPEN) % 60 == 0) sound(c, c.point, ModSounds.DOMAIN_SHRED.get(), 20f, 1f);
        if (ShrineConfig.SERVER.cutBlocks.get()) {
            if (c.leveler == null) {
                c.leveler = new Leveler(c.level, BlockPos.containing(c.point), (int) c.size, Mth.floor(c.point.y));
            }
            c.leveler.allow(radius);
            c.leveler.tick(ShrineConfig.SERVER.blocksPerTick.get());
        }
    }

    // ------------------------------------------------------------------ World-Cutting Slash
    private static void worldSlash(Cast c, int t) {
        if (t == Timing.WS_SIGN1 || t == Timing.WS_SIGN2 || t == Timing.WS_SIGN3) {
            sound(c, c.origin, ModSounds.WORLD_SIGN.get(), 4f, t == Timing.WS_SIGN3 ? 0.8f : 1f);
        }
        if (t == Timing.WS_SLASH) {
            sound(c, c.origin, ModSounds.WORLD_SLASH.get(), 60f, 1f);
            // everything on the plane of the cut, all the way along it, is cut
            Vec3 n = new Vec3(-c.dir.z, 0, c.dir.x);
            double len = c.size;
            Vec3 a = c.point, b = c.point.add(c.dir.scale(len));
            AABB box = new AABB(a, b).inflate(4, 400, 4);
            for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
                Vec3 rel = e.position().subtract(c.point);
                double along = rel.dot(c.dir);
                if (along < 0 || along > len) continue;
                if (Math.abs(rel.dot(n)) > e.getBbWidth() * 0.5 + 2.5) continue;
                hit(c, e, 1.0);
                if (e.isAlive() && e instanceof LivingEntity l) l.hurt(c.level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            }
            if (ShrineConfig.SERVER.cutBlocks.get()) c.cut = new PlaneCut(c.level, c.point.add(c.dir.scale(2.5)), c.dir, (int) len, 2);
        }
        if (t == Timing.WS_SPLIT) sound(c, c.origin, ModSounds.WORLD_SPLIT.get(), 60f, 1f);
        if (c.cut != null) {
            c.cut.tick(ShrineConfig.SERVER.blocksPerTick.get());
            if (t == Timing.WS_END - 1 && !c.cut.done()) c.endsAt += 600;
        }
    }

    // ------------------------------------------------------------------ housekeeping
    /** Nothing can touch the caster while a cutscene move is being performed. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Long busy = BUSY.get(p.getUUID());
        if (busy != null && p.level().getGameTime() < busy) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sendCooldowns(sp);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        READY.clear();
        BUSY.clear();
    }
}
