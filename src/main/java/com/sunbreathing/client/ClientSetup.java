package com.sunbreathing.client;

import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.client.anim.PlayerAnims;
import com.sunbreathing.client.fx.ModShaders;
import com.sunbreathing.client.hud.BreathHudOverlay;
import com.sunbreathing.client.particle.SunParticles;
import com.sunbreathing.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registration on the MOD event bus. */
@Mod.EventBusSubscriber(modid = SunBreathingMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(PlayerAnims::init);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        Keybinds.register(event);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "breath", BreathHudOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SUN_FLAME.get(), SunParticles.FlameProvider::new);
        event.registerSpriteSet(ModParticles.SUN_EMBER.get(), SunParticles.EmberProvider::new);
        event.registerSpriteSet(ModParticles.SUN_RAY.get(), SunParticles.RayProvider::new);
        event.registerSpriteSet(ModParticles.SUN_FLASH.get(), SunParticles.FlashProvider::new);
        event.registerSpriteSet(ModParticles.SUN_GLOW.get(), SunParticles.GlowProvider::new);
    }

    @SubscribeEvent
    public static void onShaders(RegisterShadersEvent event) {
        ModShaders.register(event);
    }
}
