package com.ton618.network;

import com.ton618.Ton618Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Ton618Mod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(S2CBlackHoles.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBlackHoles::encode).decoder(S2CBlackHoles::new)
                .consumerMainThread(S2CBlackHoles::handle).add();
        CHANNEL.messageBuilder(S2CLock.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CLock::encode).decoder(S2CLock::new).consumerMainThread(S2CLock::handle).add();
        CHANNEL.messageBuilder(S2CCutscene.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCutscene::encode).decoder(S2CCutscene::new).consumerMainThread(S2CCutscene::handle).add();
    }
}
