package com.sunbreathing.forms;

import com.sunbreathing.breath.BreathCapability;
import com.sunbreathing.breath.BreathData;
import com.sunbreathing.breath.ConcentrationLogic;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.item.NichirinBladeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side gatekeeper for every form cast. All rules live here so the client can never
 * bypass a cooldown or breath cost; the client only predicts them for HUD feedback.
 */
public final class FormExecutor {
    private FormExecutor() {}

    public static void tryCast(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        BreathCapability.get(player).ifPresent(data -> tryCast(player, data));
    }

    private static void tryCast(ServerPlayer player, BreathData data) {
        Form form = data.selected();

        if (!(player.getMainHandItem().getItem() instanceof NichirinBladeItem)) {
            actionBar(player, Component.translatable("message.sunbreathing.need_blade"));
            return;
        }
        if (!form.implemented) {
            actionBar(player, Component.translatable("message.sunbreathing.form_locked",
                    Component.translatable(form.langKey())));
            return;
        }
        if (data.castLock() > 0 || data.cooldown(form) > 0) return; // silently ignore spam

        SunConfig.FormStats stats = SunConfig.SERVER.form(form);
        float cost = stats.breathCost.get().floatValue();
        if (data.concentration()) cost *= SunConfig.SERVER.concentrationCostMultiplier.get().floatValue();
        if (!data.consume(cost)) {
            ConcentrationLogic.outOfBreath(player);
            return;
        }

        data.setCooldown(form, stats.cooldownTicks.get());
        data.setCastLock(form.castLockTicks);
        data.setRegenDelay(SunConfig.SERVER.regenDelayTicks.get());

        ServerForms.cast(form, player, data, stats);
    }

    private static void actionBar(ServerPlayer player, Component msg) {
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GRAY), true);
    }
}
