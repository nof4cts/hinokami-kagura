package com.sunbreathing.client;

import com.sunbreathing.client.fx.ConcentrationFx;
import com.sunbreathing.client.fx.FormVfx;
import com.sunbreathing.network.S2CConcentrationState;
import com.sunbreathing.network.S2CFormCast;
import com.sunbreathing.network.S2CImpact;
import com.sunbreathing.network.S2CSyncBreath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

/** Client-side packet entry points. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void onSyncBreath(S2CSyncBreath msg) {
        ClientBreathState.apply(msg);
    }

    public static void onConcentrationState(S2CConcentrationState msg) {
        boolean was = ClientConcentration.isActive(msg.entityId);
        ClientConcentration.set(msg.entityId, msg.active);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && msg.entityId == mc.player.getId()) {
            ClientBreathState.concentration = msg.active;
        }
        if (mc.level != null && msg.active && !was) {
            Entity e = mc.level.getEntity(msg.entityId);
            if (e instanceof AbstractClientPlayer p) ConcentrationFx.onStateChanged(p, true);
        }
    }

    public static void onFormCast(S2CFormCast msg) {
        FormVfx.onCast(msg);
    }

    public static void onImpact(S2CImpact msg) {
        FormVfx.onImpact(msg);
    }
}
