package com.ton618.server;

import com.ton618.Ton618Mod;
import com.ton618.common.BlackHole;
import com.ton618.network.Net;
import com.ton618.network.S2CBlackHoles;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Server-side registry of black holes per dimension; the single source of truth clients mirror. */
@Mod.EventBusSubscriber(modid = Ton618Mod.MODID)
public final class BlackHoleManager {
    private static final Map<ResourceKey<Level>, List<BlackHole>> HOLES = new HashMap<>();
    private static int nextId = 1;

    private BlackHoleManager() {}

    /**
     * @param delayTicks   ticks from now before the hole appears / starts falling
     * @param descentTicks 0 = hangs at {@code end}
     */
    public static BlackHole spawn(ServerLevel level, Vec3 start, Vec3 end, float rs, int delayTicks, int descentTicks) {
        float tiltX = 8f + level.random.nextFloat() * 14f;
        float tiltZ = -10f + level.random.nextFloat() * 20f;
        BlackHole hole = new BlackHole(nextId++, start, end, rs, level.getGameTime() + delayTicks, descentTicks,
                tiltX, tiltZ, Long.MAX_VALUE);
        HOLES.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(hole);
        sync(level);
        return hole;
    }

    /** Schedules a hole's collapse; it fades out over the last 60 ticks and is then removed. */
    public static void collapse(ServerLevel level, int holeId, long atTick) {
        List<BlackHole> list = HOLES.get(level.dimension());
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == holeId) list.set(i, list.get(i).withEndTick(atTick));
        }
        sync(level);
    }

    public static List<BlackHole> in(ServerLevel level) {
        return HOLES.getOrDefault(level.dimension(), List.of());
    }

    public static int clear(ServerLevel level) {
        List<BlackHole> removed = HOLES.remove(level.dimension());
        sync(level);
        return removed == null ? 0 : removed.size();
    }

    public static void sync(ServerLevel level) {
        Net.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), new S2CBlackHoles(in(level)));
    }

    private static void syncTo(ServerPlayer player) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CBlackHoles(in(player.serverLevel())));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        List<BlackHole> list = HOLES.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        long now = level.getGameTime();
        if (list.removeIf(h -> now >= h.endTick())) sync(level);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) syncTo(sp);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) syncTo(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) syncTo(sp);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HOLES.clear();
    }
}
