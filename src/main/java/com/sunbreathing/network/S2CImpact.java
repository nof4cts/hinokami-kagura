package com.sunbreathing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A confirmed server-side hit. Clients spawn impact VFX at the position; the attacker and the
 * victim (if it's the local player) also get hit-stop, camera shake and an FOV punch scaled by damage.
 */
public final class S2CImpact {
    public final double x, y, z;
    public final float damage;
    public final int attackerId;
    public final int targetId;
    public final int form;
    public final float yaw;

    public S2CImpact(double x, double y, double z, float damage, int attackerId, int targetId, int form, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.damage = damage;
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.form = form;
        this.yaw = yaw;
    }

    public S2CImpact(FriendlyByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readFloat());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(damage);
        buf.writeVarInt(attackerId);
        buf.writeVarInt(targetId);
        buf.writeVarInt(form);
        buf.writeFloat(yaw);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.sunbreathing.client.ClientPacketHandler.onImpact(this));
        ctx.get().setPacketHandled(true);
    }
}
