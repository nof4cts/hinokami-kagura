package com.sunbreathing.breath;

import com.sunbreathing.forms.Form;
import net.minecraft.nbt.CompoundTag;

/**
 * Per-player Sun Breathing state (server-authoritative, attached as a Forge capability).
 *
 * Persistent: breath, concentration flag, selected form, cooldowns.
 * Transient:  cast lock, regen delay, last position (for "standing still" detection), sync bookkeeping.
 */
public class BreathData {
    private float breath = -1f; // -1 = "fill to max on first tick" (max comes from config)
    private boolean concentration;
    private int selected;
    private final int[] cooldowns = new int[Form.COUNT];

    // transient
    private int castLock;
    private int regenDelay;
    double lastX = Double.NaN, lastZ = Double.NaN;
    float lastSyncedBreath = Float.NaN;
    int ticksSinceSync;
    private boolean dirty = true;

    public float breath() {
        return breath;
    }

    public void setBreath(float value, float max) {
        float v = Math.max(0f, Math.min(max, value));
        if (v != breath) breath = v;
    }

    public boolean consume(float amount) {
        if (breath < amount) return false;
        breath -= amount;
        dirty = true; // spending breath is a discrete event: sync immediately
        return true;
    }

    public boolean concentration() {
        return concentration;
    }

    void setConcentration(boolean on) {
        if (concentration != on) {
            concentration = on;
            dirty = true;
        }
    }

    public Form selected() {
        return Form.byIndex(selected);
    }

    public void setSelected(Form form) {
        if (selected != form.ordinal()) {
            selected = form.ordinal();
            dirty = true;
        }
    }

    public int cooldown(Form f) {
        return cooldowns[f.ordinal()];
    }

    public void setCooldown(Form f, int ticks) {
        cooldowns[f.ordinal()] = Math.max(0, ticks);
        dirty = true;
    }

    public int[] cooldownsView() {
        return cooldowns;
    }

    public int castLock() {
        return castLock;
    }

    public void setCastLock(int ticks) {
        castLock = ticks;
    }

    public void setRegenDelay(int ticks) {
        regenDelay = Math.max(regenDelay, ticks);
    }

    /** Ticks timers down. Cooldowns are also counted down client-side, so they don't force a sync. */
    void tickTimers() {
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) cooldowns[i]--;
        }
        if (castLock > 0) castLock--;
    }

    boolean tickRegenDelay() {
        if (regenDelay > 0) {
            regenDelay--;
            return true;
        }
        return false;
    }

    public void markDirty() {
        dirty = true;
    }

    boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void copyFrom(BreathData other, boolean death) {
        this.selected = other.selected;
        System.arraycopy(other.cooldowns, 0, this.cooldowns, 0, cooldowns.length);
        if (death) {
            this.breath = -1f;
            this.concentration = false;
        } else {
            this.breath = other.breath;
            this.concentration = other.concentration;
        }
        this.dirty = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("breath", breath);
        tag.putBoolean("concentration", concentration);
        tag.putInt("selected", selected);
        tag.putIntArray("cooldowns", cooldowns.clone());
        return tag;
    }

    public void load(CompoundTag tag) {
        breath = tag.contains("breath") ? tag.getFloat("breath") : -1f;
        concentration = tag.getBoolean("concentration");
        selected = Math.floorMod(tag.getInt("selected"), Form.COUNT);
        int[] cd = tag.getIntArray("cooldowns");
        for (int i = 0; i < Math.min(cd.length, cooldowns.length); i++) cooldowns[i] = cd[i];
        dirty = true;
    }
}
