package com.shrine.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The domain's work on everything that isn't alive: every column within the radius is cut away down to the
 * caster's footing, nearest columns first, so the leveling spreads outward from the caster in a ring.
 * {@link #allow(double)} lets the caller hold it back to the domain's current, still-spreading radius.
 */
final class Leveler {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final BlockPos centre;
    private final int floorY;
    private final List<int[]> columns = new ArrayList<>();
    private int index;
    private int y = Integer.MIN_VALUE;
    private double allowed;

    Leveler(ServerLevel level, BlockPos centre, int radius, int floorY) {
        this.level = level;
        this.centre = centre;
        this.floorY = floorY;
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2) columns.add(new int[]{dx, dz, dx * dx + dz * dz});
            }
        }
        columns.sort(Comparator.comparingInt(c -> c[2]));
    }

    void allow(double radius) {
        allowed = radius;
    }

    boolean done() {
        return index >= columns.size();
    }

    void tick(int budget) {
        int changed = 0, scanned = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double allowed2 = allowed * allowed;
        while (index < columns.size() && changed < budget && scanned < budget * 6) {
            int[] c = columns.get(index);
            if (c[2] > allowed2) return;
            int x = centre.getX() + c[0], z = centre.getZ() + c[1];
            pos.set(x, floorY, z);
            if (!level.isLoaded(pos)) {
                index++;
                y = Integer.MIN_VALUE;
                continue;
            }
            if (y == Integer.MIN_VALUE) y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            while (y >= floorY && changed < budget) {
                pos.set(x, y, z);
                scanned++;
                BlockState s = level.getBlockState(pos);
                if (!s.isAir()) {
                    level.setBlock(pos, AIR, FLAGS);
                    changed++;
                }
                y--;
            }
            if (y < floorY) {
                index++;
                y = Integer.MIN_VALUE;
            }
        }
    }
}
