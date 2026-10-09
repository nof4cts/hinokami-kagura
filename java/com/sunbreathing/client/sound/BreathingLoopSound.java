package com.sunbreathing.client.sound;

import com.sunbreathing.client.ClientBreathState;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.registry.ModSounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * The subtle rhythmic breathing heard only by the concentrating player. Follows the player,
 * fades in/out smoothly, and stops itself once Total Concentration ends.
 */
public class BreathingLoopSound extends AbstractTickableSoundInstance {
    private final LocalPlayer player;
    private float fade;

    public BreathingLoopSound(LocalPlayer player) {
        super(ModSounds.BREATH_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {
        if (player.isRemoved()) {
            stop();
            return;
        }
        boolean active = ClientBreathState.concentration && player.isAlive();
        fade += active ? 0.08f : -0.06f;
        if (fade <= 0f && !active) {
            stop();
            return;
        }
        fade = Math.min(1f, Math.max(0f, fade));
        this.volume = Math.max(0.001f, fade * SunConfig.CLIENT.breathingVolume.get().floatValue());
        this.x = player.getX();
        this.y = player.getEyeY();
        this.z = player.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
