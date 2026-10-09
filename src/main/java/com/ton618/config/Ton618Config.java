package com.ton618.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Client visuals (config/ton618-client.toml) and server rules (world/serverconfig/ton618-server.toml). */
public final class Ton618Config {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        Pair<Server, ForgeConfigSpec> s = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = s.getLeft();
        SERVER_SPEC = s.getRight();
        Pair<Client, ForgeConfigSpec> c = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = c.getLeft();
        CLIENT_SPEC = c.getRight();
    }

    private Ton618Config() {}

    public enum Quality {
        LOW(110), MEDIUM(180), HIGH(280), ULTRA(420);
        /** Max raymarch steps per pixel through the black hole's region. */
        public final int steps;

        Quality(int steps) {
            this.steps = steps;
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.EnumValue<Quality> quality;
        public final ForgeConfigSpec.DoubleValue bloom;
        public final ForgeConfigSpec.DoubleValue aberration;
        public final ForgeConfigSpec.DoubleValue filmGrain;
        public final ForgeConfigSpec.DoubleValue cameraShake;
        public final ForgeConfigSpec.DoubleValue flash;

        Client(ForgeConfigSpec.Builder b) {
            b.push("rendering");
            quality = b.comment("Black hole raymarch quality: LOW, MEDIUM, HIGH or ULTRA (GPU heavy).")
                    .defineEnum("quality", Quality.HIGH);
            bloom = b.comment("Bloom strength.").defineInRange("bloom", 1.0, 0.0, 3.0);
            aberration = b.comment("Chromatic aberration strength.").defineInRange("aberration", 1.0, 0.0, 3.0);
            filmGrain = b.comment("Film grain strength.").defineInRange("filmGrain", 1.0, 0.0, 3.0);
            b.pop();
            b.push("comfort");
            cameraShake = b.comment("Camera shake strength (0 disables).").defineInRange("cameraShake", 1.0, 0.0, 2.0);
            flash = b.comment("Impact flash strength (lower if bright flashes bother you).").defineInRange("flash", 1.0, 0.0, 1.0);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue holeSize;
        public final ForgeConfigSpec.IntValue destructionRadius;
        public final ForgeConfigSpec.IntValue blocksPerTick;
        public final ForgeConfigSpec.IntValue consumeSeconds;
        public final ForgeConfigSpec.IntValue lingerSeconds;
        public final ForgeConfigSpec.BooleanValue destroyBlocks;
        public final ForgeConfigSpec.BooleanValue pullEntities;
        public final ForgeConfigSpec.IntValue lockRange;
        public final ForgeConfigSpec.IntValue cooldownSeconds;
        public final ForgeConfigSpec.IntValue cutsceneRadius;

        Server(ForgeConfigSpec.Builder b) {
            b.push("strike");
            holeSize = b.comment("Event horizon radius of the dropped black hole, in blocks.")
                    .defineInRange("holeSize", 16.0, 2.0, 200.0);
            destructionRadius = b.comment("Radius of the sphere of world the black hole consumes, in blocks.",
                            "Large values take a long time and load the server; 160 is a good balance.")
                    .defineInRange("destructionRadius", 160, 0, 1024);
            blocksPerTick = b.comment("Maximum blocks removed per server tick while consuming (performance budget).")
                    .defineInRange("blocksPerTick", 12000, 100, 500000);
            consumeSeconds = b.comment("Seconds for the consumption sphere to grow to full size.")
                    .defineInRange("consumeSeconds", 40, 1, 3600);
            lingerSeconds = b.comment("Seconds the black hole stays after it finishes consuming, before collapsing.")
                    .defineInRange("lingerSeconds", 12, 0, 3600);
            destroyBlocks = b.comment("Whether blocks are actually removed (false = visual only).").define("destroyBlocks", true);
            pullEntities = b.comment("Whether mobs, items and players are pulled in and destroyed.").define("pullEntities", true);
            lockRange = b.comment("Max targeting range of the remote, in blocks (also limited by the client's view distance).")
                    .defineInRange("lockRange", 640, 16, 4096);
            cooldownSeconds = b.comment("Remote cooldown after a strike.").defineInRange("cooldownSeconds", 30, 0, 36000);
            cutsceneRadius = b.comment("Players within this many blocks of the target or the shooter see the cutscene.")
                    .defineInRange("cutsceneRadius", 512, 0, 100000);
            b.pop();
        }
    }
}
