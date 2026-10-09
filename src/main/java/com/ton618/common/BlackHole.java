package com.ton618.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One black hole, as the server knows it. Everything the client needs to animate it on its own:
 * it falls from {@code start} to {@code end} over {@code descentTicks} game ticks beginning at
 * {@code startTick} (0 ticks = it simply hangs at {@code end}).
 *
 * @param rs   event horizon radius (Schwarzschild radius) in blocks
 * @param tiltX/tiltZ  accretion disk tilt in degrees, so every hole looks a little different
 */
public record BlackHole(int id, Vec3 start, Vec3 end, float rs, long startTick, int descentTicks,
                        float tiltX, float tiltZ, long endTick) {

    /** 0..1 fade multiplier for the hole's final collapse (1 until 60 ticks before endTick). */
    public float remaining(double gameTime) {
        if (endTick == Long.MAX_VALUE) return 1f;
        return Mth.clamp((float) ((endTick - gameTime) / 60.0), 0f, 1f);
    }

    public BlackHole withEndTick(long collapseAt) {
        return new BlackHole(id, start, end, rs, startTick, descentTicks, tiltX, tiltZ, collapseAt);
    }

    public boolean falls() {
        return descentTicks > 0;
    }

    /** 0..1 progress of the descent at (fractional) game time. */
    public float progress(double gameTime) {
        if (!falls()) return 1f;
        return Mth.clamp((float) ((gameTime - startTick) / descentTicks), 0f, 1f);
    }

    /**
     * Position at game time. The fall accelerates (it is being pulled in, not floating down):
     * a gentle start, then it closes the last stretch very fast.
     */
    public Vec3 position(double gameTime) {
        if (!falls()) return end;
        float t = progress(gameTime);
        float eased = t * t * t * (0.35f + 0.65f * t);
        return start.lerp(end, eased);
    }

    public boolean landed(double gameTime) {
        return falls() && gameTime >= startTick + descentTicks;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(end.x);
        buf.writeDouble(end.y);
        buf.writeDouble(end.z);
        buf.writeFloat(rs);
        buf.writeLong(startTick);
        buf.writeVarInt(descentTicks);
        buf.writeFloat(tiltX);
        buf.writeFloat(tiltZ);
        buf.writeLong(endTick);
    }

    public static BlackHole read(FriendlyByteBuf buf) {
        return new BlackHole(buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readLong(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readLong());
    }
}
