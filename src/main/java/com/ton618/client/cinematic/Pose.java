package com.ton618.client.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * Where the camera is this frame.
 * World shots: {@code pos} in world space, aimed by yaw/pitch (Minecraft convention), plus roll and FOV.
 * Space shots ({@code space == true}): {@code pos}/{@code fwd} are in the space shader's own frame for the
 * given {@code scene} (see {@link SpaceFrame}) and the space pass replaces the whole image.
 */
public record Pose(boolean space, Vec3 pos, Vec3 fwd, float yaw, float pitch, float roll, float fov,
                   float charge, float fireAge, int scene, int kind, float progress) {

    static Pose world(Vec3 pos, Vec3 lookAt, float roll, float fov) {
        Vec3 d = lookAt.subtract(pos);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
        return new Pose(false, pos, d.normalize(), yaw, pitch, roll, fov, 0f, -1f, -1, 0, 0f);
    }

    static Pose space(int scene, int kind, Vec3 pos, Vec3 lookAt, float roll, float fov, float charge, float fireAge,
                      float progress) {
        return new Pose(true, pos, lookAt.subtract(pos).normalize(), 0f, 0f, roll, fov, charge, fireAge, scene, kind,
                progress);
    }
}
