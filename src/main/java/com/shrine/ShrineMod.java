package com.shrine;

import com.mojang.logging.LogUtils;
import com.shrine.config.ShrineConfig;
import com.shrine.network.Net;
import com.shrine.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Shrine: a cursed technique of cutting and fire.
 *
 * Moves (keys rebindable): Dismantle (hold for a barrage), Cleave, Spiderweb, Black Flash,
 * Open (the fire arrow, with a cutscene), Domain Expansion (cutscene, then a domain without a barrier that
 * shreds everything in range), and the World-Cutting Slash (cutscene; a cut through space itself).
 *
 * Architecture:
 *  - server/  validates casts, cooldowns and timing, deals the damage and cuts the world (streamed).
 *  - network/ one packet per cast tells every nearby client what happened and when (server game time),
 *    so each client animates the same thing on its own, per frame.
 *  - client/render/  a self-contained HDR post pipeline (own GL programs): every effect is raymarched or
 *    drawn analytically in technique.fsh over the real frame and depth buffer; bloom; a composite pass with
 *    impact frames, god rays, shockwaves. Nothing uses Minecraft particles or block geometry.
 *  - client/cinematic/  the cutscene director (camera, title cards, sound and impact-frame cues).
 */
@Mod(ShrineMod.MODID)
public class ShrineMod {
    public static final String MODID = "shrine";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShrineMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ShrineConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ShrineConfig.SERVER_SPEC);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(modBus);
        Net.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
