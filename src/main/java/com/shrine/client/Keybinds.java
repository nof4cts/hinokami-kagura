package com.shrine.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.shrine.client.cinematic.Cutscene;
import com.shrine.common.Move;
import com.shrine.network.C2SCast;
import com.shrine.network.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Defaults (all rebindable under Controls > Shrine):
 *   G  Dismantle (tap) / Barrage (hold)     H  Cleave        J  Spiderweb      N  Black Flash
 *   Y  Open (fire arrow)                     U  Domain Expansion               I  World-Cutting Slash
 */
public final class Keybinds {
    private static final String CATEGORY = "key.categories.shrine";

    public static final KeyMapping DISMANTLE = key("dismantle", GLFW.GLFW_KEY_G);
    public static final KeyMapping CLEAVE = key("cleave", GLFW.GLFW_KEY_H);
    public static final KeyMapping SPIDERWEB = key("spiderweb", GLFW.GLFW_KEY_J);
    public static final KeyMapping BLACK_FLASH = key("black_flash", GLFW.GLFW_KEY_N);
    public static final KeyMapping FUGA = key("fuga", GLFW.GLFW_KEY_Y);
    public static final KeyMapping DOMAIN = key("domain", GLFW.GLFW_KEY_U);
    public static final KeyMapping WORLD_SLASH = key("world_slash", GLFW.GLFW_KEY_I);

    private static int dismantleHeld = -1;

    private Keybinds() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.shrine." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : new KeyMapping[]{DISMANTLE, CLEAVE, SPIDERWEB, BLACK_FLASH, FUGA, DOMAIN, WORLD_SLASH}) {
            event.register(k);
        }
    }

    public static KeyMapping keyFor(Move m) {
        return switch (m) {
            case DISMANTLE, BARRAGE -> DISMANTLE;
            case CLEAVE -> CLEAVE;
            case SPIDERWEB -> SPIDERWEB;
            case BLACK_FLASH -> BLACK_FLASH;
            case FUGA -> FUGA;
            case DOMAIN -> DOMAIN;
            case WORLD_SLASH -> WORLD_SLASH;
        };
    }

    private static void send(Move m) {
        Net.CHANNEL.sendToServer(new C2SCast(m));
    }

    /** Polled once per client tick. */
    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Cutscene.active() || mc.screen != null) {
            dismantleHeld = -1;
            return;
        }
        // Dismantle: a tap is one cut; hold it and the cuts rain
        if (DISMANTLE.isDown()) {
            if (dismantleHeld >= 0) dismantleHeld++;
            if (dismantleHeld == 6) send(Move.BARRAGE);
        } else if (dismantleHeld >= 0) {
            if (dismantleHeld < 6) send(Move.DISMANTLE);
            dismantleHeld = -1;
        }
        while (DISMANTLE.consumeClick()) if (dismantleHeld < 0) dismantleHeld = 0;
        while (CLEAVE.consumeClick()) send(Move.CLEAVE);
        while (SPIDERWEB.consumeClick()) send(Move.SPIDERWEB);
        while (BLACK_FLASH.consumeClick()) send(Move.BLACK_FLASH);
        while (FUGA.consumeClick()) send(Move.FUGA);
        while (DOMAIN.consumeClick()) send(Move.DOMAIN);
        while (WORLD_SLASH.consumeClick()) send(Move.WORLD_SLASH);
    }
}
