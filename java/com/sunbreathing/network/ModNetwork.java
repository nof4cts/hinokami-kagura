package com.sunbreathing.network;

import com.sunbreathing.SunBreathingMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network contract.
 *
 * C2S packets carry INTENT only ("use my selected form", "toggle concentration"). The server
 * validates everything (blade held, cooldown, breath) and performs hit detection itself.
 *
 * S2C packets carry PRESENTATION: who cast what and where, where an impact landed and how hard.
 * One small packet per event replaces hundreds of vanilla particle packets.
 */
public final class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SunBreathingMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(C2SUseForm.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SUseForm::encode).decoder(C2SUseForm::new).consumerMainThread(C2SUseForm::handle).add();
        CHANNEL.messageBuilder(C2SSelectForm.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSelectForm::encode).decoder(C2SSelectForm::new).consumerMainThread(C2SSelectForm::handle).add();
        CHANNEL.messageBuilder(C2SToggleConcentration.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SToggleConcentration::encode).decoder(C2SToggleConcentration::new)
                .consumerMainThread(C2SToggleConcentration::handle).add();

        CHANNEL.messageBuilder(S2CSyncBreath.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSyncBreath::encode).decoder(S2CSyncBreath::new).consumerMainThread(S2CSyncBreath::handle).add();
        CHANNEL.messageBuilder(S2CConcentrationState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CConcentrationState::encode).decoder(S2CConcentrationState::new)
                .consumerMainThread(S2CConcentrationState::handle).add();
        CHANNEL.messageBuilder(S2CFormCast.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFormCast::encode).decoder(S2CFormCast::new).consumerMainThread(S2CFormCast::handle).add();
        CHANNEL.messageBuilder(S2CImpact.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CImpact::encode).decoder(S2CImpact::new).consumerMainThread(S2CImpact::handle).add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToTrackingAndSelf(Entity entity, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), msg);
    }

    public static void sendNear(ServerLevel level, Vec3 pos, double radius, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(
                PacketDistributor.TargetPoint.p(pos.x, pos.y, pos.z, radius, level.dimension())), msg);
    }
}
