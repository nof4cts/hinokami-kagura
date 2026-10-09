package com.sunbreathing.network;

import com.sunbreathing.breath.BreathData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Full snapshot of the owning player's breath state. Cooldowns are then counted down locally. */
public final class S2CSyncBreath {
    public final float breath;
    public final float maxBreath;
    public final boolean concentration;
    public final int selected;
    public final int[] cooldowns;

    public S2CSyncBreath(float breath, float maxBreath, boolean concentration, int selected, int[] cooldowns) {
        this.breath = breath;
        this.maxBreath = maxBreath;
        this.concentration = concentration;
        this.selected = selected;
        this.cooldowns = cooldowns;
    }

    public static S2CSyncBreath of(BreathData d, float max) {
        return new S2CSyncBreath(d.breath(), max, d.concentration(), d.selected().ordinal(), d.cooldownsView().clone());
    }

    public S2CSyncBreath(FriendlyByteBuf buf) {
        this.breath = buf.readFloat();
        this.maxBreath = buf.readFloat();
        this.concentration = buf.readBoolean();
        this.selected = buf.readVarInt();
        this.cooldowns = buf.readVarIntArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(breath);
        buf.writeFloat(maxBreath);
        buf.writeBoolean(concentration);
        buf.writeVarInt(selected);
        buf.writeVarIntArray(cooldowns);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.sunbreathing.client.ClientPacketHandler.onSyncBreath(this));
        ctx.get().setPacketHandled(true);
    }
}
