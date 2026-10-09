package com.sunbreathing.forms;

import com.sunbreathing.SunBreathingMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tiny tick scheduler for form timelines (lunge on tick 1, hit on tick 3, ...).
 * Lets a form's server logic line up with the windup frames of its client animation.
 * Only touched from the server thread.
 */
@Mod.EventBusSubscriber(modid = SunBreathingMod.MODID)
public final class ServerScheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private ServerScheduler() {}

    public static void schedule(int delayTicks, Runnable action) {
        if (delayTicks <= 0) {
            action.run();
            return;
        }
        PENDING.add(new Task(delayTicks, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TASKS.addAll(PENDING);
        PENDING.clear();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (--t.ticks <= 0) {
                it.remove();
                try {
                    t.action.run();
                } catch (Exception e) {
                    SunBreathingMod.LOGGER.error("Sun Breathing scheduled task failed", e);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
        PENDING.clear();
    }

    private static final class Task {
        int ticks;
        final Runnable action;

        Task(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }
}
