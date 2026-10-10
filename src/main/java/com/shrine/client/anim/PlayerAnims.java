package com.shrine.client.anim;

import com.shrine.ShrineMod;
import com.shrine.common.Move;
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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Body animation for every move, via Player Animator: hand flicks for the cuts, the palm for Cleave, the
 * slam for Spiderweb, the punch for Black Flash, the bow draw for Open, the hand sign held for the Domain,
 * and the three signs and the sweep for the World-Cutting Slash. Keyframed (Blockbench/GeckoLib JSON in
 * assets/shrine/player_animation/) with eased curves, blended in over a couple of ticks.
 */
public final class PlayerAnims {
    public static final ResourceLocation LAYER_ID = ShrineMod.id("technique");

    private PlayerAnims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, player -> new ModifierLayer<>(null, new SpeedModifier(1f)));
    }

    @SuppressWarnings("unchecked")
    public static void play(AbstractClientPlayer player, Move move) {
        ResourceLocation id = ShrineMod.id("move_" + move.key);
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(id);
        if (anim == null) {
            ShrineMod.LOGGER.warn("Missing player animation {}", id);
            return;
        }
        var data = PlayerAnimationAccess.getPlayerAssociatedData(player);
        IAnimation raw = data.get(LAYER_ID);
        if (!(raw instanceof ModifierLayer<?> layerAny)) return;
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) layerAny;
        KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(anim)
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration()
                        .setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true).setShowLeftItem(false));
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), p, true);
    }
}
