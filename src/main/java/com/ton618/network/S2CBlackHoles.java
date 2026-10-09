package com.ton618.network;

import com.ton618.common.BlackHole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Full list of black holes in the player's dimension. Small and rare, so always a full replace. */
public final class S2CBlackHoles {
    public final List<BlackHole> holes;

    public S2CBlackHoles(List<BlackHole> holes) {
        this.holes = List.copyOf(holes);
    }

    public S2CBlackHoles(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<BlackHole> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(BlackHole.read(buf));
        this.holes = list;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(holes.size());
        for (BlackHole h : holes) h.write(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ton618.client.ClientHoles.replace(holes));
        ctx.get().setPacketHandled(true);
    }
}
