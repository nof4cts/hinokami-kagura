package com.sunbreathing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "Player X started form N, facing yaw/pitch, from this position."
 * Clients use it to play the body animation, spawn the slash mesh and particles.
 * The yaw/pitch are the ones the SERVER used for hit detection, so visuals match hits.
 */
public final class S2CFormCast {
    public final int entityId;
    public final int form;
    public final double x, y, z;
    public final float yaw, pitch;

    public S2CFormCast(int entityId, int form, double x, double y, double z, float yaw, float pitch) {
        this.entityId = entityId;
        this.form = form;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public S2CFormCast(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(form);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.sunbreathing.client.ClientPacketHandler.onFormCast(this));
        ctx.get().setPacketHandled(true);
    }
}
