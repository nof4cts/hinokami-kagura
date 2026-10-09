package com.sunbreathing.network;

import com.sunbreathing.breath.BreathCapability;
import com.sunbreathing.forms.Form;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Selection from the form wheel. */
public final class C2SSelectForm {
    private final int form;

    public C2SSelectForm(Form form) {
        this.form = form.ordinal();
    }

    public C2SSelectForm(FriendlyByteBuf buf) {
        this.form = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(form);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && form >= 0 && form < Form.COUNT) {
            BreathCapability.get(player).ifPresent(d -> d.setSelected(Form.byIndex(form)));
        }
        ctx.get().setPacketHandled(true);
    }
}
