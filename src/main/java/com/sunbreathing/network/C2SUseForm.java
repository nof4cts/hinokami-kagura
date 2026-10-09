package com.sunbreathing.network;

import com.sunbreathing.forms.FormExecutor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** "I pressed Use Form." The server decides which form (the player's selected one) and whether it's allowed. */
public final class C2SUseForm {
    public C2SUseForm() {}

    public C2SUseForm(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) FormExecutor.tryCast(player);
        ctx.get().setPacketHandled(true);
    }
}
