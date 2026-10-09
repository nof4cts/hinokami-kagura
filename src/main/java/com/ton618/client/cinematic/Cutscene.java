package com.ton618.client.cinematic;

import com.ton618.client.ClientHoles;
import com.ton618.client.ScreenFx;
import com.ton618.common.BlackHole;
import com.ton618.common.StrikeTiming;
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

/**
 * The strike cutscene director.
 *
 * Shots (ticks from the shot, see {@link StrikeTiming}):
 *   0    WIDE      the cannon over the Earth; coils begin to wake         (space)
 *   38   BARREL    tracking along the barrel as the charge races up it    (space)
 *   70   FIRE      down the line of fire; the shot, the beam, the projectile (space)
 *   110  SKY       white-out cut to the world: from near the target, looking up as it comes
 *   180  SHOOTER   over the shooter's shoulder
 *   262  GROUND    low and close, lens tightening for the last seconds
 *   300  AFTERMATH impact; crane up and away while the world is eaten
 *   380  end, control returns
 *
 * Camera control: a client-only Marker entity becomes the camera entity and is posed every frame
 * (before the frame renders), so movement is perfectly smooth at any frame rate. The player can't
 * move, the hand and HUD are hidden, letterbox bars and captions are drawn instead.
 * Hold SHIFT for one second to skip.
 */
public final class Cutscene {
    private static S2CCutscene active;
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

    @Nullable
    public static Pose pose() {
        return active == null ? null : pose;
    }

    public static void start(S2CCutscene msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        stop();
        active = msg;
        lastCue = -1;
        skipHeld = 0;
        Entity shooter = mc.level.getEntity(msg.shooterId);
        shooterEye = shooter != null ? shooter.getEyePosition() : mc.player.getEyePosition();
        Vec3 flat = msg.target.subtract(msg.from).multiply(1, 0, 1);
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

    // ------------------------------------------------------------------ per tick: sounds, skip
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        int t = (int) time(0f);
        skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0;
        if (t >= StrikeTiming.END || skipHeld >= 20) {
            stop();
            return;
        }
        for (int cue = lastCue + 1; cue <= t; cue++) cue(cue);
        lastCue = t;
    }

    private static void cue(int t) {
        if (t == 0) score(ModRegistry.CANNON_CHARGE.get(), 1.0f);
        if (t == 60 || t == 70 || t == 80) score(ModRegistry.REMOTE_BEEP.get(), t == 80 ? 1.3f : 1.0f);
        if (t == StrikeTiming.FIRE) score(ModRegistry.CANNON_FIRE.get(), 1.0f);
        if (t == StrikeTiming.FIRE + 2) score(ModRegistry.CANNON_BEAM.get(), 1.0f);
        if (t == StrikeTiming.SHOT_SKY) {
            score(ModRegistry.CUT_STINGER.get(), 1.0f);
            score(ModRegistry.HOLE_INBOUND.get(), 1.0f);
            ScreenFx.cutFlash(0.85f);
        }
        if (t == StrikeTiming.SHOT_SHOOTER || t == StrikeTiming.SHOT_GROUND) score(ModRegistry.CUT_STINGER.get(), 0.8f);
    }

