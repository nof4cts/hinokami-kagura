package com.sunbreathing.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.config.SunConfig;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Custom core shaders (assets/sunbreathing/shaders/core/*).
 *
 * slash_glow: renders slash ribbons with an additive, procedurally animated flame edge and a
 * white-hot core, so the arc reads as light rather than a flat sprite. If it fails to compile
 * (some shaderpacks) or is disabled in config, renderers fall back to vanilla position_color.
 */
public final class ModShaders {
    private static ShaderInstance slashGlow;

    private ModShaders() {}

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    SunBreathingMod.id("slash_glow"), DefaultVertexFormat.POSITION_COLOR_TEX), s -> slashGlow = s);
        } catch (Exception e) {
            SunBreathingMod.LOGGER.error("Sun Breathing: slash_glow shader failed to load; using fallback", e);
            slashGlow = null;
        }
    }

    /** The glow shader, or null if unavailable/disabled (callers must handle the fallback). */
    @Nullable
    public static ShaderInstance slashGlow() {
        return SunConfig.CLIENT.glowShader.get() ? slashGlow : null;
    }
}
