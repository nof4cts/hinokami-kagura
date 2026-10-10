package com.ton618.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One strike body as the server knows it: TON 618, the falling Sun, or the Archangel. Everything the client
 * needs to animate it on its own: it moves from {@code start} to {@code end} over {@code descentTicks} game
 * ticks beginning at {@code startTick} (0 ticks = it simply hangs at {@code end}).
 *
 * @param rs     size: event horizon radius (hole), radius (Sun) or height (Archangel), in blocks
 * @param tiltX/tiltZ  accretion disk tilt in degrees (hole)
 * @param kind   {@link StrikeKind} ordinal
 * @param yaw    facing in degrees (Archangel: the direction of the cut)
 * @param impactTick  game tick the blow lands (for the Archangel this is the slash, after landing)
 */
public record BlackHole(int id, Vec3 start, Vec3 end, float rs, long startTick, int descentTicks,
                        float tiltX, float tiltZ, long endTick, int kind, float yaw, long impactTick) {

    public StrikeKind strikeKind() {
        return StrikeKind.byId(kind);
    }

    /** 0..1 fade multiplier for the final collapse (1 until 60 ticks before endTick). */
    public float remaining(double gameTime) {
        if (endTick == Long.MAX_VALUE) return 1f;
        return Mth.clamp((float) ((endTick - gameTime) / 60.0), 0f, 1f);
    }

    public BlackHole withEndTick(long collapseAt) {
        return new BlackHole(id, start, end, rs, startTick, descentTicks, tiltX, tiltZ, collapseAt, kind, yaw, impactTick);
    }

    public boolean falls() {
        return descentTicks > 0;
    }

    public float progress(double gameTime) {
        if (!falls()) return 1f;
        return Mth.clamp((float) ((gameTime - startTick) / descentTicks), 0f, 1f);
    }

    /**
     * Position at game time. The hole and the Sun accelerate (they are falling: a gentle start, then the last
     * stretch very fast); the Archangel decelerates, settling like something that chooses where it lands.
     */
    public Vec3 position(double gameTime) {
        if (!falls()) return end;
        float t = progress(gameTime);
        float eased;
        if (strikeKind() == StrikeKind.ANGEL) {
            float u = 1f - t;
            eased = 1f - u * u * u * u;
        } else {
            eased = t * t * t * (0.35f + 0.65f * t);
        }
        return start.lerp(end, eased);
    }

    public boolean landed(double gameTime) {
        return falls() && gameTime >= startTick + descentTicks;
    }

    public boolean struck(double gameTime) {
        return gameTime >= impactTick;
    }

    /** Unit horizontal facing from yaw (Minecraft convention). */
    public Vec3 facing() {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
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
        buf.writeVarInt(kind);
        buf.writeFloat(yaw);
        buf.writeLong(impactTick);
    }

    public static BlackHole read(FriendlyByteBuf buf) {
        return new BlackHole(buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readLong(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readLong(),
                buf.readVarInt(), buf.readFloat(), buf.readLong());
    }
}
