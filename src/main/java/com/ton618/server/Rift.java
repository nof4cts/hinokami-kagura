package com.ton618.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Archangel's cut: a straight rift through the world, tapering at both ends, from the surface all the way
 * down into the void. It races outward from the point of the blow in both directions at once, one column at
 * a time within a per-tick budget, and its walls are seared into glowing magma near the top.
 */
final class Rift {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();

    private final ServerLevel level;
    private final Vec3 centre;
    private final Vec3 dir;      // horizontal unit vector along the cut
    private final Vec3 across;
    private final int halfLength;
    private final float halfWidth;

    private int step;            // 0, +1, -1, +2, -2 ... (distance along the cut from the centre)
    private int col;             // column across the current step
    private int y = Integer.MIN_VALUE;
    private boolean done;

    Rift(ServerLevel level, Vec3 centre, Vec3 dir, int length, int width) {
        this.level = level;
        this.centre = centre;
        this.dir = dir.normalize();
        this.across = new Vec3(-this.dir.z, 0, this.dir.x);
        this.halfLength = Math.max(1, length / 2);
        this.halfWidth = Math.max(1f, width / 2f);
        this.col = Integer.MIN_VALUE;
    }

    boolean done() {
        return done;
    }

    /** How far along the cut the work has reached (blocks from the centre). */
    int reach() {
        return (step + 1) / 2;
    }

    private int along() {
        int k = (step + 1) / 2;
        return (step % 2 == 1) ? k : -k;
    }

    /** Half width (in blocks) of the cut at distance a from the centre, plus the seared rim. */
    private float widthAt(int a) {
        float t = Math.abs(a) / (float) halfLength;
        return Math.max(0.8f, halfWidth * (float) Math.sqrt(Math.max(0.0, 1.0 - t * t)));
    }

    void tick(int budget) {
        if (done) return;
        int changed = 0, scanned = 0;
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (changed < budget && scanned < budget * 6) {
            int a = along();
            if (Math.abs(a) > halfLength) {
                done = true;
                return;
            }
            float w = widthAt(a);
            int cols = (int) Math.ceil(w) + 1;          // one extra column each side for the seared rim
            if (col == Integer.MIN_VALUE) col = -cols;
            if (col > cols) {
                step++;
                col = Integer.MIN_VALUE;
                continue;
            }
            Vec3 c = centre.add(dir.scale(a)).add(across.scale(col));
            int x = Mth.floor(c.x), z = Mth.floor(c.z);
            pos.set(x, 0, z);
            if (!level.isLoaded(pos)) {
                col++;
                y = Integer.MIN_VALUE;
                continue;
            }
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            if (y == Integer.MIN_VALUE) y = top;
            boolean rim = Math.abs(col) > w;
            while (y >= minY && changed < budget) {
                pos.set(x, y, z);
                scanned++;
                BlockState old = level.getBlockState(pos);
                if (rim) {
                    // sear the walls: the top 10 blocks of rock either side glow, then stop
                    if (top - y > 10) {
                        y = minY - 1;
                        break;
                    }
                    if (!old.isAir() && old.getFluidState().isEmpty()) {
                        level.setBlock(pos, level.random.nextFloat() < 0.65f ? MAGMA : BLACKSTONE, FLAGS);
                        changed++;
                    }
                } else if (!old.isAir()) {
                    level.setBlock(pos, AIR, FLAGS);
                    changed++;
                }
                y--;
            }
            if (y < minY) {
                col++;
                y = Integer.MIN_VALUE;
            }
        }
    }
}
