package com.shrine.client.cinematic;

import com.shrine.client.Casts;
import com.shrine.common.Move;
import com.shrine.network.S2CCast;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static com.shrine.common.Timing.*;

/**
 * The cutscene director for the three great moves. Every shot is a camera move with smootherstep easing
 * (no move starts or stops with a jerk), cut on the action, with a little handheld breath on wide shots.
 *
 * OPEN ........ close on the open hand as fire pours from it; the title card; profile as the fire becomes a
 *               bow; over the shoulder as the arrow is drawn, tension building; loosed: chase the arrow; then
 *               wide on the pillar of fire, craning up with it.
 * DOMAIN ...... close on the hand sign; the cards; then crane up and back as the domain opens and the world
 *               turns; then low on the ground inside it, the caster standing in the storm of cuts.
 * WORLD-CUTTING SLASH
 *               three hand signs in hard close cuts; behind as the hand comes up; then from inside the plane of
 *               the cut, so it splits the frame down the middle; then high above, the line across the land.
 *
 * Camera control: a client-only Marker entity becomes the camera entity and is posed every frame before the
 * frame renders. Movement is blocked, hand and HUD hidden, letterbox and title cards drawn. Hold SHIFT to skip.
 */
public final class Cutscene {
    private static S2CCast active;
    private static Marker camera;
    private static CameraType savedCamera;
    private static int skipHeld;
    @Nullable
    private static Pose pose;
    private static float groundY;

    private Cutscene() {}

    public static boolean active() {
        return active != null;
    }

    public static boolean isFor(S2CCast m) {
        return active == m;
    }

    @Nullable
    public static S2CCast cast() {
        return active;
    }

    @Nullable
    public static Pose pose() {
        return active == null ? null : pose;
    }

