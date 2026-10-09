package com.sunbreathing.network;

import com.sunbreathing.breath.ConcentrationLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class C2SToggleConcentration {
    public C2SToggleConcentration() {}

    public C2SToggleConcentration(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.isAlive()) ConcentrationLogic.toggle(player);
        ctx.get().setPacketHandled(true);
    }
}
