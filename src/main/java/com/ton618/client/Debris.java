package com.ton618.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ton618.network.S2CDebris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The world being swallowed: blocks torn out of the ground by TON 618 don't just vanish, they are ripped up,
 * tumble, and spiral inward, faster and faster, shrinking and stretching as they fall through the disk and
 * into the horizon. Purely visual (the server has already removed them) and animated per frame, so the
 * motion is perfectly smooth at any frame rate.
 */
public final class Debris {
    private static final int MAX = 2600;
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final RandomSource RAND = RandomSource.create();

    private Debris() {}

    private static final class Piece {
        final BlockState state;
        final BlockPos lightPos;
        final Vec3 centre;
        final double r0, theta0, y0, swirl;
        final float life, delay;
        final Vector3f axis;
        final float spin;
        final double born;

        Piece(BlockState state, BlockPos pos, Vec3 centre, double now) {
            this.state = state;
            this.lightPos = pos.above();
            this.centre = centre;
            double dx = pos.getX() + 0.5 - centre.x, dz = pos.getZ() + 0.5 - centre.z;
            this.r0 = Math.sqrt(dx * dx + dz * dz);
            this.theta0 = Math.atan2(dz, dx);
            this.y0 = pos.getY() + 0.5;
            this.swirl = (RAND.nextBoolean() ? 1 : 1) * (2.5 + RAND.nextDouble() * 3.0);
            double dist = Math.sqrt(r0 * r0 + (y0 - centre.y) * (y0 - centre.y));
            this.life = (float) Mth.clamp(1.4 + dist / 45.0 + RAND.nextDouble() * 0.8, 1.6, 6.0);
            this.delay = RAND.nextFloat() * 0.35f;
            this.axis = new Vector3f(RAND.nextFloat() - 0.5f, RAND.nextFloat() - 0.5f, RAND.nextFloat() - 0.5f).normalize();
            this.spin = 2f + RAND.nextFloat() * 5f;
            this.born = now;
        }
    }

    private static double seconds() {
        return System.nanoTime() / 1.0e9;
    }

    public static void add(S2CDebris msg) {
        double now = seconds();
        for (int i = 0; i < msg.positions.size(); i++) {
            BlockState st = msg.states.get(i);
            if (st.getRenderShape() != RenderShape.MODEL) continue;
            if (PIECES.size() >= MAX) PIECES.remove(0);
            PIECES.add(new Piece(st, msg.positions.get(i), msg.centre, now));
        }
    }

    public static void clear() {
        PIECES.clear();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PIECES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        double now = seconds();

        Iterator<Piece> it = PIECES.iterator();
        while (it.hasNext()) {
            Piece p = it.next();
            float age = (float) (now - p.born) - p.delay;
            if (age < 0f) {
                age = 0f;
            }
            float u = age / p.life;
            if (u >= 1f) {
                it.remove();
                continue;
            }
            // torn loose: a short shudder and lift, then the inward spiral accelerates
            float rip = Mth.clamp(age / 0.35f, 0f, 1f);
            float fall = u * u * (3f - 2f * u);
            double r = p.r0 * Math.pow(1.0 - fall, 1.5);
            double theta = p.theta0 + p.swirl * fall * fall * (1.0 + 2.0 / (r + 4.0));
            double lift = Math.sin(Math.PI * Math.min(u * 1.6, 1.0)) * Math.min(p.r0 * 0.18, 14.0) * rip;
            double y = Mth.lerp(fall * fall, p.y0, p.centre.y) + lift;
            double shake = (1f - rip) * 0.08;
            double x = p.centre.x + Math.cos(theta) * r + (RAND.nextDouble() - 0.5) * shake;
            double z = p.centre.z + Math.sin(theta) * r + (RAND.nextDouble() - 0.5) * shake;
            // it shrinks as it falls in (and stretches along the fall: spaghettification)
            float scale = (float) Mth.clamp(Math.pow(1.0 - u, 0.6), 0.05, 1.0);
            float stretch = 1f + 2.5f * u * u;

            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            Vec3 toC = new Vec3(p.centre.x - x, p.centre.y - y, p.centre.z - z);
            if (toC.lengthSqr() > 1e-6 && u > 0.2f) {
                // align the stretch with the direction of fall
                Vector3f d = new Vector3f((float) toC.x, (float) toC.y, (float) toC.z).normalize();
                ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d));
                ps.scale(scale / (float) Math.sqrt(stretch), scale * stretch, scale / (float) Math.sqrt(stretch));
            } else {
                ps.scale(scale, scale, scale);
            }
            ps.mulPose(new Quaternionf().rotationAxis(age * p.spin * (1f + 3f * u), p.axis));
            ps.translate(-0.5, -0.5, -0.5);
            int light = LevelRenderer.getLightColor(mc.level, p.lightPos);
            // the disk's light: brighter as it nears the hole
            int boost = (int) (15 * Mth.clamp(u * 1.6f - 0.2f, 0f, 1f));
            int block = Math.max((light & 0xFFFF) >> 4, boost);
            light = (light & 0xFFFF0000) | (block << 4);
            blocks.renderSingleBlock(p.state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        buffers.endBatch();
    }
}
