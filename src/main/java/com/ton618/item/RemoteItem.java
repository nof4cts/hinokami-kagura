package com.ton618.item;

import com.ton618.config.Ton618Config;
import com.ton618.network.Net;
import com.ton618.network.S2CLock;
import com.ton618.registry.ModRegistry;
import com.ton618.server.Strikes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The SS-618 remote.
 *   Use once:        lock the block you're looking at (up to the lock range); the HUD brackets it.
 *   Use again (5 s): fire. The orbital cannon charges, shoots, and TON 618 falls on the lock.
 *   Sneak + use:     cancel the lock.
 */
public class RemoteItem extends Item {
    public static final int LOCK_TICKS = 100;
    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    private record Lock(Vec3 target, long expires) {}

    public RemoteItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, true);
        long now = level.getGameTime();

        if (sp.isShiftKeyDown()) {
            if (LOCKS.remove(sp.getUUID()) != null) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), S2CLock.cleared());
                sp.playNotifySound(ModRegistry.REMOTE_DENY.get(), SoundSource.PLAYERS, 0.8f, 0.8f);
            }
            return InteractionResultHolder.success(stack);
        }

        Lock lock = LOCKS.get(sp.getUUID());
        if (lock != null && now < lock.expires()) {
            LOCKS.remove(sp.getUUID());
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), S2CLock.cleared());
            sp.playNotifySound(ModRegistry.REMOTE_ARM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            Strikes.start(sp, lock.target());
            if (!sp.getAbilities().instabuild) {
                sp.getCooldowns().addCooldown(this, Ton618Config.SERVER.cooldownSeconds.get() * 20);
            }
            return InteractionResultHolder.consume(stack);
        }

        HitResult hit = sp.pick(Strikes.lockRange(sp), 1f, false);
        if (hit.getType() == HitResult.Type.MISS) {
            sp.playNotifySound(ModRegistry.REMOTE_DENY.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
            sp.displayClientMessage(Component.translatable("message.ton618.no_target").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        Vec3 target = hit.getLocation();
        LOCKS.put(sp.getUUID(), new Lock(target, now + LOCK_TICKS));
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2CLock(true, target, now + LOCK_TICKS));
        sp.playNotifySound(ModRegistry.REMOTE_LOCK.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ton618.remote.tip1").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.ton618.remote.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.ton618.remote.tip3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
