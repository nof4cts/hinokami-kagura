package com.ton618.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Eats a sphere of the world, growing outward one spherical SHELL at a time so the hole visibly
 * widens instead of chunks popping. Work is capped per tick (blocks changed and positions scanned)
 * and resumes exactly where it stopped, so a 160-block sphere streams out over a minute without
 * stalling the server.
 *
 * Shell k is generated column by column: for each (dx, dz) with dx^2+dz^2 <= k^2, the y values with
 * (k-1)^2 < dx^2+dy^2+dz^2 <= k^2 form at most two short runs (above and below centre).
 * Blocks are cleared with flags 2|16|32: tell clients, skip neighbour shape updates and drops.
 */
final class Consumption {
    /** Decides what a position becomes. Return null to leave it alone. d = distance from centre in blocks. */
    interface Replacer {
        BlockState replace(BlockPos pos, BlockState old, double d, int dx, int dy, int dz);
    }

    /** Told about every block that was solid and got removed (to fling it visually into the hole). */
    interface Sink {
        void removed(BlockPos pos, BlockState old);
    }

    static final Replacer ERASE = (pos, old, d, dx, dy, dz) -> AIR;

    private Replacer replacer = ERASE;
    private Sink sink;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final BlockPos centre;
    private final int maxRadius;
    private final long startTick;
    private final int growTicks;

    private int shell = 0;      // current shell radius k
    private int dx, dz;         // resume position inside the shell
    private boolean shellStarted;
    private boolean done;

    Consumption(ServerLevel level, Vec3 centre, int radius, long startTick, int growTicks) {
        this.level = level;
        this.centre = BlockPos.containing(centre);
        this.maxRadius = radius;
        this.startTick = startTick;
        this.growTicks = Math.max(1, growTicks);
        this.done = radius <= 0;
    }

    Consumption with(Replacer r, Sink s) {
        this.replacer = r;
        this.sink = s;
        return this;
    }

    boolean done() {
        return done;
    }

    /** Radius the hole has grown to by now (eased: a violent start that slows as it widens). */
    double currentRadius(long now) {
        double t = Math.min(1.0, Math.max(0.0, (now - startTick) / (double) growTicks));
        double eased = 1.0 - Math.pow(1.0 - t, 2.2);
        return Math.max(2.0, maxRadius * eased);
    }

    /** Advance by at most {@code budget} block changes (and ~12x that many scanned positions). */
    void tick(long now, int budget) {
        if (done) return;
        int target = (int) Math.floor(currentRadius(now));
        int changed = 0;
        int scanned = 0;
        int scanBudget = budget * 12;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (shell <= Math.min(target, maxRadius)) {
            int k = shell;
            if (!shellStarted) {
                dx = -k;
                dz = -k;
                shellStarted = true;
            }
            int k2 = k * k;
            int inner2 = (k - 1) * (k - 1);
            while (dx <= k) {
                while (dz <= k) {
                    int rxz = dx * dx + dz * dz;
                    if (rxz <= k2) {
                        int yMax = (int) Math.floor(Math.sqrt(k2 - rxz));
                        int yMin = k == 0 ? 0 : (rxz <= inner2 ? (int) Math.floor(Math.sqrt(inner2 - rxz)) + 1 : 0);
                        for (int dy = yMin; dy <= yMax; dy++) {
                            for (int sgn = 0; sgn < (dy == 0 ? 1 : 2); sgn++) {
                                int y = centre.getY() + (sgn == 0 ? dy : -dy);
                                scanned++;
                                if (y < minY || y > maxY) continue;
                                pos.set(centre.getX() + dx, y, centre.getZ() + dz);
                                if (!level.isLoaded(pos)) continue;
                                BlockState old = level.getBlockState(pos);
                                if (old.isAir() && replacer == ERASE) continue;
                                int ddy = y - centre.getY();
                                BlockState now = replacer.replace(pos, old, Math.sqrt(rxz + ddy * ddy), dx, ddy, dz);
                                if (now == null || now == old) continue;
                                if (sink != null && !old.isAir() && now.isAir()) sink.removed(pos.immutable(), old);
                                level.setBlock(pos, now, FLAGS);
                                changed++;
                            }
                        }
                    }
                    dz++;
                    if (changed >= budget || scanned >= scanBudget) return;
                }
                dz = -k;
                dx++;
            }
            shell++;
            shellStarted = false;
        }
        if (shell > maxRadius) done = true;
    }
}
