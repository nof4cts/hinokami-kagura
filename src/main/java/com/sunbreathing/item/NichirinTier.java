package com.sunbreathing.item;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Between diamond and netherite: sturdy, very enchantable, repaired with blaze powder. */
public final class NichirinTier implements Tier {
    public static final NichirinTier INSTANCE = new NichirinTier();

    private NichirinTier() {}

    @Override
    public int getUses() {
        return 2200;
    }

    @Override
    public float getSpeed() {
        return 8.0f;
    }

    @Override
    public float getAttackDamageBonus() {
        return 3.0f;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLevel() {
        return 3;
    }

    @Override
    public int getEnchantmentValue() {
        return 18;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.BLAZE_POWDER);
    }
}
