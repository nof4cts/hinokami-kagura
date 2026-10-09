package com.sunbreathing.client.render;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Item renderers don't know WHO is holding the item. We record the living entity currently being
 * rendered (RenderLivingEvent.Pre/Post) so the blade can glow for the right player.
 * First-person and GUI rendering fall back to the local player.
 */
public final class RenderContext {
    @Nullable
    private static LivingEntity current;

    private RenderContext() {}

    public static void push(LivingEntity entity) {
        current = entity;
    }

    public static void pop(LivingEntity entity) {
        if (current == entity) current = null;
    }

    @Nullable
    public static LivingEntity current() {
        return current;
    }
}
