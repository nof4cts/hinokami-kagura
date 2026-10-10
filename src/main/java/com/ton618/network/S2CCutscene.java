package com.ton618.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Start of a strike: plays the cutscene, timed from {@code startTick} (server game time). */
public final class S2CCutscene {
    public final long startTick;
    public final Vec3 target;
    public final Vec3 from;
    public final int holeId;
    public final int shooterId;
    public final int kind;

    public S2CCutscene(long startTick, Vec3 target, Vec3 from, int holeId, int shooterId, int kind) {
        this.kind = kind;
        this.startTick = startTick;
        this.target = target;
        this.from = from;
        this.holeId = holeId;
        this.shooterId = shooterId;
    }

    public S2CCutscene(FriendlyByteBuf buf) {
        this(buf.readLong(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(startTick);
        buf.writeDouble(target.x);
        buf.writeDouble(target.y);
        buf.writeDouble(target.z);
        buf.writeDouble(from.x);
        buf.writeDouble(from.y);
        buf.writeDouble(from.z);
        buf.writeVarInt(holeId);
        buf.writeVarInt(shooterId);
        buf.writeVarInt(kind);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ton618.client.cinematic.Cutscene.start(this));
        ctx.get().setPacketHandled(true);
    }
}
