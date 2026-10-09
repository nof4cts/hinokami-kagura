package com.sunbreathing.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.item.NichirinBladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * GeckoLib renderer for the blade.
 * Files (all under assets/sunbreathing/):
 *   geo/item/nichirin_blade.geo.json          model
 *   textures/item/nichirin_blade.png           black base texture
 *   textures/item/nichirin_blade_glow.png      emissive crimson->orange mask (blade only)
 *   animations/item/nichirin_blade.animation.json
 */
public class NichirinBladeRenderer extends GeoItemRenderer<NichirinBladeItem> {
    private ItemDisplayContext context = ItemDisplayContext.NONE;

    public NichirinBladeRenderer() {
        super(new DefaultedItemGeoModel<>(SunBreathingMod.id("nichirin_blade")));
        addRenderLayer(new BladeIgnitionLayer(this));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        this.context = transformType;
        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
    }

    /** Who is holding the blade being drawn right now (null for dropped items / item frames). */
    @Nullable
    public LivingEntity currentHolder() {
        LivingEntity holder = RenderContext.current();
        if (holder != null) return holder;
        if (context.firstPerson() || context == ItemDisplayContext.GUI) return Minecraft.getInstance().player;
        return null;
    }
}
