package com.shrine.network;

import com.shrine.common.Move;
import com.shrine.server.Techniques;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player pressed a move's key. The server decides everything else. */
public final class C2SCast {
    public final Move move;

    public C2SCast(Move move) {
        this.move = move;
    }

    public C2SCast(FriendlyByteBuf buf) {
        this.move = Move.byId(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(move.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) Techniques.cast(p, move);
        ctx.get().setPacketHandled(true);
    }
}
