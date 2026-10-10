package com.shrine.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class ShrineConfig {
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<Client, ForgeConfigSpec> c = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = c.getLeft();
        CLIENT_SPEC = c.getRight();
        Pair<Server, ForgeConfigSpec> s = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = s.getLeft();
        SERVER_SPEC = s.getRight();
    }

    private ShrineConfig() {}

    public static final class Client {
        public final ForgeConfigSpec.DoubleValue bloom;
        public final ForgeConfigSpec.DoubleValue aberration;
        public final ForgeConfigSpec.DoubleValue filmGrain;
        public final ForgeConfigSpec.DoubleValue cameraShake;
        public final ForgeConfigSpec.DoubleValue flash;
        public final ForgeConfigSpec.BooleanValue impactFrames;
        public final ForgeConfigSpec.IntValue volumeSteps;

        Client(ForgeConfigSpec.Builder b) {
            b.push("rendering");
            bloom = b.comment("Bloom strength.").defineInRange("bloom", 1.0, 0.0, 3.0);
            aberration = b.comment("Chromatic aberration strength.").defineInRange("aberration", 1.0, 0.0, 3.0);
            filmGrain = b.comment("Film grain strength.").defineInRange("filmGrain", 1.0, 0.0, 3.0);
            volumeSteps = b.comment("Raymarch steps for fire volumes (higher = finer flames, slower).")
                    .defineInRange("volumeSteps", 48, 16, 160);
            b.pop();
            b.push("comfort");
            cameraShake = b.comment("Camera shake strength (0 disables).").defineInRange("cameraShake", 1.0, 0.0, 2.0);
            flash = b.comment("Flash strength (lower if bright flashes bother you).").defineInRange("flash", 1.0, 0.0, 1.0);
            impactFrames = b.comment("Impact frames (stark stylised frames on big hits). Contains rapid flashing.")
                    .define("impactFrames", true);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.BooleanValue cutBlocks;
        public final ForgeConfigSpec.DoubleValue cooldownScale;
        public final ForgeConfigSpec.IntValue dismantleLength;
        public final ForgeConfigSpec.IntValue fugaRadius;
        public final ForgeConfigSpec.IntValue fugaRange;
        public final ForgeConfigSpec.IntValue domainRadius;
        public final ForgeConfigSpec.IntValue domainSeconds;
        public final ForgeConfigSpec.IntValue worldSlashLength;
        public final ForgeConfigSpec.IntValue blocksPerTick;
        public final ForgeConfigSpec.IntValue cutsceneRadius;
        public final ForgeConfigSpec.BooleanValue opOnly;

        Server(ForgeConfigSpec.Builder b) {
            b.push("technique");
            damage = b.comment("Damage of a direct hit (default kills anything vanilla in one blow).")
                    .defineInRange("damage", 100000.0, 1.0, 1.0e9);
            cutBlocks = b.comment("Whether the technique actually cuts the world (false = visuals and damage only).")
                    .define("cutBlocks", true);
            cooldownScale = b.comment("Multiplier on every cooldown (0 = no cooldowns).").defineInRange("cooldownScale", 1.0, 0.0, 10.0);
            dismantleLength = b.comment("Reach of a Dismantle slash, in blocks.").defineInRange("dismantleLength", 48, 4, 512);
            fugaRadius = b.comment("Radius of the fire arrow's blast, in blocks.").defineInRange("fugaRadius", 34, 4, 256);
            fugaRange = b.comment("How far the fire arrow flies, in blocks.").defineInRange("fugaRange", 220, 16, 1024);
            domainRadius = b.comment("Radius of the domain, in blocks (everything inside is cut to pieces).")
                    .defineInRange("domainRadius", 110, 8, 512);
            domainSeconds = b.comment("How long the domain lasts once open, in seconds.").defineInRange("domainSeconds", 14, 2, 120);
            worldSlashLength = b.comment("Length of the World-Cutting Slash, in blocks. It cuts from the sky down into the void.")
                    .defineInRange("worldSlashLength", 1000, 16, 8192);
            blocksPerTick = b.comment("Maximum blocks changed per server tick by large cuts (performance budget).")
                    .defineInRange("blocksPerTick", 16000, 100, 500000);
            cutsceneRadius = b.comment("Players within this many blocks of the caster also see the cutscenes.")
                    .defineInRange("cutsceneRadius", 160, 0, 100000);
            opOnly = b.comment("Only operators can use the technique.").define("opOnly", false);
            b.pop();
        }
    }
}
