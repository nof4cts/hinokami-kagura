package com.ton618.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The remote's target lock (or its removal), for the HUD brackets and countdown. */
public final class S2CLock {
    public final boolean locked;
    public final Vec3 target;
    public final long expires;

    public S2CLock(boolean locked, Vec3 target, long expires) {
        this.locked = locked;
        this.target = target;
        this.expires = expires;
    }

    public static S2CLock cleared() {
        return new S2CLock(false, Vec3.ZERO, 0);
    }

    public S2CLock(FriendlyByteBuf buf) {
        this(buf.readBoolean(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(locked);
        buf.writeDouble(target.x);
        buf.writeDouble(target.y);
        buf.writeDouble(target.z);
        buf.writeLong(expires);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ton618.client.RemoteHud.onLock(this));
        ctx.get().setPacketHandled(true);
    }
}
