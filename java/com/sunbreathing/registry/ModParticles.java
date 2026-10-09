package com.sunbreathing.registry;

import com.sunbreathing.SunBreathingMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Particle types. All are spawned client-side only (from VFX packets), so they never
 * travel over the network individually; the server sends one compact packet per event.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SunBreathingMod.MODID);

    /** Licking flame wisp; colour ramps white-hot -> gold -> orange -> deep red over its life. */
    public static final RegistryObject<SimpleParticleType> SUN_FLAME = register("sun_flame");
    /** Tiny bright ember that drifts upward and flickers. */
    public static final RegistryObject<SimpleParticleType> SUN_EMBER = register("sun_ember");
    /** Stretched light streak aligned to its velocity (impact rays, slash sparks). */
    public static final RegistryObject<SimpleParticleType> SUN_RAY = register("sun_ray");
    /** Large, very short-lived radial flash for impacts. */
    public static final RegistryObject<SimpleParticleType> SUN_FLASH = register("sun_flash");
    /** Soft, faint glow puff used for the Total Concentration aura. */
    public static final RegistryObject<SimpleParticleType> SUN_GLOW = register("sun_glow");

    private ModParticles() {}

    private static RegistryObject<SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
