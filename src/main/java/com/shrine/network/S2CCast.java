package com.shrine.network;

import com.shrine.common.Move;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A cast, as every nearby client needs it: what, by whom, when (server game time), from where, which way,
 * the point it acts on (target, impact point or centre), a seed for the shape of every stroke, a size,
 * the target entity (or -1), and how far from the caster the cutscene reaches.
 */
public final class S2CCast {
    public final Move move;
    public final int casterId;
    public final long startTick;
    public final Vec3 origin;
    public final Vec3 dir;
    public final Vec3 point;
    public final long seed;
    public final float size;
    public final int targetId;
    public final float cutsceneRadius;

    public S2CCast(Move move, int casterId, long startTick, Vec3 origin, Vec3 dir, Vec3 point, long seed, float size,
                   int targetId, float cutsceneRadius) {
        this.move = move;
        this.casterId = casterId;
        this.startTick = startTick;
        this.origin = origin;
        this.dir = dir;
        this.point = point;
        this.seed = seed;
        this.size = size;
        this.targetId = targetId;
        this.cutsceneRadius = cutsceneRadius;
    }

    private static Vec3 vec(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    private static void vec(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    public S2CCast(FriendlyByteBuf b) {
        this(Move.byId(b.readVarInt()), b.readVarInt(), b.readLong(), vec(b), vec(b), vec(b), b.readLong(), b.readFloat(),
                b.readVarInt(), b.readFloat());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(move.ordinal());
        b.writeVarInt(casterId);
        b.writeLong(startTick);
        vec(b, origin);
        vec(b, dir);
        vec(b, point);
        b.writeLong(seed);
        b.writeFloat(size);
        b.writeVarInt(targetId);
        b.writeFloat(cutsceneRadius);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.shrine.client.Casts.add(this));
        ctx.get().setPacketHandled(true);
    }
}
