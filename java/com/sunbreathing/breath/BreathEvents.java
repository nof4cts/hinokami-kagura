package com.sunbreathing.breath;

import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.network.ModNetwork;
import com.sunbreathing.network.S2CConcentrationState;
import com.sunbreathing.network.S2CSyncBreath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side lifecycle of {@link BreathData}: attach, persist across death/dimension,
 * regenerate, drain Total Concentration, and sync to the owning client.
 */
@Mod.EventBusSubscriber(modid = SunBreathingMod.MODID)
public final class BreathEvents {
    private BreathEvents() {}

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            BreathCapability.Provider provider = new BreathCapability.Provider();
            event.addCapability(BreathCapability.KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        BreathCapability.get(original).ifPresent(old ->
                BreathCapability.get(event.getEntity()).ifPresent(fresh -> fresh.copyFrom(old, event.isWasDeath())));
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) resync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) resync(sp);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) resync(sp);
    }

    /** When a client starts seeing another player, tell it whether that player is concentrating. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer watcher) {
            BreathCapability.get(target).ifPresent(data -> {
                if (data.concentration()) {
                    ModNetwork.sendTo(watcher, new S2CConcentrationState(target.getId(), true));
                }
            });
        }
    }

    private static void resync(ServerPlayer player) {
        BreathCapability.get(player).ifPresent(data -> {
            ConcentrationLogic.applySpeed(player, data.concentration());
            ModNetwork.sendToTrackingAndSelf(player, new S2CConcentrationState(player.getId(), data.concentration()));
            data.markDirty();
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer player) || !player.isAlive()) return;
        BreathCapability.get(player).ifPresent(data -> tick(player, data));
    }

    private static void tick(ServerPlayer player, BreathData data) {
        SunConfig.Server cfg = SunConfig.SERVER;
        float max = cfg.maxBreath.get().floatValue();
        if (data.breath() < 0f) {
            data.setBreath(max, max);
            data.markDirty();
        }
        data.tickTimers();

        // Horizontal distance moved this tick decides "standing still" vs "walking".
        double moved = 0;
        if (!Double.isNaN(data.lastX)) {
            double dx = player.getX() - data.lastX, dz = player.getZ() - data.lastZ;
            moved = Math.sqrt(dx * dx + dz * dz);
        }
        data.lastX = player.getX();
        data.lastZ = player.getZ();

        float breath = data.breath();
        if (data.concentration()) {
            breath -= cfg.concentrationUpkeep.get().floatValue() / 20f;
            if (breath <= 0f) {
                data.setBreath(0f, max);
                ConcentrationLogic.set(player, data, false, true);
                breath = 0f;
            }
        }

        if (!data.tickRegenDelay() && !player.isSprinting()) {
            double perSecond = moved < 0.02 ? cfg.regenStanding.get() : cfg.regenWalking.get();
            breath += (float) (perSecond / 20.0);
        }
        data.setBreath(breath, max);

        // Sync: immediately on discrete changes, otherwise throttled while breath drifts.
        data.ticksSinceSync++;
        boolean dirty = data.consumeDirty();
        float delta = Float.isNaN(data.lastSyncedBreath) ? Float.MAX_VALUE : Math.abs(data.breath() - data.lastSyncedBreath);
        if (dirty || (delta >= 0.5f && data.ticksSinceSync >= 3) || (delta > 0f && data.ticksSinceSync >= 20)) {
            ModNetwork.sendTo(player, S2CSyncBreath.of(data, max));
            data.lastSyncedBreath = data.breath();
            data.ticksSinceSync = 0;
        }
    }
}
