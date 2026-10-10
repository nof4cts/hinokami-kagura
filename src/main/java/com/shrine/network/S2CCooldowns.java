package com.shrine.network;

import com.shrine.common.Move;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Each move's cooldown for the HUD: the game tick it's ready again, and the total length. */
public final class S2CCooldowns {
    public final long[] readyAt;
    public final int[] total;

    public S2CCooldowns(long[] readyAt, int[] total) {
        this.readyAt = readyAt;
        this.total = total;
    }

    public S2CCooldowns(FriendlyByteBuf b) {
        int n = Move.values().length;
        readyAt = new long[n];
        total = new int[n];
        for (int i = 0; i < n; i++) {
            readyAt[i] = b.readLong();
            total[i] = b.readVarInt();
        }
    }

    public void encode(FriendlyByteBuf b) {
        for (int i = 0; i < readyAt.length; i++) {
            b.writeLong(readyAt[i]);
            b.writeVarInt(total[i]);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.shrine.client.TechniqueHud.cooldowns(this));
        ctx.get().setPacketHandled(true);
    }
}
