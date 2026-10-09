package com.sunbreathing;

import com.mojang.logging.LogUtils;
import com.sunbreathing.breath.BreathCapability;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.network.ModNetwork;
import com.sunbreathing.registry.ModItems;
import com.sunbreathing.registry.ModParticles;
import com.sunbreathing.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point.
 *
 * Architecture at a glance:
 *  - SERVER is authoritative: breath, cooldowns, hit detection and damage all live in
 *    {@code breath/*} and {@code forms/*}. The client only sends "I pressed a key" packets.
 *  - CLIENT is pure presentation: animations, slash meshes, particles, camera, HUD. It reacts
 *    to S2C packets ({@code network/*}) and never decides whether something hit.
 *  - Client-only classes live under {@code client/*} and are only touched through
 *    Dist-guarded entry points, so a dedicated server never loads them.
 */
@Mod(SunBreathingMod.MODID)
public class SunBreathingMod {
    public static final String MODID = "sunbreathing";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SunBreathingMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);

        modBus.addListener(BreathCapability::register);
        modBus.addListener(ModItems::addToCreativeTabs);

        // SERVER config is synced to clients automatically, so the HUD can read
        // cooldown lengths and breath costs that match the server exactly.
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SunConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SunConfig.CLIENT_SPEC);

        ModNetwork.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