    private static void score(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 1.0f));
    }

    // ------------------------------------------------------------------ per frame: camera
    /** Called at the start of every frame, before the world renders. */
    public static void frame(float partial) {
        if (active == null || camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        float t = time(partial);
        pose = compute(t, partial);
        if (pose.space()) {
            // The orbit pass replaces the image; park the camera at the player so the world it hides is local.
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

    private static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static float span(float t, int from, int to) {
        return Mth.clamp((t - from) / (float) (to - from), 0f, 1f);
    }

    private static Pose compute(float t, float partial) {
        Vec3 target = active.target;
        BlackHole hole = ClientHoles.find(active.holeId);
        Minecraft mc = Minecraft.getInstance();
        double now = mc.level.getGameTime() + partial;
        Vec3 holePos = hole != null ? hole.position(now) : target.add(0, 300, 0);

        float charge = Mth.clamp(t / StrikeTiming.FIRE, 0f, 1f);
        charge = charge * charge;
        float fireAge = t >= StrikeTiming.FIRE ? (t - StrikeTiming.FIRE) / 20f : -1f;
        float projT = Mth.clamp((t - StrikeTiming.FIRE - 2) / StrikeTiming.PROJECTILE_TICKS, 0f, 1f);

        if (t < StrikeTiming.SHOT_BARREL) {
            float u = ease(span(t, StrikeTiming.SHOT_WIDE, StrikeTiming.SHOT_BARREL));
            Vec3 pos = SpaceFrame.at(-18, 46, 16).lerp(SpaceFrame.at(-6, 38, 12), u);
            return Pose.space(pos, SpaceFrame.at(2 + 4 * u, 0, 0), -4f + 3f * u, 45f, charge, fireAge, projT);
        }
        if (t < StrikeTiming.SHOT_FIRE) {
            float u = ease(span(t, StrikeTiming.SHOT_BARREL, StrikeTiming.SHOT_FIRE));
            Vec3 pos = SpaceFrame.at(-6, 8.5, 5.5).lerp(SpaceFrame.at(12, 7.5, 5.0), u);
            return Pose.space(pos, SpaceFrame.at(22 + 8 * u, 0, 0), 6f * u, 50f, charge, fireAge, projT);
        }
        if (t < StrikeTiming.SHOT_SKY) {
            float u = ease(span(t, StrikeTiming.SHOT_FIRE, StrikeTiming.SHOT_SKY));
            Vec3 pos = SpaceFrame.at(-30 - 6 * u, 12, 9);
            return Pose.space(pos, SpaceFrame.at(120, 0, 0), 0f, 45f - 6f * u, charge, fireAge, projT);
        }
        if (t < StrikeTiming.SHOT_SHOOTER) {
            float u = ease(span(t, StrikeTiming.SHOT_SKY, StrikeTiming.SHOT_SHOOTER));
            Vec3 pos = aboveGround(target.subtract(travel.scale(90)).add(side.scale(40)).add(0, 18, 0), 4);
            return Pose.world(pos, holePos, 0f, 72f - 12f * u);
        }
        if (t < StrikeTiming.SHOT_GROUND) {
            float u = ease(span(t, StrikeTiming.SHOT_SHOOTER, StrikeTiming.SHOT_GROUND));
            Vec3 toTarget = target.subtract(shooterEye).multiply(1, 0, 1);
            Vec3 dir = toTarget.lengthSqr() < 1 ? travel : toTarget.normalize();
            Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 pos = shooterEye.subtract(dir.scale(4.0 - 1.5 * u)).add(right.scale(1.3)).add(0, 0.5, 0);
            Vec3 look = holePos.lerp(target, 0.15);
            return Pose.world(aboveGround(pos, 1.5), look, -2f * u, 58f);
        }
        if (t < StrikeTiming.SHOT_AFTERMATH) {
            float u = ease(span(t, StrikeTiming.SHOT_GROUND, StrikeTiming.SHOT_AFTERMATH));
            Vec3 pos = aboveGround(target.subtract(travel.scale(42)).add(side.scale(14)).add(0, 3, 0), 3);
            return Pose.world(pos, holePos, 3f * u, 52f - 18f * u);
        }
        float u = ease(span(t, StrikeTiming.SHOT_AFTERMATH, StrikeTiming.END));
        Vec3 pos = target.subtract(travel.scale(70 + 80 * u)).subtract(side.scale(30)).add(0, 14 + 90 * u, 0);
        return Pose.world(aboveGround(pos, 8), target, 0f, 62f + 8f * u);
    }

    /** Caption text for the current moment, or null. */
    @Nullable
    public static String[] caption(float partial) {
        if (active == null) return null;
        float t = time(partial);
        if (t < StrikeTiming.SHOT_BARREL) return new String[]{"SS-618  ORBITAL RAILGUN", "LOW EARTH ORBIT  //  TARGET ACQUIRED"};
        if (t < StrikeTiming.SHOT_FIRE) {
            int pct = (int) (Mth.clamp(t / StrikeTiming.FIRE, 0f, 1f) * 100);
            return new String[]{"ACCELERATOR CHARGE  " + pct + "%", String.format("TARGET  %d  %d  %d",
                    (int) active.target.x, (int) active.target.y, (int) active.target.z)};
        }
        if (t < StrikeTiming.SHOT_SKY) return new String[]{t < StrikeTiming.FIRE ? "FIRING SOLUTION LOCKED" : "PAYLOAD RELEASED", ""};
        if (t < StrikeTiming.SHOT_SHOOTER) return new String[]{"TON 618", "40.7 BILLION SOLAR MASSES  //  INBOUND"};
        if (t < StrikeTiming.IMPACT) {
            int secs = (int) Math.ceil((StrikeTiming.IMPACT - t) / 20f);
            return new String[]{"IMPACT IN " + secs, ""};
        }
        return null;
    }
}
