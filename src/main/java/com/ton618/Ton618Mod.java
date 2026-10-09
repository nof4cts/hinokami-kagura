package com.ton618;

import com.mojang.logging.LogUtils;
import com.ton618.config.Ton618Config;
import com.ton618.network.Net;
import com.ton618.registry.ModRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * TON 618.
 *
 * Architecture:
 *  - server/  owns the black holes (where, how big, when they arrive) and syncs them to clients.
 *  - client/render/  a self-contained post-processing pipeline (own GL programs, HDR targets):
 *      scene copy -> black hole raymarch (lensing + accretion disk + jets) -> bloom -> composite/grade.
 *    Nothing is drawn with Minecraft geometry or particles.
 *  - client/  per-frame animation of each hole (descent, impact) and screen effects (flash, shake,
 *    shockwave, aberration).
 * Later stages add the orbital cannon, the remote, the cutscene director and streamed destruction.
 */
@Mod(Ton618Mod.MODID)
public class Ton618Mod {
    public static final String MODID = "ton618";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ton618Mod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Ton618Config.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Ton618Config.SERVER_SPEC);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.ITEMS.register(modBus);
        ModRegistry.SOUNDS.register(modBus);
        modBus.addListener(ModRegistry::addToCreativeTabs);
        Net.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
