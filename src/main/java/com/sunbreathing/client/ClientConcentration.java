package com.sunbreathing.client;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.util.Mth;

/**
 * Which players (by entity id) are in Total Concentration, with an eased 0..1 "ignite" value per
 * player that drives the blade's black -> crimson fade and the aura strength.
 */
public final class ClientConcentration {
    private static final IntOpenHashSet ACTIVE = new IntOpenHashSet();
    private static final Int2FloatOpenHashMap IGNITE = new Int2FloatOpenHashMap();
    private static final Int2FloatOpenHashMap PREV = new Int2FloatOpenHashMap();

    private ClientConcentration() {}

    public static void set(int entityId, boolean active) {
        if (active) ACTIVE.add(entityId);
        else ACTIVE.remove(entityId);
    }

    public static boolean isActive(int entityId) {
        return ACTIVE.contains(entityId);
    }

    /** Called once per client tick. Ignite rises in ~0.4s and cools down over ~1s. */
    public static void tick() {
        for (int id : ACTIVE) {
            if (!IGNITE.containsKey(id)) IGNITE.put(id, 0f);
        }
        var it = IGNITE.int2FloatEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            int id = e.getIntKey();
            float v = e.getFloatValue();
            PREV.put(id, v);
            float next = ACTIVE.contains(id) ? Mth.approach(v, 1f, 0.125f) : Mth.approach(v, 0f, 0.05f);
            if (next <= 0f && !ACTIVE.contains(id)) {
                it.remove();
                PREV.remove(id);
            } else {
                e.setValue(next);
            }
        }
    }

    /** Eased ignite amount 0..1 for an entity. */
    public static float ignite(int entityId, float partial) {
        if (!IGNITE.containsKey(entityId)) return 0f;
        float v = Mth.lerp(partial, PREV.getOrDefault(entityId, 0f), IGNITE.get(entityId));
        return v * v * (3f - 2f * v); // smoothstep
    }

    public static void reset() {
        ACTIVE.clear();
        IGNITE.clear();
        PREV.clear();
    }
}
