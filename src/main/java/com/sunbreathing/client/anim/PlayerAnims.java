package com.sunbreathing.client.anim;

import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.client.fx.VfxClock;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.forms.Form;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Body animations via Player Animator.
 *
 * Each player gets one high-priority ModifierLayer ("sunbreathing:forms") holding:
 *   - a SpeedModifier, set to ~0 during hit-stop so the swing visibly freezes on impact, and
 *   - the currently playing form animation, swapped in with a short fade for smooth blending.
 *
 * Animations are GeckoLib-format JSON in assets/sunbreathing/player_animation/, authored in
 * Blockbench; the animation NAME inside the file (e.g. "form_dance") is the lookup key.
 * In first person we show the animated arms and the item using the third-person model, so the
 * swing you see matches what other players see.
 */
public final class PlayerAnims {
    public static final ResourceLocation LAYER_ID = SunBreathingMod.id("forms");
    private static final Map<AbstractClientPlayer, SpeedModifier> SPEED = new WeakHashMap<>();

    private PlayerAnims() {}

    /** Called once from client setup. */
    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, player -> {
            SpeedModifier speed = new SpeedModifier(1f);
            SPEED.put(player, speed);
            return new ModifierLayer<>(null, speed);
        });
    }

    public static ResourceLocation animationFor(Form form) {
        return SunBreathingMod.id("form_" + form.key);
    }

    @SuppressWarnings("unchecked")
    public static void play(AbstractClientPlayer player, Form form) {
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(animationFor(form));
        if (anim == null) {
            SunBreathingMod.LOGGER.warn("Missing player animation {}", animationFor(form));
            return;
        }
        var data = PlayerAnimationAccess.getPlayerAssociatedData(player);
        IAnimation raw = data.get(LAYER_ID);
        if (!(raw instanceof ModifierLayer<?> layerAny)) return;
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) layerAny;

        KeyframeAnimationPlayer playerAnim = new KeyframeAnimationPlayer(anim)
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration()
                        .setShowRightArm(true).setShowLeftArm(true)
                        .setShowRightItem(true).setShowLeftItem(false));

        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), playerAnim, true);
    }

    /** Called every render frame: freeze the local player's swing while the VFX clock is frozen. */
    public static void updateHitStop() {
        AbstractClientPlayer self = Minecraft.getInstance().player;
        if (self == null) return;
        SpeedModifier speed = SPEED.get(self);
        if (speed == null) return;
        boolean freeze = SunConfig.CLIENT.hitStop.get() && VfxClock.isFrozen();
        speed.speed = freeze ? 0.02f : 1f;
    }
}
