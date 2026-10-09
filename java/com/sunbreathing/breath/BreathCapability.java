package com.sunbreathing.breath;

import com.sunbreathing.SunBreathingMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BreathCapability {
    public static final Capability<BreathData> BREATH = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = SunBreathingMod.id("breath");

    private BreathCapability() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(BreathData.class);
    }

    public static LazyOptional<BreathData> get(Player player) {
        return player.getCapability(BREATH);
    }

    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final BreathData data = new BreathData();
        private final LazyOptional<BreathData> optional = LazyOptional.of(() -> data);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return BREATH.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}
