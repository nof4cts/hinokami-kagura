package com.shrine.registry;

import com.shrine.ShrineMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every sound is synthesised from scratch by tools/gen_sounds.py. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ShrineMod.MODID);

    public static final RegistryObject<SoundEvent> DISMANTLE = sound("dismantle");
    public static final RegistryObject<SoundEvent> BARRAGE = sound("barrage");
    public static final RegistryObject<SoundEvent> CLEAVE = sound("cleave");
    public static final RegistryObject<SoundEvent> SPIDERWEB = sound("spiderweb");
    public static final RegistryObject<SoundEvent> BLACK_FLASH = sound("black_flash");
    public static final RegistryObject<SoundEvent> FUGA_IGNITE = sound("fuga.ignite");
    public static final RegistryObject<SoundEvent> FUGA_DRAW = sound("fuga.draw");
    public static final RegistryObject<SoundEvent> FUGA_RELEASE = sound("fuga.release");
    public static final RegistryObject<SoundEvent> FUGA_IMPACT = sound("fuga.impact");
    public static final RegistryObject<SoundEvent> DOMAIN_SIGN = sound("domain.sign");
    public static final RegistryObject<SoundEvent> DOMAIN_OPEN = sound("domain.open");
    public static final RegistryObject<SoundEvent> DOMAIN_SHRED = sound("domain.shred");
    public static final RegistryObject<SoundEvent> WORLD_SIGN = sound("world.sign");
    public static final RegistryObject<SoundEvent> WORLD_SLASH = sound("world.slash");
    public static final RegistryObject<SoundEvent> WORLD_SPLIT = sound("world.split");
    public static final RegistryObject<SoundEvent> IMPACT = sound("impact");
    public static final RegistryObject<SoundEvent> CARD = sound("card");
    public static final RegistryObject<SoundEvent> DENY = sound("deny");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ShrineMod.id(name)));
    }
}
