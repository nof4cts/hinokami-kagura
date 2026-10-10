package com.shrine.client;

import com.shrine.client.anim.PlayerAnims;
import com.shrine.client.cinematic.Cutscene;
import com.shrine.common.Slash;
import com.shrine.common.Timing;
import com.shrine.network.S2CCast;
import com.shrine.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Every cast the client knows about, and everything that turns a cast's timeline into what's on screen:
 * per tick, the beats (impact frames, title-card sounds) and the strokes of barrages; per frame, the
 * parameters of every effect for technique.fsh ({@link Frame}).
 */
public final class Casts {
    private static final List<Active> ACTIVE = new ArrayList<>();

    private Casts() {}

    /** A slash fixed in the world from the tick it was struck. */
    record Stroke(Slash slash, double startTick, int kind, double speed, float seed, float intensity) {}

    static final class Active {
        final S2CCast msg;
        final List<Stroke> strokes = new ArrayList<>();
        int lastTick = -1;
        Vec3 fugaFrom;           // the arrow's launch point, frozen on release
        Vec3 fugaAim;
        long endTick;

        Active(S2CCast msg) {
            this.msg = msg;
            this.endTick = msg.startTick + switch (msg.move) {
                case DISMANTLE -> 80;
                case BARRAGE -> Timing.BARRAGE_END + 60;
                case CLEAVE, BLACK_FLASH -> 40;
                case SPIDERWEB -> 80;
                case FUGA -> Timing.FUGA_IMPACT + 20 * 9;
                case DOMAIN -> Timing.DOMAIN_OPEN + msg.targetId * 20L + 20;   // targetId carries the duration (s)
                case WORLD_SLASH -> Timing.WS_SLASH + 20 * 14;
            };
        }
    }

    public static void add(S2CCast msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Active a = new Active(msg);
        ACTIVE.add(a);
        Entity caster = mc.level.getEntity(msg.casterId);
        if (caster instanceof AbstractClientPlayer p) PlayerAnims.play(p, msg.move);
        if (msg.move.cutscene) {
            boolean mine = caster == mc.player;
            boolean near = mc.player.getEyePosition().distanceTo(msg.origin) < msg.cutsceneRadius;
            if (mine || near) Cutscene.start(msg);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    static double now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    private static void ui(SoundEvent e, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, 1.0f));
    }

