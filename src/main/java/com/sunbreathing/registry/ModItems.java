package com.sunbreathing.registry;

import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.item.NichirinBladeItem;
import com.sunbreathing.item.NichirinTier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SunBreathingMod.MODID);

    /** Base attack: 3 (tier bonus) + 3 (item) + 1 (hand) = 7 damage, 1.8 attacks/sec. */
    public static final RegistryObject<NichirinBladeItem> NICHIRIN_BLADE = ITEMS.register("nichirin_blade",
            () -> new NichirinBladeItem(NichirinTier.INSTANCE, 3, -2.2f,
                    new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {}

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(NICHIRIN_BLADE);
        }
    }
}
