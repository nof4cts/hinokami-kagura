package com.ton618.registry;

import com.ton618.Ton618Mod;
import com.ton618.item.RemoteItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Ton618Mod.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Ton618Mod.MODID);

    public static final RegistryObject<RemoteItem> REMOTE = ITEMS.register("remote",
            () -> new RemoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // Remote
    public static final RegistryObject<SoundEvent> REMOTE_LOCK = sound("remote.lock");
    public static final RegistryObject<SoundEvent> REMOTE_ARM = sound("remote.arm");
    public static final RegistryObject<SoundEvent> REMOTE_DENY = sound("remote.deny");
    public static final RegistryObject<SoundEvent> REMOTE_BEEP = sound("remote.beep");
    // Cannon
    public static final RegistryObject<SoundEvent> CANNON_CHARGE = sound("cannon.charge");
    public static final RegistryObject<SoundEvent> CANNON_FIRE = sound("cannon.fire");
    public static final RegistryObject<SoundEvent> CANNON_BEAM = sound("cannon.beam");
    // The hole
    public static final RegistryObject<SoundEvent> HOLE_INBOUND = sound("hole.inbound");
    public static final RegistryObject<SoundEvent> HOLE_IMPACT = sound("hole.impact");
    public static final RegistryObject<SoundEvent> HOLE_RUMBLE = sound("hole.rumble");
    public static final RegistryObject<SoundEvent> HOLE_COLLAPSE = sound("hole.collapse");
    public static final RegistryObject<SoundEvent> CUT_STINGER = sound("cutscene.stinger");

    private ModRegistry() {}

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Ton618Mod.id(name)));
    }

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(REMOTE);
        }
    }
}