    public static void start(S2CCast msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        stop();
        active = msg;
        skipHeld = 0;
        groundY = (float) ground(msg.point.x, msg.point.z, msg.point.y);
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

    private static Vec3 aboveGround(Vec3 p, double clearance) {
        double g = ground(p.x, p.z, groundY);
        return p.y < g + clearance ? new Vec3(p.x, g + clearance, p.z) : p;
    }

    public static float time(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || active == null ? 0f : (float) (mc.level.getGameTime() + partial - active.startTick);
    }

    private static int end() {
        return switch (active.move) {
            case FUGA -> FUGA_END;
            case DOMAIN -> DOMAIN_CUT_END;
            case WORLD_SLASH -> WS_END;
            default -> 1;
        };
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (active == null) return;
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0;
        if (time(0f) >= end() || skipHeld >= 20) stop();
    }

    public static void frame(float partial) {
        if (active == null || camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        float t = time(partial);
        pose = switch (active.move) {
            case FUGA -> fuga(t, partial);
            case DOMAIN -> domain(t, partial);
            default -> worldSlash(t, partial);
        };
        Vec3 p = pose.pos();
        camera.setPos(p.x, p.y, p.z);
        camera.xo = p.x;
        camera.yo = p.y;
        camera.zo = p.z;
        camera.xOld = p.x;
        camera.yOld = p.y;
        camera.zOld = p.z;
        camera.setYRot(pose.yaw());
        camera.yRotO = pose.yaw();
        camera.setXRot(pose.pitch());
        camera.xRotO = pose.pitch();
    }

    static float ease(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    static float easeOut(float x) {
        x = Mth.clamp(x, 0f, 1f);
        float u = 1f - x;
        return 1f - u * u * u;
    }

    static float span(float t, int a, int b) {
        return Mth.clamp((t - a) / (float) (b - a), 0f, 1f);
    }

    private static Vec3 handheld(float t, double amount) {
        return new Vec3(Mth.sin(t * 0.071f) * 0.6 + Mth.sin(t * 0.23f) * 0.25,
                Mth.sin(t * 0.053f + 1.3f) * 0.4 + Mth.sin(t * 0.19f) * 0.2,
                Mth.sin(t * 0.061f + 2.1f) * 0.6).scale(amount);
    }

    private static Entity caster() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getEntity(active.casterId);
    }

    private static Vec3[] basis(Vec3 d) {
        Vec3 r = d.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1e-6) r = new Vec3(1, 0, 0);
        r = r.normalize();
        return new Vec3[]{d.normalize(), r, r.cross(d).normalize()};
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    // ------------------------------------------------------------------ Open
    private static Pose fuga(float t, float partial) {
        Entity c = caster();
        Vec3 E = c != null ? c.getEyePosition(partial) : active.origin;
        Vec3 aim = c != null ? c.getViewVector(partial) : active.dir;
        Vec3[] b = basis(aim);
        Vec3 D = b[0], R = b[1], U = b[2];
        if (t < FUGA_BOW) {
            float u = ease(span(t, 0, FUGA_BOW));
            Vec3 hand = Casts.fugaHand(c, active, partial);
            Vec3 pos = E.add(D.scale(1.25)).add(R.scale(0.95 - 0.15 * u)).subtract(U.scale(0.15));
            return Pose.look(pos, hand, -4f + 2f * u, 42f - 7f * u);
        }
        if (t < FUGA_DRAW) {
            float u = ease(span(t, FUGA_BOW, FUGA_DRAW));
            Vec3 pos = E.add(R.scale(3.0 - 0.4 * u)).add(D.scale(0.9 - 0.7 * u)).add(U.scale(0.15));
            return Pose.look(pos, E.add(D.scale(0.9)).subtract(U.scale(0.12)), 0f, 48f);
        }
        if (t < FUGA_RELEASE) {
            float u = ease(span(t, FUGA_DRAW, FUGA_RELEASE));
            float tense = span(t, FUGA_RELEASE - 18, FUGA_RELEASE);
            Vec3 shake = new Vec3(Mth.sin(t * 2.3f), Mth.sin(t * 3.1f + 1f), 0).scale(0.012 * tense);
            Vec3 pos = E.subtract(D.scale(2.2 - 0.7 * u)).subtract(R.scale(0.75)).add(U.scale(0.25)).add(shake);
            return Pose.look(pos, E.add(D.scale(25)), 3f * u, 54f - 10f * u);
        }
        Vec3 target = active.point;
        Vec3 from = Casts.fugaBow(c, active, partial);
        Vec3 path = target.subtract(from);
        Vec3 D2 = path.lengthSqr() < 1e-4 ? D : path.normalize();
        Vec3[] b2 = basis(D2);
        if (t < FUGA_IMPACT) {
            float f = (t - FUGA_RELEASE) / (float) FUGA_FLIGHT;
            f = f * f * (0.4f + 0.6f * f);
            Vec3 head = from.add(path.scale(Mth.clamp(f, 0f, 1f)));
            Vec3 pos = head.subtract(D2.scale(7)).add(b2[2].scale(1.6)).add(b2[1].scale(1.2));
            return Pose.look(pos, head.add(D2.scale(12)), 0f, 80f);
        }
        float u = ease(span(t, FUGA_IMPACT, FUGA_END));
        double Rb = active.size;
        Vec3 flatD = new Vec3(D2.x, 0, D2.z);
        flatD = flatD.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flatD.normalize();
        Vec3 flatR = flatD.cross(UP).normalize();
        Vec3 pos = target.subtract(flatD.scale(Rb * 3.3)).add(flatR.scale(Rb * 1.3)).add(0, Rb * (0.35 + 0.5 * u), 0)
                .add(handheld(t, 0.6 + 2.5 * (1f - u)));
        return Pose.look(aboveGround(pos, 4), target.add(0, Rb * (1.1 + 0.9 * u), 0), 0f, 72f);
    }

    // ------------------------------------------------------------------ Domain Expansion
    private static Pose domain(float t, float partial) {
        Entity c = caster();
        Vec3 E = c != null ? c.getEyePosition(partial) : active.origin;
        Vec3 aim = c != null ? c.getViewVector(partial) : active.dir;
        Vec3 flat = new Vec3(aim.x, 0, aim.z);
        Vec3 D = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 R = D.cross(UP).normalize();
        Vec3 P = active.point;
        double Rd = active.size;
        if (t < DOMAIN_CARD) {
            float u = ease(span(t, 0, DOMAIN_CARD));
            Vec3 pos = E.add(D.scale(1.35 - 0.2 * u)).subtract(UP.scale(0.2)).add(R.scale(0.15));
            return Pose.look(pos, E.subtract(UP.scale(0.42)), 0f, 40f - 6f * u);
        }
        if (t < DOMAIN_OPEN) {
            Vec3 pos = E.add(D.scale(0.9));
            return Pose.look(pos, E, 0f, 30f);
        }
        if (t < DOMAIN_OPEN + 58) {
            float u = ease(span(t, DOMAIN_OPEN, DOMAIN_OPEN + 58));
            Vec3 a = E.subtract(D.scale(3)).add(UP.scale(1.2));
            Vec3 bpos = P.subtract(D.scale(Rd * 0.35)).add(UP.scale(Rd * 0.3)).add(R.scale(Rd * 0.1));
            Vec3 look = E.lerp(P.add(D.scale(Rd * 0.3)), u);
            return Pose.look(aboveGround(a.lerp(bpos, u), 2), look, -3f * u, 60f + 18f * u);
        }
        float u = ease(span(t, DOMAIN_OPEN + 58, DOMAIN_CUT_END));
        Vec3 pos = P.add(R.scale(14 - 3 * u)).add(D.scale(18 - 4 * u)).add(0, 1.2, 0).add(handheld(t, 0.35));
        return Pose.look(aboveGround(pos, 1.2), P.add(0, 1.6, 0), 0f, 55f - 6f * u);
    }

    // ------------------------------------------------------------------ World-Cutting Slash
    private static Pose worldSlash(float t, float partial) {
        Entity c = caster();
        Vec3 E = c != null ? c.getEyePosition(partial) : active.origin;
        Vec3 D = active.dir;
        Vec3 R = D.cross(UP).normalize();
        Vec3 U = UP;
        Vec3 P = active.point;
        if (t < WS_SIGN2) {
            float u = ease(span(t, WS_SIGN1, WS_SIGN2));
            return Pose.look(E.add(D.scale(1.3 - 0.15 * u)).subtract(U.scale(0.3)).add(R.scale(0.35)), E.subtract(U.scale(0.45)), 5f, 36f);
        }
        if (t < WS_SIGN3) {
            float u = ease(span(t, WS_SIGN2, WS_SIGN3));
            return Pose.look(E.add(D.scale(1.1 - 0.1 * u)).subtract(R.scale(0.8)).subtract(U.scale(0.05)), E.subtract(U.scale(0.35)), -7f, 34f);
        }
        if (t < WS_AIM) {
            float u = ease(span(t, WS_SIGN3, WS_AIM));
            return Pose.look(E.add(D.scale(1.7)).subtract(U.scale(1.1 - 0.2 * u)).add(R.scale(0.2)), E.add(U.scale(0.1)), 3f, 44f - 4f * u);
        }
        if (t < WS_SLASH) {
            float u = ease(span(t, WS_AIM, WS_SLASH));
            Vec3 pos = E.subtract(D.scale(3.2 - 1.0 * u)).add(U.scale(1.0)).add(R.scale(0.5));
            return Pose.look(pos, E.add(D.scale(60)).add(0, 4, 0), 0f, 62f - 6f * u);
        }
        if (t < 140) {
            // from inside the plane of the cut: it splits the frame down the middle
            float u = easeOut(span(t, WS_SLASH, 140));
            Vec3 pos = P.subtract(D.scale(8 + 4 * u)).add(0, 3 + 2.5 * u, 0).add(R.scale(0.02));
            float kick = t < WS_SLASH + 10 ? (float) Math.exp(-(t - WS_SLASH) / 3.0) * Mth.sin((t - WS_SLASH) * 2.2f) * 2f : 0f;
            return Pose.look(aboveGround(pos, 2), P.add(D.scale(220)).add(0, 12, 0), kick, 76f);
        }
        float u = ease(span(t, 140, WS_END));
        double L = Math.min(active.size, 420);
        Vec3 pos = P.add(D.scale(L * 0.06)).add(R.scale(L * 0.18)).add(0, L * 0.09 + 30 * u, 0);
        return Pose.look(aboveGround(pos, 10), P.add(D.scale(L * (0.35 + 0.15 * u))), 0f, 72f);
    }

    /** Title cards for the overlay: {big, small, style} or null. style: 0 lower third, 1 full black card. */
    @Nullable
    public static Object[] card(float partial) {
        if (active == null) return null;
        float t = time(partial);
        Move m = active.move;
        if (m == Move.FUGA && t >= FUGA_CARD && t < FUGA_BOW + 6) return new Object[]{"■ 開", "OPEN", 0, t - FUGA_CARD};
        if (m == Move.DOMAIN) {
            if (t >= DOMAIN_CARD && t < DOMAIN_NAME) return new Object[]{"領域展開", "DOMAIN EXPANSION", 1, t - DOMAIN_CARD};
            if (t >= DOMAIN_NAME && t < DOMAIN_OPEN) return new Object[]{"伏魔御厨子", "MALEVOLENT SHRINE", 1, t - DOMAIN_NAME};
        }
        if (m == Move.WORLD_SLASH && t >= WS_AIM && t < WS_SLASH) return new Object[]{"世界を断つ斬撃", "WORLD-CUTTING SLASH", 0, t - WS_AIM};
        return null;
    }
}
