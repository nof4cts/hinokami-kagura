package com.ton618.client;

import com.ton618.common.BlackHole;
import com.ton618.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The hole's deep, endless rumble. Played without distance attenuation and faded by hand instead, so
 * it carries much farther than a normal sound (you should feel it from hundreds of blocks away), and it
 * dies away as the hole collapses.
 */
public class HoleRumbleSound extends AbstractTickableSoundInstance {
    private final int holeId;
    private final Vec3 at;

    public HoleRumbleSound(int holeId, Vec3 at) {
        super(ModRegistry.HOLE_RUMBLE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.holeId = holeId;
        this.at = at;
        this.looping = true;
        this.delay = 0;
        this.attenuation = Attenuation.NONE;
        this.relative = true;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.volume = 0.001f;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        BlackHole hole = ClientHoles.find(holeId);
        if (hole == null || mc.level == null || mc.player == null) {
            stop();
            return;
        }
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        float near = (float) Math.max(0.0, 1.0 - d / 900.0);
        float fade = hole.remaining(mc.level.getGameTime());
        this.volume = Math.max(0.001f, near * near * fade * 1.4f);
        this.pitch = 0.85f + 0.15f * near;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
