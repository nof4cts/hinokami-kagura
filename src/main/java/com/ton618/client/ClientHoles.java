package com.ton618.client;

import com.ton618.common.BlackHole;
import com.ton618.common.StrikeKind;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The client's mirror of the server's black holes, plus everything that turns a hole's timeline into
 * per-frame visuals: which hole to draw, how present it is, how dark the world gets around it, and
 * the moment of impact.
 */
public final class ClientHoles {
    private static List<BlackHole> holes = List.of();
    private static final Set<Integer> IMPACTED = new HashSet<>();
    private static final Set<Integer> SEEN = new HashSet<>();

    private ClientHoles() {}

    public static void replace(List<BlackHole> list) {
        holes = new ArrayList<>(list);
        Set<Integer> ids = new HashSet<>();
        for (BlackHole h : holes) ids.add(h.id());
        IMPACTED.retainAll(ids);
        SEEN.retainAll(ids);
        // Holes that had already landed before we joined shouldn't replay their impact.
        double now = gameTime(0f);
        for (BlackHole h : holes) {
            if (SEEN.add(h.id()) && h.struck(now) && now - h.impactTick() > 40) {
                IMPACTED.add(h.id());
            }
        }
    }

    public static void clear() {
        holes = List.of();
        RUMBLING.clear();
        IMPACTED.clear();
        SEEN.clear();
    }

    public static boolean any() {
        return !holes.isEmpty();
    }

    static double gameTime(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    /** Called each client tick: fires impacts exactly once and keeps each landed hole's rumble going. */
    public static void tick() {
        double now = gameTime(0f);
        for (BlackHole h : holes) {
            if (h.falls() && h.struck(now) && IMPACTED.add(h.id())) {
                switch (h.strikeKind()) {
                    case SUN -> ScreenFx.impact(h.end(), h.rs(), 1.0f, 0.22f, 0.04f);
                    case ANGEL -> ScreenFx.impact(h.end().add(h.facing().scale(h.rs() * 0.62)), h.rs() * 0.3f, 1.0f, 0.72f, 0.2f);
                    default -> ScreenFx.impact(h.end(), h.rs(), 0.62f, 0.22f, 1.0f);
                }
            }
            if (h.strikeKind() != StrikeKind.ANGEL && (h.landed(now) || !h.falls()) && !RUMBLING.contains(h.id())) {
                RUMBLING.add(h.id());
                Minecraft.getInstance().getSoundManager().play(new HoleRumbleSound(h.id(), h.end()));
            }
        }
        RUMBLING.removeIf(id -> find(id) == null);
    }

    private static final Set<Integer> RUMBLING = new HashSet<>();

    @Nullable
    public static BlackHole find(int id) {
        for (BlackHole h : holes) if (h.id() == id) return h;
        return null;
    }

    /** Everything the renderer needs about the hole being drawn this frame. */
    public record Frame(BlackHole hole, Vec3 position, float presence, float eclipse, float jets, float approach) {}

    /** Picks the most important hole for this frame (closest to the camera) and evaluates its timeline. */
    @Nullable
    public static Frame frame(float partial, Vec3 camera) {
        if (holes.isEmpty()) return null;
        double now = gameTime(partial);
        Frame best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlackHole h : holes) {
            Vec3 pos = h.position(now);
            double d = pos.distanceTo(camera);
            if (d < bestDist) {
                bestDist = d;
                best = evaluate(h, pos, now);
                if (h.strikeKind() != StrikeKind.HOLE) {
                    // the Sun's heat and heaven's light belong to the region of the strike, not the whole dimension
                    float far = Mth.clamp(1f - (float) ((d - 2500.0) / 2500.0), 0f, 1f);
                    best = new Frame(best.hole(), best.position(), best.presence() * far, best.eclipse(), best.jets(), best.approach());
                }
            }
        }
        return best;
    }

    private static Frame evaluate(BlackHole h, Vec3 pos, double now) {
        double age = now - h.startTick();
        float fadeIn = Mth.clamp((float) (age / 40.0), 0f, 1f);
        float presence = fadeIn * fadeIn * (3f - 2f * fadeIn) * h.remaining(now);
        float approach = h.falls() ? h.progress(now) : 0f;
        float eclipse;
        if (!h.falls()) {
            eclipse = 0.6f * presence;
        } else if (!h.landed(now)) {
            eclipse = 0.25f + 0.6f * approach * approach;
        } else {
            eclipse = 0.85f;
        }
        float jets = 0.6f + 0.4f * approach;
        return new Frame(h, pos, presence, eclipse * presence, jets, approach);
    }
}
