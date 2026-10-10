package com.ton618.client.cinematic;

import com.ton618.client.ClientHoles;
import com.ton618.client.ScreenFx;
import com.ton618.common.BlackHole;
import com.ton618.common.StrikeKind;
import com.ton618.network.S2CCutscene;
import com.ton618.registry.ModRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static com.ton618.common.StrikeTiming.*;

/**
 * The strike cutscene director: one film per strike kind, every shot a camera move with eased motion
 * (smootherstep in and out, never linear), cut on the action, with impact frames and sound on each beat.
 *
 * TON 618 ........ the cannon beyond the galaxy wakes, charges and fires; we ride the beam across the galaxy;
 *                  it runs a star through; the star convulses and implodes, and a black hole is born from it,
 *                  spinning up a disk from the star's remains; a second pulse drives it out; it falls across
 *                  the galaxy, through Earth's sky, and onto the target.
 * THE SUN ........ the cannon fires; the beam seizes the Sun and tears it from its place; it swells over the
 *                  Earth; then from the ground, the sky turns to fire as it comes down.
 * ARCHANGEL ...... the clouds part over the target and a pillar of light comes down; the Archangel descends
 *                  it, settles, raises a sword of fire, and brings it down. The world splits.
 *
 * Camera control: a client-only Marker entity becomes the camera entity and is posed every frame (before the
 * frame renders), so motion is smooth at any frame rate. Movement is blocked, hand and HUD hidden, letterbox
 * and captions drawn. Hold SHIFT for one second to skip.
 */
public final class Cutscene {
    private static S2CCutscene active;
    private static StrikeKind kind = StrikeKind.HOLE;
    private static Marker camera;
    private static CameraType savedCamera;
    private static Vec3 shooterEye = Vec3.ZERO;
    private static Vec3 travel = new Vec3(0, 0, 1);
    private static Vec3 side = new Vec3(1, 0, 0);
    private static float groundY;
    private static int lastCue = -1;
    private static int skipHeld;
    @Nullable
    private static Pose pose;

    private Cutscene() {}

    public static boolean active() {
        return active != null;
    }

    public static StrikeKind kind() {
        return kind;
    }

    @Nullable
    public static Pose pose() {
        return active == null ? null : pose;
    }

    public static void start(S2CCutscene msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        stop();
        active = msg;
        kind = StrikeKind.byId(msg.kind);
        lastCue = -1;
        skipHeld = 0;
        Entity shooter = mc.level.getEntity(msg.shooterId);
        shooterEye = shooter != null ? shooter.getEyePosition() : mc.player.getEyePosition();
        Vec3 flat = msg.target.subtract(shooterEye).multiply(1, 0, 1);
        if (flat.lengthSqr() < 1.0) flat = msg.target.subtract(msg.from).multiply(1, 0, 1);
        travel = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        side = travel.cross(new Vec3(0, 1, 0)).normalize();
        groundY = (float) ground(msg.target.x, msg.target.z, msg.target.y);

        camera = new Marker(EntityType.MARKER, mc.level);
        camera.setPos(mc.player.getEyePosition());
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        active = null;
        pose = null;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        savedCamera = null;
        camera = null;
    }

    private static double ground(double x, double z, double fallback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return fallback;
        int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        return h <= mc.level.getMinBuildHeight() ? fallback : h;
    }

    /** Keeps world cameras above the terrain. */
    private static Vec3 aboveGround(Vec3 p, double clearance) {
        double g = ground(p.x, p.z, groundY);
        return p.y < g + clearance ? new Vec3(p.x, g + clearance, p.z) : p;
    }

    private static float time(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0f : (float) (mc.level.getGameTime() + partial - active.startTick);
    }

    private static int end() {
        return switch (kind) {
            case HOLE -> H_END;
            case SUN -> S_END;
            case ANGEL -> A_END;
        };
    }

