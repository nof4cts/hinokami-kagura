package com.sunbreathing.item;

import com.sunbreathing.client.render.NichirinBladeRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Nichirin Blade: a regular sword for melee, plus the key that unlocks Sun Breathing forms.
 *
 * Rendering is done by GeckoLib ({@link NichirinBladeRenderer}). The blade is black at rest;
 * an emissive layer fades it to crimson-orange and adds a heat shimmer while its wielder is in
 * Total Concentration. That state is NOT stored on the item; the renderer looks up whoever is
 * holding it, so the same stack looks right in every player's hand without NBT churn.
 */
public class NichirinBladeItem extends SwordItem implements GeoItem {
    public static final String CONTROLLER = "blade";
    public static final String ANIM_IGNITE = "ignite";
    private static final RawAnimation IGNITE = RawAnimation.begin().thenPlay("animation.nichirin_blade.ignite");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NichirinBladeItem(Tier tier, int attackDamage, float attackSpeed, Properties props) {
        super(tier, attackDamage, attackSpeed, props);
        // Lets the server trigger the "ignite" flare on everyone's client.
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /** Server side: play the ignite flare on this specific stack for all nearby clients. */
    public void playIgnite(Player holder, ItemStack stack) {
        if (holder.level() instanceof ServerLevel level) {
            triggerAnim(holder, GeoItem.getOrAssignId(stack, level), CONTROLLER, ANIM_IGNITE);
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private NichirinBladeRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new NichirinBladeRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(ANIM_IGNITE, IGNITE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sunbreathing.nichirin_blade.tooltip").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.sunbreathing.nichirin_blade.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
