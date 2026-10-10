package com.shrine.client.cinematic;

import net.minecraft.world.phys.Vec3;

/** Where the cutscene camera is this frame: position, yaw/pitch (Minecraft convention), roll and FOV. */
public record Pose(Vec3 pos, float yaw, float pitch, float roll, float fov) {
    static Pose look(Vec3 pos, Vec3 at, float roll, float fov) {
        Vec3 d = at.subtract(pos);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
        return new Pose(pos, yaw, pitch, roll, fov);
    }
}