    // ------------------------------------------------------------------ per tick: sounds, impact frames, skip
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        int t = (int) time(0f);
        skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0;
        if (t >= end() || skipHeld >= 20) {
            stop();
            return;
        }
        for (int cue = lastCue + 1; cue <= t; cue++) {
            switch (kind) {
                case HOLE -> cueHole(cue);
                case SUN -> cueSun(cue);
                case ANGEL -> cueAngel(cue);
            }
        }
        lastCue = t;
    }

    private static void score(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 1.0f));
    }

    private static void beat(ScreenFx.Hit hit, float u, float v, float r, float g, float b) {
        ScreenFx.hit(hit, null, u, v, r, g, b);
        score(ModRegistry.IMPACT_HIT.get(), hit == ScreenFx.Hit.LIGHT ? 1.25f : 0.9f);
    }

    private static void worldBeat(ScreenFx.Hit hit, Vec3 at, float r, float g, float b) {
        ScreenFx.hit(hit, at, 0.5f, 0.5f, r, g, b);
        score(ModRegistry.IMPACT_HIT.get(), hit == ScreenFx.Hit.LIGHT ? 1.25f : 0.9f);
    }

    private static void cueHole(int t) {
        if (t == 0) score(ModRegistry.CANNON_CHARGE.get(), 1.0f);
        if (t == 60 || t == 70 || t == 78) score(ModRegistry.REMOTE_BEEP.get(), t == 78 ? 1.3f : 1.0f);
        if (t == H_FIRE) {
            score(ModRegistry.CANNON_FIRE.get(), 1.0f);
            beat(ScreenFx.Hit.LIGHT, 0.5f, 0.5f, 0.6f, 0.8f, 1.0f);
        }
        if (t == H_FIRE + 2) score(ModRegistry.CANNON_BEAM.get(), 1.0f);
        if (t == H_CROSS || t == H_HOME) score(ModRegistry.CUT_WHOOSH.get(), t == H_HOME ? 0.8f : 1.0f);
        if (t == H_PIERCE_HIT) {
            score(ModRegistry.STAR_PIERCE.get(), 1.0f);
            beat(ScreenFx.Hit.HEAVY, 0.5f, 0.55f, 1.0f, 0.55f, 0.15f);
        }
        if (t == H_COLLAPSE - 50) score(ModRegistry.STAR_COLLAPSE.get(), 1.0f);   // the boom lands on the collapse
        if (t == H_COLLAPSE) {
            beat(ScreenFx.Hit.HEAVY, 0.5f, 0.55f, 0.62f, 0.22f, 1.0f);
            ScreenFx.flashAfterHit(0.9f);
        }
        if (t == H_BIRTH) ScreenFx.cutFlash(0.6f);
        if (t == H_LAUNCH) {
            score(ModRegistry.CANNON_FIRE.get(), 0.7f);
            beat(ScreenFx.Hit.LIGHT, 0.5f, 0.5f, 0.62f, 0.22f, 1.0f);
        }
        if (t == H_EARTH) score(ModRegistry.CUT_WHOOSH.get(), 0.7f);
        if (t == H_APPEAR) {
            score(ModRegistry.CUT_STINGER.get(), 1.0f);
            score(ModRegistry.HOLE_INBOUND.get(), 1.0f);
            ScreenFx.cutFlash(0.85f);
        }
        if (t == H_SHOOTER || t == H_GROUND) score(ModRegistry.CUT_STINGER.get(), 0.8f);
    }

    private static void cueSun(int t) {
        if (t == 0) score(ModRegistry.CANNON_CHARGE.get(), 1.15f);
        if (t == 28 || t == 36 || t == 42) score(ModRegistry.REMOTE_BEEP.get(), t == 42 ? 1.3f : 1.0f);
        if (t == S_FIRE) {
            score(ModRegistry.CANNON_FIRE.get(), 0.9f);
            beat(ScreenFx.Hit.LIGHT, 0.5f, 0.5f, 1.0f, 0.6f, 0.2f);
        }
        if (t == S_FIRE + 2) score(ModRegistry.CANNON_BEAM.get(), 0.9f);
        if (t == S_CROSS) score(ModRegistry.CUT_WHOOSH.get(), 1.0f);
        if (t == S_TEAR_HIT) {
            score(ModRegistry.SUN_TEAR.get(), 1.0f);
            beat(ScreenFx.Hit.HEAVY, 0.5f, 0.5f, 1.0f, 0.25f, 0.04f);
        }
        if (t == S_FALL) score(ModRegistry.SUN_INBOUND.get(), 1.1f);
        if (t == S_APPEAR) {
            score(ModRegistry.CUT_STINGER.get(), 1.0f);
            score(ModRegistry.SUN_INBOUND.get(), 0.9f);
            ScreenFx.cutFlash(0.9f);
        }
        if (t == S_SHOOTER || t == S_GROUND) score(ModRegistry.CUT_STINGER.get(), 0.8f);
    }

    private static void cueAngel(int t) {
        BlackHole body = ClientHoles.find(active.holeId);
        if (t == 0) score(ModRegistry.ANGEL_CHOIR.get(), 1.0f);
        if (t == A_APPEAR) {
            score(ModRegistry.CUT_STINGER.get(), 1.0f);
            score(ModRegistry.ANGEL_CHOIR.get(), 0.94f);
            ScreenFx.cutFlash(0.9f);
        }
        if (t == A_LAND && body != null) {
            score(ModRegistry.CUT_STINGER.get(), 0.6f);
            worldBeat(ScreenFx.Hit.LIGHT, body.end(), 1.0f, 0.75f, 0.25f);
        }
        if (t == A_RAISE) score(ModRegistry.ANGEL_SWING.get(), 0.8f);
        if (t == A_RAISED && body != null) {
            worldBeat(ScreenFx.Hit.LIGHT, body.end().add(0, body.rs() * 1.1, 0), 1.0f, 0.5f, 0.1f);
        }
        if (t == A_SWING) score(ModRegistry.ANGEL_SWING.get(), 1.3f);
    }

    // ------------------------------------------------------------------ per frame: camera
    /** Called at the start of every frame, before the world renders. */
    public static void frame(float partial) {
        if (active == null || camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        float t = time(partial);
        pose = switch (kind) {
            case HOLE -> hole(t, partial);
            case SUN -> sun(t, partial);
            case ANGEL -> angel(t, partial);
        };
        if (pose.space()) {
            // The space pass replaces the image; park the camera at the player so the world it hides is local.
            place(mc.player.getEyePosition(), mc.player.getYRot(), mc.player.getXRot());
        } else {
            place(pose.pos(), pose.yaw(), pose.pitch());
        }
    }

    private static void place(Vec3 p, float yaw, float pitch) {
        camera.setPos(p.x, p.y, p.z);
        camera.xo = p.x;
        camera.yo = p.y;
        camera.zo = p.z;
        camera.xOld = p.x;
        camera.yOld = p.y;
        camera.zOld = p.z;
        camera.setYRot(yaw);
        camera.yRotO = yaw;
        camera.setXRot(pitch);
        camera.xRotO = pitch;
    }

    /** smootherstep: zero velocity and acceleration at both ends, so no move ever starts or stops with a jerk. */
    static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    /** ease-out only: for shots that are cut INTO while already moving. */
    static float easeOut(float x) {
        x = Mth.clamp(x, 0f, 1f);
        float u = 1f - x;
        return 1f - u * u * u;
    }

    static float span(float t, int from, int to) {
        return Mth.clamp((t - from) / (float) (to - from), 0f, 1f);
    }

    private static float charge(float t, int fire) {
        float c = Mth.clamp(t / fire, 0f, 1f);
        return c * c;
    }

    private static Vec3 bodyPos(float partial, Vec3 fallback) {
        BlackHole b = ClientHoles.find(active.holeId);
        Minecraft mc = Minecraft.getInstance();
        return b != null ? b.position(mc.level.getGameTime() + partial) : fallback;
    }

    /** A tiny handheld drift, so world shots breathe instead of looking locked off. */
    private static Vec3 handheld(float t, double amount) {
        return new Vec3(Mth.sin(t * 0.071f) * 0.6 + Mth.sin(t * 0.23f) * 0.25,
                Mth.sin(t * 0.053f + 1.3f) * 0.4 + Mth.sin(t * 0.19f) * 0.2,
                Mth.sin(t * 0.061f + 2.1f) * 0.6).scale(amount);
    }

    // ------------------------------------------------------------------ shared space shots
    private static Pose cannonWide(float t, int from, int to, int fire) {
        float u = ease(span(t, from, to));
        Vec3 pos = SpaceFrame.at(-52, 24, 11).lerp(SpaceFrame.at(-44, 20, 9), u);
        return Pose.space(0, 0, pos, SpaceFrame.at(6, 0, 0), -3f + 3f * u, 55f, charge(t, fire), -1f, 0f);
    }

    private static Pose cannonBarrel(float t, int from, int to, int fire) {
        float u = ease(span(t, from, to));
        Vec3 pos = SpaceFrame.at(-6, 8.5, 5.5).lerp(SpaceFrame.at(12, 7.5, 5.0), u);
        return Pose.space(0, 0, pos, SpaceFrame.at(22 + 8 * u, 0, 0), 6f * u, 52f, charge(t, fire), -1f, 0f);
    }

    private static Pose cannonBehind(float t, int from, int to, int fire) {
        float u = ease(span(t, from, to));
        float fireAge = t >= fire ? (t - fire) / 20f : -1f;
        // recoil: the camera is shoved back by the shot and settles
        float kick = t >= fire ? (float) Math.exp(-(t - fire) / 6f) * Mth.sin((t - fire) * 0.9f) * 1.2f : 0f;
        Vec3 pos = SpaceFrame.at(-42 - 4 * u - kick, 9 + u, 7 + u * 0.5);
        return Pose.space(0, 0, pos, SpaceFrame.at(200, 0, 0), kick * 2f, 55f - 7f * u, 1f, fireAge, 0f);
    }

    private static Pose crossing(float t, int from, int to, int k) {
        float u = span(t, from, to);
        // ease in hard (the jump to light speed) and ease out (arrival)
        float p = ease(u);
        return Pose.space(1, k, Vec3.ZERO, new Vec3(0, 0, 1), 0f, 70f, 1f, 1f, p);
    }

    // ------------------------------------------------------------------ TON 618
    private static Pose hole(float t, float partial) {
        Vec3 target = active.target;
        if (t < H_BARREL) return cannonWide(t, H_WIDE, H_BARREL, H_FIRE);
        if (t < H_FIRE_SHOT) return cannonBarrel(t, H_BARREL, H_FIRE_SHOT, H_FIRE);
        if (t < H_CROSS) return cannonBehind(t, H_FIRE_SHOT, H_CROSS, H_FIRE);
        if (t < H_PIERCE) return crossing(t, H_CROSS, H_PIERCE, 0);
        Vec3 bd = SpaceFrame.BEAM_DIR;
        if (t < H_BIRTH) {
            // side-on to the beam: the star run through, convulsing, then collapsing (push in on the collapse)
            float u = ease(span(t, H_PIERCE, H_BIRTH));
            float p = 0.6f * (t - H_PIERCE) / (H_COLLAPSE - H_PIERCE);
            Vec3 sideV = bd.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 pos = sideV.scale(Mth.lerp(u, -7800, -6600)).add(0, 1400, 0).subtract(bd.scale(2500));
            float push = ease(span(t, H_COLLAPSE - 22, H_COLLAPSE));
            float shake = t > H_COLLAPSE - 30 ? Mth.sin(t * 2.7f) * 0.6f * span(t, H_COLLAPSE - 30, H_COLLAPSE) : 0f;
            return Pose.space(4, 0, pos, bd.scale(600), shake, 55f - 17f * push, 1f, 1f, p);
        }
        if (t < H_HOME) {
            // behind the newborn hole as its disk spins up, then it is driven out past us
            float u = ease(span(t, H_BIRTH, H_HOME));
            float p = 0.641f + (1f - 0.641f) * span(t, H_BIRTH, H_HOME);
            Vec3 pos = bd.scale(-Mth.lerp(u, 9000, 6200)).add(0, Mth.lerp(u, 600, 900), 0);
            return Pose.space(4, 0, pos, Vec3.ZERO, Mth.lerp(u, 0f, -4f), Mth.lerp(u, 50f, 44f), 1f, 1f, p);
        }
        if (t < H_EARTH) return crossing(t, H_HOME, H_EARTH, 1);
        if (t < H_APPEAR) {
            float u = ease(span(t, H_EARTH, H_APPEAR));
            Vec3 up = SpaceFrame.TARGET_UP;
            Vec3 sideV = up.cross(new Vec3(1, 0, 0)).normalize();
            Vec3 pos = SpaceFrame.TARGET.add(up.scale(Mth.lerp(u, 3200, 1600))).add(sideV.scale(1400));
            return Pose.space(2, 0, pos, SpaceFrame.TARGET, 0f, 45f, 1f, 1f, span(t, H_EARTH, H_APPEAR + 6));
        }
        Vec3 holePos = bodyPos(partial, target.add(0, 300, 0));
        if (t < H_SHOOTER) {
            // from near the target, looking up as it comes; slow push in
            float u = ease(span(t, H_APPEAR, H_SHOOTER));
            Vec3 pos = aboveGround(target.subtract(travel.scale(90)).add(side.scale(40)).add(0, 18, 0).add(handheld(t, 0.6)), 4);
            return Pose.world(pos, holePos, 0f, 72f - 14f * u);
        }
        if (t < H_GROUND) {
            float u = ease(span(t, H_SHOOTER, H_GROUND));
            Vec3 toTarget = target.subtract(shooterEye).multiply(1, 0, 1);
            Vec3 dir = toTarget.lengthSqr() < 1 ? travel : toTarget.normalize();
            Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 pos = shooterEye.subtract(dir.scale(4.0 - 1.5 * u)).add(right.scale(1.3)).add(0, 0.5, 0).add(handheld(t, 0.15));
            return Pose.world(aboveGround(pos, 1.5), holePos.lerp(target, 0.15), -2f * u, 58f);
        }
        if (t < H_IMPACT) {
            float u = ease(span(t, H_GROUND, H_IMPACT));
            Vec3 pos = aboveGround(target.subtract(travel.scale(42)).add(side.scale(14)).add(0, 3, 0), 3);
            return Pose.world(pos, holePos, 3f * u, 52f - 18f * u);
        }
        float u = ease(span(t, H_IMPACT, H_END));
        Vec3 pos = target.subtract(travel.scale(70 + 80 * u)).subtract(side.scale(30)).add(0, 14 + 90 * u, 0);
        return Pose.world(aboveGround(pos, 8), target, 0f, 62f + 8f * u);
    }

    // ------------------------------------------------------------------ the Sun
    private static Pose sun(float t, float partial) {
        Vec3 target = active.target;
        if (t < S_FIRE_SHOT) return cannonWide(t, S_WIDE, S_FIRE_SHOT, S_FIRE);
        if (t < S_CROSS) return cannonBehind(t, S_FIRE_SHOT, S_CROSS, S_FIRE);
        if (t < S_TEAR) return crossing(t, S_CROSS, S_TEAR, 0);
        if (t < S_FALL) {
            // the Sun, seized: we drift round it as it's wrenched out of place and starts to tear
            float u = ease(span(t, S_TEAR, S_FALL));
            float shake = t > S_TEAR_HIT ? (float) Math.exp(-(t - S_TEAR_HIT) / 10f) * Mth.sin(t * 3.1f) * 2f : 0f;
            Vec3 pos = new Vec3(Mth.lerp(u, 0, 500), 300, Mth.lerp(u, -3400, -3000));
            return Pose.space(3, 1, pos, Vec3.ZERO, shake, 55f - 6f * u, 1f, 1f, span(t, S_TEAR, S_FALL));
        }
        if (t < S_APPEAR) {
            // over the Earth: the Sun swells, the day side starts to cook
            float u = ease(span(t, S_FALL, S_APPEAR));
            Vec3 sunD = SpaceFrame.SUN_DIR;
            Vec3 sideV = sunD.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 pos = SpaceFrame.EARTH_C.add(sideV.subtract(sunD.scale(0.55)).add(0, 0.25, 0).normalize()
                    .scale(Mth.lerp(u, 9000, 7000)));
            return Pose.space(2, 1, pos, SpaceFrame.EARTH_C.add(sunD.scale(2600)), 0f, 62f, 1f, 1f,
                    Mth.lerp(span(t, S_FALL, S_APPEAR), 0.05f, 0.95f));
        }
        Vec3 sunPos = bodyPos(partial, target.add(0, 600, 0));
        if (t < S_SHOOTER) {
            // on the ground: a wide low shot, the sky burning, the Sun coming down behind the land
            float u = ease(span(t, S_APPEAR, S_SHOOTER));
            Vec3 pos = aboveGround(target.subtract(travel.scale(260 - 40 * u)).add(side.scale(80)).add(0, 12, 0).add(handheld(t, 0.8)), 6);
            return Pose.world(pos, sunPos.lerp(target, 0.25), 0f, 74f - 10f * u);
        }
        if (t < S_GROUND) {
            float u = ease(span(t, S_SHOOTER, S_GROUND));
            Vec3 toTarget = target.subtract(shooterEye).multiply(1, 0, 1);
            Vec3 dir = toTarget.lengthSqr() < 1 ? travel : toTarget.normalize();
            Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 pos = shooterEye.subtract(dir.scale(4.0 - 1.5 * u)).add(right.scale(1.3)).add(0, 0.5, 0).add(handheld(t, 0.2));
            return Pose.world(aboveGround(pos, 1.5), sunPos.lerp(target, 0.3), -2f * u, 64f);
        }
        if (t < S_IMPACT) {
            float u = ease(span(t, S_GROUND, S_IMPACT));
            Vec3 pos = aboveGround(target.subtract(travel.scale(190)).add(side.scale(-60)).add(0, 4, 0).add(handheld(t, 1.0 + 2.0 * u)), 4);
            return Pose.world(pos, sunPos, 4f * u, 70f - 16f * u);
        }
        float u = ease(span(t, S_IMPACT, S_END));
        Vec3 pos = target.subtract(travel.scale(260 + 160 * u)).subtract(side.scale(90)).add(0, 40 + 160 * u, 0);
        return Pose.world(aboveGround(pos, 10), target, 0f, 66f + 8f * u);
    }

    // ------------------------------------------------------------------ the Archangel
    private static Pose angel(float t, float partial) {
        Vec3 target = active.target;
        Vec3 up = SpaceFrame.TARGET_UP;
        if (t < A_CLOSE) {
            float u = ease(span(t, A_HEAVENS, A_CLOSE));
            Vec3 sideV = up.cross(new Vec3(1, 0, 0)).normalize();
            Vec3 pos = SpaceFrame.TARGET.add(up.scale(Mth.lerp(u, 2600, 1800))).add(sideV.scale(1700));
            return Pose.space(2, 2, pos, SpaceFrame.TARGET.add(up.scale(900)), 0f, 55f, 0f, -1f,
                    0.5f * span(t, A_HEAVENS, A_CLOSE));
        }
        if (t < A_APPEAR) {
            float u = ease(span(t, A_CLOSE, A_APPEAR));
            Vec3 sideV = up.cross(new Vec3(1, 0, 0)).normalize();
            Vec3 pos = SpaceFrame.TARGET.add(up.scale(Mth.lerp(u, 1300, 640))).add(sideV.scale(Mth.lerp(u, 900, 480)));
            return Pose.space(2, 2, pos, SpaceFrame.TARGET.add(up.scale(Mth.lerp(u, 320, 200))), 0f, 55f - 5f * u, 0f, -1f,
                    0.5f + 0.5f * span(t, A_CLOSE, A_APPEAR));
        }
        BlackHole body = ClientHoles.find(active.holeId);
        double H = body != null ? body.rs() : 190;
        Vec3 f = body != null ? body.facing() : travel;
        Vec3 s = f.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 feet = body != null ? body.end() : target.subtract(f.scale(H * 0.62));
        Vec3 now = bodyPos(partial, feet);
        Vec3 head = now.add(0, H * 0.9, 0);
        if (t < 140) {
            // wide and low, three quarters front: it comes down the pillar of light
            float u = ease(span(t, A_APPEAR, 140));
            Vec3 pos = feet.add(f.scale(H * 1.5)).add(s.scale(H * 0.95)).add(0, H * 0.06, 0).add(handheld(t, 1.0));
            return Pose.world(aboveGround(pos, 3), now.add(0, H * 0.5, 0), 0f, 62f - 4f * u);
        }
        if (t < A_HERO) {
            // tracking down with it as it settles: the camera arrives with it
            float u = easeOut(span(t, 140, A_HERO));
            Vec3 pos = feet.add(f.scale(H * Mth.lerp(u, 0.95, 0.75))).add(s.scale(-H * 0.55))
                    .add(0, H * Mth.lerp(u, 0.5, 0.12), 0).add(handheld(t, 0.8));
            return Pose.world(aboveGround(pos, 3), now.add(0, H * Mth.lerp(u, 0.6, 0.7), 0), 0f, 52f);
        }
        if (t < A_SWING) {
            // worm's-eye, almost under it: the sword goes up and catches fire against the sky
            float u = ease(span(t, A_HERO, A_SWING));
            Vec3 pos = feet.add(f.scale(H * Mth.lerp(u, 0.42, 0.34))).add(s.scale(H * 0.22)).add(handheld(t, 0.3));
            return Pose.world(aboveGround(pos, 2.5), head.add(f.scale(H * 0.05)), Mth.lerp(u, -6f, -9f), 74f - 10f * u);
        }
        if (t < A_SLASH) {
            // cut to profile, wide, to read the whole arc of the swing; fast push in as it falls
            float u = span(t, A_SWING, A_SLASH);
            Vec3 pos = feet.add(s.scale(H * 1.75)).add(f.scale(H * 0.55)).add(0, H * 0.32, 0);
            return Pose.world(aboveGround(pos, 4), feet.add(f.scale(H * 0.5)).add(0, H * 0.42, 0), 0f, 60f - 12f * u * u);
        }
        if (t < A_SLASH + 40) {
            // impact: low behind the blade, looking down the line of the cut as it races away
            float u = easeOut(span(t, A_SLASH, A_SLASH + 40));
            Vec3 pos = target.subtract(f.scale(H * 0.15)).add(s.scale(H * 0.18)).add(0, H * 0.06 + 12 * u, 0)
                    .add(handheld(t, 2.5 * (1f - u)));
            return Pose.world(aboveGround(pos, 3), target.add(f.scale(H * 2.5)), 0f, 68f);
        }
        // crane up and back: the land split in two as far as you can see
        float u = ease(span(t, A_SLASH + 40, A_END));
        Vec3 pos = target.add(s.scale(H * (0.5 + 0.6 * u))).subtract(f.scale(H * (0.3 + 0.5 * u))).add(0, H * (0.4 + 1.2 * u), 0);
        return Pose.world(aboveGround(pos, 8), target.add(f.scale(H * (0.8 + 0.6 * u))), 0f, 66f + 6f * u);
    }

    // ------------------------------------------------------------------ captions
    /** Caption text for the current moment, or null. */
    @Nullable
    public static String[] caption(float partial) {
        if (active == null) return null;
        float t = time(partial);
        return switch (kind) {
            case HOLE -> holeCaption(t);
            case SUN -> sunCaption(t);
            case ANGEL -> angelCaption(t);
        };
    }

    private static String[] holeCaption(float t) {
        if (t < H_BARREL) return new String[]{"SS-618  INTERGALACTIC RAILGUN", "2.4 MILLION LIGHT YEARS FROM THE MILKY WAY"};
        if (t < H_FIRE_SHOT) {
            int pct = (int) (Mth.clamp(t / H_FIRE, 0f, 1f) * 100);
            return new String[]{"ACCELERATOR CHARGE  " + pct + "%", "PAYLOAD: STELLAR COLLAPSE"};
        }
        if (t < H_CROSS) return new String[]{t < H_FIRE ? "FIRING SOLUTION LOCKED" : "FIRE", ""};
        if (t < H_PIERCE) return null;
        if (t < H_COLLAPSE) return new String[]{"STELLAR CORE BREACHED", ""};
        if (t < H_LAUNCH) return new String[]{"TON 618", "40.7 BILLION SOLAR MASSES"};
        if (t < H_APPEAR) return null;
        if (t < H_SHOOTER) return new String[]{"TON 618", "INBOUND"};
        if (t < H_IMPACT) {
            int secs = (int) Math.ceil((H_IMPACT - t) / 20f);
            return new String[]{"IMPACT IN " + secs, ""};
        }
        return null;
    }

    private static String[] sunCaption(float t) {
        if (t < S_FIRE_SHOT) return new String[]{"SS-618  INTERGALACTIC RAILGUN", "TARGET: SOL"};
        if (t < S_CROSS) return new String[]{t < S_FIRE ? "GRAVITIC LANCE ARMED" : "FIRE", ""};
        if (t < S_TEAR) return null;
        if (t < S_FALL) return new String[]{"SOL", "1.989 × 10³⁰ KG  //  SEIZED"};
        if (t < S_APPEAR) return new String[]{"SOL", "TRAJECTORY: EARTH"};
        if (t < S_IMPACT && t > S_GROUND) {
            int secs = (int) Math.ceil((S_IMPACT - t) / 20f);
            return new String[]{"IMPACT IN " + secs, ""};
        }
        return null;
    }

    private static String[] angelCaption(float t) {
        if (t < A_APPEAR) return new String[]{"THE HEAVENS OPEN", ""};
        if (t < A_LAND) return null;
        if (t < A_SWING) return new String[]{"\"BEHOLD\"", ""};
        return null;
    }
}
