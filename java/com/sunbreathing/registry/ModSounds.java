package com.sunbreathing.registry;

import com.sunbreathing.SunBreathingMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events. Each form is built from layers that are played together:
 * swing (blade whoosh) + flame (ignite/crackle) + impact (slash hit) + sub (low thump).
 * The file mapping lives in assets/sunbreathing/sounds.json.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SunBreathingMod.MODID);

    // Form-specific swing layers
    public static final RegistryObject<SoundEvent> DANCE_SWING = register("form.dance.swing");
    public static final RegistryObject<SoundEvent> SWING_HEAVY = register("form.swing.heavy");
    public static final RegistryObject<SoundEvent> SPIN = register("form.spin");
    public static final RegistryObject<SoundEvent> THRUST = register("form.thrust");
    public static final RegistryObject<SoundEvent> DASH = register("form.dash");
    public static final RegistryObject<SoundEvent> RADIANCE = register("form.radiance");
    public static final RegistryObject<SoundEvent> FINALE = register("form.finale");

    // Shared layers
    public static final RegistryObject<SoundEvent> FLAME_IGNITE = register("flame.ignite");
    public static final RegistryObject<SoundEvent> FLAME_CRACKLE = register("flame.crackle");
    public static final RegistryObject<SoundEvent> IMPACT_SLASH = register("impact.slash");
    public static final RegistryObject<SoundEvent> IMPACT_SUB = register("impact.sub");
    public static final RegistryObject<SoundEvent> BLADE_IGNITE = register("blade.ignite");

    // Breathing
    public static final RegistryObject<SoundEvent> BREATH_LOOP = register("breath.loop");
    public static final RegistryObject<SoundEvent> BREATH_INHALE = register("breath.inhale");
    public static final RegistryObject<SoundEvent> BREATH_EXHALE = register("breath.exhale");
    public static final RegistryObject<SoundEvent> BREATH_FAIL = register("breath.fail");

    // UI
    public static final RegistryObject<SoundEvent> UI_OPEN = register("ui.open");
    public static final RegistryObject<SoundEvent> UI_SELECT = register("ui.select");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SunBreathingMod.id(name)));
    }
}
