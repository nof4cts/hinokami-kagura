package com.sunbreathing.config;

import com.sunbreathing.forms.Form;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * All tunables.
 *
 * SERVER (world/serverconfig/sunbreathing-server.toml): gameplay balance. Forge syncs this
 * file to every client on join, so client-side HUD math always matches the server.
 *
 * CLIENT (config/sunbreathing-client.toml): purely cosmetic / comfort settings.
 */
public final class SunConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Server, ForgeConfigSpec> s = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = s.getLeft();
        SERVER_SPEC = s.getRight();
        Pair<Client, ForgeConfigSpec> c = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = c.getLeft();
        CLIENT_SPEC = c.getRight();
    }

    private SunConfig() {}

    // ------------------------------------------------------------------ server
    public static final class Server {
        public final ForgeConfigSpec.DoubleValue maxBreath;
        public final ForgeConfigSpec.DoubleValue regenStanding;
        public final ForgeConfigSpec.DoubleValue regenWalking;
        public final ForgeConfigSpec.IntValue regenDelayTicks;

        public final ForgeConfigSpec.DoubleValue concentrationUpkeep;
        public final ForgeConfigSpec.DoubleValue concentrationMinToEnter;
        public final ForgeConfigSpec.DoubleValue concentrationSpeedBonus;
        public final ForgeConfigSpec.DoubleValue concentrationCostMultiplier;

        public final ForgeConfigSpec.IntValue fireSeconds;
        public final ForgeConfigSpec.BooleanValue hitPlayers;

        private final FormStats[] forms = new FormStats[Form.COUNT];

        Server(ForgeConfigSpec.Builder b) {
            b.comment("Breath resource").push("breath");
            maxBreath = b.comment("Maximum breath.").defineInRange("maxBreath", 100.0, 10.0, 1000.0);
            regenStanding = b.comment("Breath regenerated per second while standing still.")
                    .defineInRange("regenStanding", 14.0, 0.0, 200.0);
            regenWalking = b.comment("Breath regenerated per second while walking (sprinting regenerates nothing).")
                    .defineInRange("regenWalking", 6.0, 0.0, 200.0);
            regenDelayTicks = b.comment("Ticks after using a form before breath starts regenerating.")
                    .defineInRange("regenDelayTicks", 20, 0, 200);
            b.pop();

            b.comment("Total Concentration Breathing").push("concentration");
            concentrationUpkeep = b.comment("Breath drained per second while Total Concentration is active.")
                    .defineInRange("upkeepPerSecond", 1.5, 0.0, 100.0);
            concentrationMinToEnter = b.comment("Minimum breath required to enter Total Concentration.")
                    .defineInRange("minBreathToEnter", 20.0, 0.0, 1000.0);
            concentrationSpeedBonus = b.comment("Movement speed bonus while active (0.20 = +20%).")
                    .defineInRange("speedBonus", 0.20, 0.0, 2.0);
            concentrationCostMultiplier = b.comment("Form breath costs are multiplied by this while concentrated.")
                    .defineInRange("formCostMultiplier", 0.8, 0.1, 2.0);
            b.pop();

            b.comment("Combat").push("combat");
            fireSeconds = b.comment("Seconds a target burns after a Sun Breathing hit (0 disables).")
                    .defineInRange("fireSeconds", 3, 0, 30);
            hitPlayers = b.comment("Whether forms can damage other players (still respects the server's pvp setting).")
                    .define("hitPlayers", true);
            b.pop();

            b.comment("Per-form balance. Damage is per hit; cooldown is in ticks (20 = 1s).").push("forms");
            for (Form f : Form.values()) {
                forms[f.ordinal()] = new FormStats(b, f);
            }
            b.pop();
        }

        public FormStats form(Form f) {
            return forms[f.ordinal()];
        }
    }

    public static final class FormStats {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.IntValue cooldownTicks;
        public final ForgeConfigSpec.DoubleValue breathCost;

        FormStats(ForgeConfigSpec.Builder b, Form f) {
            b.comment(f.roman() + " Form").push(f.key);
            damage = b.defineInRange("damage", f.defaultDamage, 0.0, 1000.0);
            cooldownTicks = b.defineInRange("cooldownTicks", f.defaultCooldownTicks, 0, 20 * 600);
            breathCost = b.defineInRange("breathCost", f.defaultBreathCost, 0.0, 1000.0);
            b.pop();
        }
    }

    // ------------------------------------------------------------------ client
    public enum ParticleDensity {
        LOW(0.35f), MEDIUM(0.7f), HIGH(1.0f);
        public final float multiplier;

        ParticleDensity(float m) {
            this.multiplier = m;
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.EnumValue<ParticleDensity> particleDensity;
        public final ForgeConfigSpec.BooleanValue glowShader;
        public final ForgeConfigSpec.DoubleValue cameraShake;
        public final ForgeConfigSpec.BooleanValue fovPunch;
        public final ForgeConfigSpec.BooleanValue hitStop;
        public final ForgeConfigSpec.DoubleValue breathingVolume;
        public final ForgeConfigSpec.IntValue hudOffsetX;
        public final ForgeConfigSpec.IntValue hudOffsetY;
        public final ForgeConfigSpec.BooleanValue hudAlwaysVisible;

        Client(ForgeConfigSpec.Builder b) {
            b.push("visuals");
            particleDensity = b.comment("Particle density: LOW, MEDIUM or HIGH.")
                    .defineEnum("particleDensity", ParticleDensity.HIGH);
            glowShader = b.comment("Use the custom glow shader for slash arcs. Disable if a shaderpack breaks them;",
                            "a vanilla-shader fallback is used instead.")
                    .define("glowShader", true);
            b.pop();

            b.push("feel");
            cameraShake = b.comment("Camera shake strength multiplier (0 disables).")
                    .defineInRange("cameraShake", 1.0, 0.0, 2.0);
            fovPunch = b.comment("Brief FOV kick on heavy hits.").define("fovPunch", true);
            hitStop = b.comment("Brief 2-4 frame freeze of your animation and slash on impact.").define("hitStop", true);
            breathingVolume = b.comment("Volume of the Total Concentration breathing loop.")
                    .defineInRange("breathingVolume", 0.55, 0.0, 1.0);
            b.pop();

            b.push("hud");
            hudOffsetX = b.defineInRange("offsetX", 0, -2000, 2000);
            hudOffsetY = b.defineInRange("offsetY", 0, -2000, 2000);
            hudAlwaysVisible = b.comment("Show the breath HUD even when not holding a Nichirin Blade and breath is full.")
                    .define("alwaysVisible", false);
            b.pop();
        }
    }
}
