package com.ton618.client;

import com.ton618.client.cinematic.Cutscene;
import com.ton618.item.RemoteItem;
import com.ton618.network.S2CLock;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Vector3f;

/**
 * The remote's targeting HUD: a thin reticle with live range while held; once locked, animated
 * brackets pinned to the target in the world, distance, and the time left to confirm.
 */
public final class RemoteHud implements IGuiOverlay {
    public static final RemoteHud INSTANCE = new RemoteHud();

    private static boolean locked;
    private static Vec3 target = Vec3.ZERO;
    private static long expires;
    private static long lockedAt;

    private RemoteHud() {}

    public static void onLock(S2CLock msg) {
        locked = msg.locked;
        target = msg.target;
        expires = msg.expires;
        Minecraft mc = Minecraft.getInstance();
        lockedAt = mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static final int AMBER = 0xFFFFB347;
    private static final int RED = 0xFFFF4A3A;
    private static final int DIM = 0x88FFD8A0;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || Cutscene.active()) return;
        boolean holding = mc.player.getMainHandItem().getItem() instanceof RemoteItem
                || mc.player.getOffhandItem().getItem() instanceof RemoteItem;
        long now = mc.level.getGameTime();
        if (locked && now >= expires) locked = false;
        if (!holding && !locked) return;
        Font font = mc.font;
        int cx = w / 2, cy = h / 2;
        float time = (now + partial) / 20f;

        if (holding && !locked) {
            // reticle: four ticks and a slowly rotating dotted ring
            int r = 14;
            g.fill(cx - r - 6, cy, cx - r, cy + 1, AMBER);
            g.fill(cx + r, cy, cx + r + 6, cy + 1, AMBER);
            g.fill(cx, cy - r - 6, cx + 1, cy - r, AMBER);
            g.fill(cx, cy + r, cx + 1, cy + r + 6, AMBER);
            for (int i = 0; i < 24; i++) {
                double a = i / 24.0 * Math.PI * 2 + time * 0.6;
                int x = cx + (int) Math.round(Math.cos(a) * r);
                int y = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(x, y, x + 1, y + 1, i % 2 == 0 ? AMBER : DIM);
            }
            double range = Math.min(640, mc.options.getEffectiveRenderDistance() * 16 - 24);
            HitResult hit = mc.player.pick(range, partial, false);
            String txt = hit.getType() == HitResult.Type.MISS ? "NO TARGET"
                    : String.format("RANGE  %d m", (int) mc.player.getEyePosition(partial).distanceTo(hit.getLocation()));
            g.drawString(font, txt, cx + 22, cy + 6, hit.getType() == HitResult.Type.MISS ? RED : AMBER, false);
            g.drawString(font, "USE TO LOCK", cx + 22, cy + 16, DIM, false);
            return;
        }

        // locked: brackets on the target
        Camera cam = mc.gameRenderer.getMainCamera();
        float[] s = project(cam, target, w, h, mc.options.fov().get());
        float left = (expires - now - partial) / 20f;
        float since = (now + partial - lockedAt) / 20f;
        float pulse = 0.5f + 0.5f * Mth.sin(time * 12f);
        int col = left < 1.5f && pulse > 0.5f ? RED : AMBER;
        if (s != null) {
            int bx = (int) s[0], by = (int) s[1];
            int size = (int) (10 + 26 * Math.max(0f, 1f - since * 4f)); // brackets snap in
            int arm = 5;
            bracket(g, bx - size, by - size, arm, 1, 1, col);
            bracket(g, bx + size, by - size, arm, -1, 1, col);
            bracket(g, bx - size, by + size, arm, 1, -1, col);
            bracket(g, bx + size, by + size, arm, -1, -1, col);
            g.fill(bx, by, bx + 1, by + 1, col);
            int dist = (int) mc.player.getEyePosition(partial).distanceTo(target);
            g.drawString(font, "LOCKED  " + dist + " m", bx + size + 6, by - 4, col, false);
        }
        String confirm = String.format("USE AGAIN TO FIRE  %.1fs", Math.max(0f, left));
        g.drawCenteredString(font, confirm, cx, cy + 34, col);
        g.drawCenteredString(font, "SNEAK + USE TO CANCEL", cx, cy + 46, DIM);
    }

    private static void bracket(GuiGraphics g, int x, int y, int arm, int dx, int dy, int col) {
        g.fill(Math.min(x, x + arm * dx), y, Math.max(x, x + arm * dx) + 1, y + 1, col);
        g.fill(x, Math.min(y, y + arm * dy), x + 1, Math.max(y, y + arm * dy) + 1, col);
    }

    /** World position -> GUI pixel coordinates, or null when behind the camera. */
    static float[] project(Camera cam, Vec3 world, int w, int h, double fovDeg) {
        Vec3 rel = world.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), leftV = cam.getLeftVector();
        double z = rel.x * look.x() + rel.y * look.y() + rel.z * look.z();
        if (z <= 0.1) return null;
        double x = -(rel.x * leftV.x() + rel.y * leftV.y() + rel.z * leftV.z());
        double y = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
        double tan = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double aspect = w / (double) h;
        float sx = (float) ((0.5 + x / (z * tan * aspect) * 0.5) * w);
        float sy = (float) ((0.5 - y / (z * tan) * 0.5) * h);
        return new float[]{sx, sy};
    }
}
