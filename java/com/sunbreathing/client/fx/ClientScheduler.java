package com.sunbreathing.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-tick scheduler for VFX timelines (e.g. spawn the slash mesh 2 ticks after the cast packet). */
public final class ClientScheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private ClientScheduler() {}

    public static void schedule(int delayTicks, Runnable action) {
        if (delayTicks <= 0) {
            action.run();
            return;
        }
        PENDING.add(new Task(delayTicks, action));
    }

    public static void tick() {
        TASKS.addAll(PENDING);
        PENDING.clear();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (--t.ticks <= 0) {
                it.remove();
                t.action.run();
            }
        }
    }

    public static void clear() {
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
