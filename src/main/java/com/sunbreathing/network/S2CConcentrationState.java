package com.sunbreathing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells clients that a player entered/left Total Concentration (drives aura + blade glow). */
public final class S2CConcentrationState {
    public final int entityId;
    public final boolean active;

    public S2CConcentrationState(int entityId, boolean active) {
        this.entityId = entityId;
        this.active = active;
    }

    public S2CConcentrationState(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.active = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(active);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.sunbreathing.client.ClientPacketHandler.onConcentrationState(this));
        ctx.get().setPacketHandled(true);
    }
}
