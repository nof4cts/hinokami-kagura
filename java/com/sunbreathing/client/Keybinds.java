package com.sunbreathing.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sunbreathing.client.hud.FormWheelScreen;
import com.sunbreathing.network.C2SToggleConcentration;
import com.sunbreathing.network.C2SUseForm;
import com.sunbreathing.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Defaults: R = use selected form, V = Total Concentration, hold Z = form wheel. All rebindable. */
public final class Keybinds {
    private static final String CATEGORY = "key.categories.sunbreathing";

    public static final KeyMapping USE_FORM = new KeyMapping("key.sunbreathing.use_form",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping CONCENTRATION = new KeyMapping("key.sunbreathing.concentration",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping FORM_WHEEL = new KeyMapping("key.sunbreathing.form_wheel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    private Keybinds() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(USE_FORM);
        event.register(CONCENTRATION);
        event.register(FORM_WHEEL);
    }

    /** Polled once per client tick. */
    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (USE_FORM.consumeClick()) {
            // Client-side prediction only to avoid spamming packets; the server re-checks everything.
            if (ClientBreathState.cooldowns[ClientBreathState.selected.ordinal()] <= 0) {
                ModNetwork.sendToServer(new C2SUseForm());
            }
        }
        while (CONCENTRATION.consumeClick()) {
            ModNetwork.sendToServer(new C2SToggleConcentration());
        }
        if (FORM_WHEEL.isDown() && mc.screen == null) {
            mc.setScreen(new FormWheelScreen());
        }
    }
}
