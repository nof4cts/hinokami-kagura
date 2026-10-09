package com.sunbreathing.breath;

import com.sunbreathing.config.SunConfig;
import com.sunbreathing.item.NichirinBladeItem;
import com.sunbreathing.network.ModNetwork;
import com.sunbreathing.network.S2CConcentrationState;
import com.sunbreathing.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Total Concentration Breathing (server side).
 *
 * Effects owned by the server: speed modifier, breath upkeep (in {@link BreathEvents}),
 * sounds heard by everyone, and the blade "ignite" flare. Everything else (aura, ember trail,
 * blade glow, breathing loop) is client presentation driven by {@link S2CConcentrationState}.
 */
public final class ConcentrationLogic {
    private static final UUID SPEED_ID = UUID.fromString("6c1f7a54-2d3b-4a8e-9b1e-5a3c2f0e9d11");

    private ConcentrationLogic() {}

    public static void toggle(ServerPlayer player) {
        BreathCapability.get(player).ifPresent(data -> {
            if (data.concentration()) {
                set(player, data, false, false);
            } else if (data.breath() >= SunConfig.SERVER.concentrationMinToEnter.get()) {
                set(player, data, true, false);
            } else {
                outOfBreath(player);
            }
        });
    }

    public static void set(ServerPlayer player, BreathData data, boolean on, boolean exhausted) {
        data.setConcentration(on);
        applySpeed(player, on);

        var level = player.serverLevel();
        if (on) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BREATH_INHALE.get(),
                    SoundSource.PLAYERS, 0.9f, 1.0f);
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof NichirinBladeItem blade) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLADE_IGNITE.get(),
                        SoundSource.PLAYERS, 0.8f, 1.0f);
                blade.playIgnite(player, held);
            }
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BREATH_EXHALE.get(),
                    SoundSource.PLAYERS, 0.8f, exhausted ? 0.85f : 1.0f);
            if (exhausted) outOfBreath(player);
        }
        ModNetwork.sendToTrackingAndSelf(player, new S2CConcentrationState(player.getId(), on));
    }

    /** Re-applies (or clears) the transient speed modifier; transient modifiers don't survive relog/respawn. */
    public static void applySpeed(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_ID);
        if (on) {
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Sun Breathing: Total Concentration",
                    SunConfig.SERVER.concentrationSpeedBonus.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    public static void outOfBreath(ServerPlayer player) {
        player.playNotifySound(ModSounds.BREATH_FAIL.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        player.displayClientMessage(Component.translatable("message.sunbreathing.out_of_breath")
                .withStyle(ChatFormatting.RED), true);
    }
}
