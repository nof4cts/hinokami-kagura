package com.shrine.network;

import com.shrine.ShrineMod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ShrineMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(C2SCast.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCast::encode).decoder(C2SCast::new).consumerMainThread(C2SCast::handle).add();
        CHANNEL.messageBuilder(S2CCast.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCast::encode).decoder(S2CCast::new).consumerMainThread(S2CCast::handle).add();
        CHANNEL.messageBuilder(S2CCooldowns.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCooldowns::encode).decoder(S2CCooldowns::new).consumerMainThread(S2CCooldowns::handle).add();
    }
}