    /** Close enough to feel it: impact frames only for casts near the viewer (or in their cutscene). */
    private static boolean near(Active a) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (Cutscene.isFor(a.msg)) return true;
        double reach = switch (a.msg.move) {
            case DOMAIN -> a.msg.size + 40;
            case FUGA -> a.msg.size * 6 + 40;
            case WORLD_SLASH -> 600;
            default -> 48;
        };
        return mc.gameRenderer.getMainCamera().getPosition().distanceTo(a.msg.origin) < reach
                || mc.gameRenderer.getMainCamera().getPosition().distanceTo(a.msg.point) < reach;
    }

    // ------------------------------------------------------------------ per tick: beats
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            int t = (int) (now - a.msg.startTick);
            for (int k = a.lastTick + 1; k <= t; k++) beat(a, k);
            a.lastTick = t;
            if (now > a.endTick) it.remove();
        }
    }

    private static final float[] CRIMSON = {0.95f, 0.08f, 0.06f};

    private static void hit(ScreenFx.Hit h, Vec3 at, float[] c) {
        ScreenFx.hit(h, at, 0.5f, 0.5f, c[0], c[1], c[2]);
        ui(ModSounds.IMPACT.get(), h == ScreenFx.Hit.LIGHT ? 1.3f : 0.95f);
    }

    private static void screenHit(ScreenFx.Hit h, float[] c) {
        ScreenFx.hit(h, null, 0.5f, 0.5f, c[0], c[1], c[2]);
        ui(ModSounds.IMPACT.get(), h == ScreenFx.Hit.LIGHT ? 1.3f : 0.9f);
    }

    private static void beat(Active a, int t) {
        Minecraft mc = Minecraft.getInstance();
        S2CCast m = a.msg;
        boolean close = near(a);
        Entity caster = mc.level.getEntity(m.casterId);
        switch (m.move) {
            case DISMANTLE -> {
                if (t == Timing.DISMANTLE_CUT) {
                    a.strokes.add(new Stroke(Slash.of(m.origin.subtract(m.dir.scale(0.5)), m.dir, Slash.roll(m.seed, 0), m.size, 16),
                            m.startTick + t, 0, 380, 1f, 1f));
                    if (close) hit(ScreenFx.Hit.LIGHT, m.origin.add(m.dir.scale(5)), CRIMSON);
                }
            }
            case BARRAGE -> {
                int k = t - Timing.BARRAGE_FIRST;
                if (k >= 0 && k % Timing.BARRAGE_EVERY == 0 && k / Timing.BARRAGE_EVERY < Timing.BARRAGE_COUNT) {
                    int i = k / Timing.BARRAGE_EVERY;
                    Vec3 eye = caster != null ? caster.getEyePosition() : m.origin;
                    Vec3 look = caster != null ? caster.getLookAngle() : m.dir;
                    Vec3 d = Slash.jitter(m.seed, i, look, 0.35);
                    a.strokes.add(new Stroke(Slash.of(eye.subtract(d.scale(0.5)), d, Slash.roll(m.seed, i), m.size * 0.75, 11),
                            m.startTick + t, 0, 420, i, 0.85f));
                    if (close && (i == 0 || i == Timing.BARRAGE_COUNT - 1)) {
                        hit(i == 0 ? ScreenFx.Hit.LIGHT : ScreenFx.Hit.HEAVY, eye.add(d.scale(6)), CRIMSON);
                    } else if (close) {
                        ScreenFx.cutFlash(0.08f);
                    }
                }
            }
            case CLEAVE -> {
                if (t == Timing.CLEAVE_HIT) {
                    Entity target = mc.level.getEntity(m.targetId);
                    Vec3 c = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : m.point;
                    double size = target != null ? Math.max(target.getBbWidth(), target.getBbHeight()) : Math.max(1, m.size);
                    RandomSource r = RandomSource.create(m.seed);
                    for (int i = 0; i < 16; i++) {
                        Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                        Vec3 off = new Vec3(r.nextGaussian() * 0.25, r.nextGaussian() * 0.4, r.nextGaussian() * 0.25).scale(size);
                        double len = size * (1.1 + r.nextDouble() * 0.6);
                        Vec3 o = c.add(off).subtract(dir.scale(len * 0.5));
                        a.strokes.add(new Stroke(Slash.of(o, dir, 0, len, 0), m.startTick + t + i * 0.25, 1, 0, i, 1f));
                    }
                    if (close) hit(ScreenFx.Hit.HEAVY, c, CRIMSON);
                }
            }
            case SPIDERWEB -> {
                if (t == Timing.WEB_HIT && close) hit(ScreenFx.Hit.HEAVY, m.point, CRIMSON);
            }
            case BLACK_FLASH -> {
                if (t == Timing.FLASH_HIT && close) {
                    hit(ScreenFx.Hit.HEAVY, m.point, new float[]{1.0f, 0.0f, 0.12f});
                    ScreenFx.shock(m.point, 0.8f);
                }
            }
            case FUGA -> {
                if (t == Timing.FUGA_CARD && Cutscene.isFor(m)) ui(ModSounds.CARD.get(), 1.0f);
                if (t == Timing.FUGA_DRAW) {
                    if (close) ui(ModSounds.FUGA_DRAW.get(), 1.0f);
                }
                if (t == Timing.FUGA_RELEASE) {
                    a.fugaFrom = fugaBow(caster, m, 0f);
                    a.fugaAim = m.point.subtract(a.fugaFrom).normalize();
                    if (close) hit(ScreenFx.Hit.LIGHT, a.fugaFrom, new float[]{1f, 0.45f, 0.05f});
                }
                if (t == Timing.FUGA_IMPACT && close) {
                    ScreenFx.impact(m.point, m.size, 1.0f, 0.3f, 0.02f);
                }
            }
            case DOMAIN -> {
                if (Cutscene.isFor(m)) {
                    if (t == Timing.DOMAIN_CARD) {
                        ui(ModSounds.CARD.get(), 0.8f);
                        screenHit(ScreenFx.Hit.HEAVY, CRIMSON);
                    }
                    if (t == Timing.DOMAIN_NAME) ui(ModSounds.CARD.get(), 0.6f);
                }
                if (t == Timing.DOMAIN_OPEN && close) {
                    screenHit(ScreenFx.Hit.ULTIMATE, CRIMSON);
                    ScreenFx.flashAfterHit(0.7f);
                }
            }
            case WORLD_SLASH -> {
                if ((t == Timing.WS_SIGN1 || t == Timing.WS_SIGN2 || t == Timing.WS_SIGN3) && Cutscene.isFor(m)) {
                    screenHit(ScreenFx.Hit.LIGHT, new float[]{0.85f, 0.85f, 1f});
                }
                if (t == Timing.WS_SLASH && close) {
                    screenHit(ScreenFx.Hit.ULTIMATE, new float[]{0.9f, 0.9f, 1.0f});
                    ScreenFx.flashAfterHit(0.9f);
                }
                if (t == Timing.WS_SPLIT && close) screenHit(ScreenFx.Hit.HEAVY, new float[]{0.55f, 0.1f, 0.9f});
            }
        }
    }

    // ------------------------------------------------------------------ Open: where the fire is
    static Vec3[] basis(Vec3 aim) {
        Vec3 right = aim.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(aim).normalize();
        return new Vec3[]{aim, right, up};
    }

    static Vec3 eyeOf(Entity caster, S2CCast m, float partial) {
        return caster != null ? caster.getEyePosition(partial) : m.origin;
    }

    static Vec3 aimOf(Entity caster, S2CCast m, float partial) {
        return caster != null ? caster.getViewVector(partial) : m.dir;
    }

    /** The open hand, in front and to the right of the chest. */
    public static Vec3 fugaHand(Entity caster, S2CCast m, float partial) {
        Vec3[] b = basis(aimOf(caster, m, partial));
        return eyeOf(caster, m, partial).add(b[0].scale(0.55)).add(b[1].scale(0.32)).subtract(b[2].scale(0.3));
    }

    /** The bow, held out in front. */
    public static Vec3 fugaBow(Entity caster, S2CCast m, float partial) {
        Vec3[] b = basis(aimOf(caster, m, partial));
        return eyeOf(caster, m, partial).add(b[0].scale(0.8)).subtract(b[2].scale(0.18)).add(b[1].scale(0.04));
    }

    // ------------------------------------------------------------------ per frame: effect parameters
    /** Everything technique.fsh needs this frame (positions are world-space; PostFx makes them camera-relative). */
    public static final class Frame {
        public final List<float[]> slashes = new ArrayList<>();   // each: origin(3), age, dir(3), len, side(3), width, kind, speed, seed, intensity
        public Vec3 webC;
        public float webR, webAge = -1, webSeed;
        public Vec3 bfC;
        public float bfAge = -1, bfSeed;
        public boolean fuga;
        public Vec3 fHand, fBow, fDir, fUp, fFrom, fTarget;
        public float fHandAmt, fBowAmt, fDraw, fFlight = -1, fBlast = -1, fRadius;
        public Vec3 domC;
        public float domR, domAge, domStrength, domFull;
        public boolean domain;
        public Vec3 wsO, wsD;
        public float wsAge = -1, wsLen, wsSplit, wsSky;
        public boolean ws;
        public Vec3 shake;      // unused

        public boolean any() {
            return !slashes.isEmpty() || webAge >= 0 || bfAge >= 0 || fuga || domain || ws;
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    public static Frame frame(float partial) {
        Frame f = new Frame();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return f;
        double now = now(partial);
        for (Active a : ACTIVE) {
            S2CCast m = a.msg;
            float t = (float) (now - m.startTick);
            Entity caster = mc.level.getEntity(m.casterId);
            for (Stroke s : a.strokes) {
                float age = (float) ((now - s.startTick) / 20.0);
                if (age > 3.0f || f.slashes.size() >= 48) continue;
                Slash sl = s.slash;
                f.slashes.add(new float[]{
                        (float) sl.origin().x, (float) sl.origin().y, (float) sl.origin().z, age,
                        (float) sl.dir().x, (float) sl.dir().y, (float) sl.dir().z, (float) sl.length(),
                        (float) sl.side().x, (float) sl.side().y, (float) sl.side().z, (float) sl.width(),
                        s.kind, (float) s.speed, s.seed, s.intensity});
            }
            switch (m.move) {
                case SPIDERWEB -> {
                    float age = (t - Timing.WEB_HIT) / 20f;
                    if (age >= 0 && age < 3.5f) {
                        f.webC = m.point;
                        f.webR = m.size;
                        f.webAge = age;
                        f.webSeed = (m.seed & 1023) / 7f;
                    }
                }
                case BLACK_FLASH -> {
                    float age = (t - Timing.FLASH_HIT) / 20f;
                    if (age >= 0 && age < 0.7f) {
                        f.bfC = m.point;
                        f.bfAge = age;
                        f.bfSeed = (m.seed & 1023) / 11f;
                    }
                }
                case FUGA -> fugaFrame(f, a, caster, t, partial);
                case DOMAIN -> {
                    float open = (t - Timing.DOMAIN_OPEN);
                    if (open >= 0) {
                        float g = Mth.clamp(open / Timing.DOMAIN_SPREAD, 0f, 1f);
                        f.domain = true;
                        f.domC = m.point;
                        f.domFull = m.size;
                        f.domR = (float) (m.size * (1 - Math.pow(1 - g, 2.5)));
                        f.domAge = open / 20f;
                        float left = (a.endTick - 20 - (float) now) / 20f;
                        f.domStrength = smooth(open / 6f) * smooth(left);
                    } else if (t >= Timing.DOMAIN_CARD) {
                        // the world darkens while the name is spoken
                        f.domain = true;
                        f.domC = m.point;
                        f.domFull = m.size;
                        f.domR = 1.5f;
                        f.domAge = 0;
                        f.domStrength = 0.35f * smooth((t - Timing.DOMAIN_CARD) / 20f);
                    }
                }
                case WORLD_SLASH -> {
                    if (t >= Timing.WS_SLASH) {
                        f.ws = true;
                        f.wsO = m.point;
                        f.wsD = m.dir;
                        f.wsLen = m.size;
                        f.wsAge = (t - Timing.WS_SLASH) / 20f;
                        float split = smooth((t - Timing.WS_SPLIT) / 16f) * (1f - smooth((t - Timing.WS_END) / 30f));
                        f.wsSplit = 0.028f * split;
                        f.wsSky = 1f - smooth((t - Timing.WS_END - 40) / 80f);
                    }
                }
                default -> {}
            }
        }
        return f;
    }

    private static void fugaFrame(Frame f, Active a, Entity caster, float t, float partial) {
        S2CCast m = a.msg;
        if (t > Timing.FUGA_IMPACT + 20 * 8) return;
        f.fuga = true;
        f.fRadius = m.size;
        f.fTarget = m.point;
        Vec3 aim = a.fugaAim != null ? a.fugaAim : aimOf(caster, m, partial);
        Vec3[] b = basis(aim);
        f.fDir = b[0];
        f.fUp = b[2];
        f.fHand = fugaHand(caster, m, partial);
        f.fBow = a.fugaFrom != null ? a.fugaFrom : fugaBow(caster, m, partial);
        f.fFrom = a.fugaFrom != null ? a.fugaFrom : f.fBow;
        float released = smooth((t - Timing.FUGA_RELEASE) / 5f);
        // fire pours out of the hand, then draws into the bow's shape
        f.fHandAmt = smooth(t / 14f) * (1f - 0.75f * smooth((t - Timing.FUGA_BOW) / 14f)) * (1f - released);
        f.fBowAmt = smooth((t - Timing.FUGA_BOW) / 12f) * (1f - released);
        // the draw: slow, heavy, the last few inches slowest
        float d = Mth.clamp((t - Timing.FUGA_DRAW) / (Timing.FUGA_RELEASE - Timing.FUGA_DRAW - 4f), 0f, 1f);
        f.fDraw = (1f - (1f - d) * (1f - d) * (1f - d)) * (1f - released);
        if (t >= Timing.FUGA_RELEASE && t <= Timing.FUGA_IMPACT) {
            f.fFlight = (t - Timing.FUGA_RELEASE) / (float) Timing.FUGA_FLIGHT;
            f.fFlight = f.fFlight * f.fFlight * (0.4f + 0.6f * f.fFlight);   // accelerates away
        }
        if (t >= Timing.FUGA_IMPACT) f.fBlast = (t - Timing.FUGA_IMPACT) / 20f;
    }

    public static boolean any() {
        return !ACTIVE.isEmpty();
    }
}
