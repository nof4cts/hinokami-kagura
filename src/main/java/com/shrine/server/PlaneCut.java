package com.shrine.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A cut through the world along a vertical plane: from the top of the world to the very bottom (bedrock
 * included: the void shows through), {@code thickness} blocks wide, running {@code length} blocks from the
 * start along {@code dir}. Streamed outward column by column within a per-tick budget, so it races away
 * from the caster like the cut itself.
 */
final class PlaneCut {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final Vec3 start;
    private final Vec3 dir;
    private final Vec3 across;
    private final int length;
    private final int thickness;

    private int step;
    private int col;
    private int y = Integer.MIN_VALUE;
    private boolean done;

    PlaneCut(ServerLevel level, Vec3 start, Vec3 dir, int length, int thickness) {
        this.level = level;
        this.start = start;
        this.dir = new Vec3(dir.x, 0, dir.z).normalize();
        this.across = new Vec3(-this.dir.z, 0, this.dir.x);
        this.length = length;
        this.thickness = Math.max(1, thickness);
    }

    boolean done() {
        return done;
    }

    /** Distance along the cut reached so far. */
    int reach() {
        return step;
    }

    void tick(int budget) {
        if (done) return;
        int changed = 0, scanned = 0;
        int minY = level.getMinBuildHeight();
        int top = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (changed < budget && scanned < budget * 6) {
            if (step > length) {
                done = true;
                return;
            }
            if (col >= thickness * 2) {
                // two sub-steps per block along the cut, so diagonal cuts leave no gaps
                step++;
                col = 0;
                continue;
            }
            double a = step + (col % 2) * 0.5;
            double c = (col / 2) - (thickness - 1) * 0.5;
            Vec3 p = start.add(dir.scale(a)).add(across.scale(c));
            int x = Mth.floor(p.x), z = Mth.floor(p.z);
            pos.set(x, 0, z);
            if (!level.isLoaded(pos)) {
                col++;
                y = Integer.MIN_VALUE;
                continue;
            }
            if (y == Integer.MIN_VALUE) y = top;
            while (y >= minY && changed < budget) {
                pos.set(x, y, z);
                scanned++;
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, AIR, FLAGS);
                    changed++;
                }
                y--;
                if (scanned >= budget * 6) break;
            }
            if (y < minY) {
                col++;
                y = Integer.MIN_VALUE;
            }
        }
    }
}
