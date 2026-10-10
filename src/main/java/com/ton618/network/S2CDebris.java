package com.ton618.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Blocks just torn out of the world by a black hole: the clients fling them, spinning, into it. */
public final class S2CDebris {
    public final int holeId;
    public final Vec3 centre;
    public final List<BlockPos> positions;
    public final List<BlockState> states;

    public S2CDebris(int holeId, Vec3 centre, List<BlockPos> positions, List<BlockState> states) {
        this.holeId = holeId;
        this.centre = centre;
        this.positions = new ArrayList<>(positions);
        this.states = new ArrayList<>(states);
    }

    public S2CDebris(FriendlyByteBuf buf) {
        this.holeId = buf.readVarInt();
        this.centre = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int n = buf.readVarInt();
        this.positions = new ArrayList<>(n);
        this.states = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            positions.add(buf.readBlockPos());
            states.add(Block.stateById(buf.readVarInt()));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(holeId);
        buf.writeDouble(centre.x);
        buf.writeDouble(centre.y);
        buf.writeDouble(centre.z);
        buf.writeVarInt(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            buf.writeBlockPos(positions.get(i));
            buf.writeVarInt(Block.getId(states.get(i)));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ton618.client.Debris.add(this));
        ctx.get().setPacketHandled(true);
    }
}
