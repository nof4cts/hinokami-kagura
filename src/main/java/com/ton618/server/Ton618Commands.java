package com.ton618.server;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ton618.Ton618Mod;
import com.ton618.common.StrikeKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Test commands for Stage 1 (the orbital cannon and remote replace these as the real trigger later):
 *   /ton618 preview [distance] [size]  - a black hole hanging in front of you, to study the visuals
 *   /ton618 drop [size]                - one falls from the sky onto the block you're looking at
 *   /ton618 strike [hole|sun|angel]    - the full sequence (cutscene + destruction) on the block you look at
 *   /ton618 clear                      - remove all black holes in this dimension
 * size = event horizon radius in blocks.
 */
@Mod.EventBusSubscriber(modid = Ton618Mod.MODID)
public final class Ton618Commands {
    private Ton618Commands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ton618").requires(s -> s.hasPermission(2))
                .then(Commands.literal("preview")
                        .executes(c -> preview(c, 160f, 12f))
                        .then(Commands.argument("distance", FloatArgumentType.floatArg(10f, 3000f))
                                .executes(c -> preview(c, FloatArgumentType.getFloat(c, "distance"), 12f))
                                .then(Commands.argument("size", FloatArgumentType.floatArg(0.5f, 400f))
                                        .executes(c -> preview(c, FloatArgumentType.getFloat(c, "distance"),
                                                FloatArgumentType.getFloat(c, "size"))))))
                .then(Commands.literal("drop")
                        .executes(c -> drop(c, 18f))
                        .then(Commands.argument("size", FloatArgumentType.floatArg(0.5f, 400f))
                                .executes(c -> drop(c, FloatArgumentType.getFloat(c, "size")))))
                .then(Commands.literal("strike")
                        .executes(c -> strike(c, StrikeKind.HOLE))
                        .then(Commands.literal("hole").executes(c -> strike(c, StrikeKind.HOLE)))
                        .then(Commands.literal("sun").executes(c -> strike(c, StrikeKind.SUN)))
                        .then(Commands.literal("angel").executes(c -> strike(c, StrikeKind.ANGEL))))
                .then(Commands.literal("clear").executes(Ton618Commands::clear)));
    }

    private static int strike(CommandContext<CommandSourceStack> c, StrikeKind kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        HitResult hit = p.pick(Strikes.lockRange(p), 1f, false);
        if (hit.getType() == HitResult.Type.MISS) {
            c.getSource().sendFailure(Component.literal("Look at the ground (within range) to strike."));
            return 0;
        }
        Strikes.start(p, hit.getLocation(), kind);
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> c, float distance, float size) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Vec3 pos = p.getEyePosition().add(p.getLookAngle().scale(distance));
        BlackHoleManager.spawn(p.serverLevel(), pos, pos, size, 0, 0);
        c.getSource().sendSuccess(() -> Component.literal("TON 618 preview placed " + (int) distance
                + " blocks ahead (horizon radius " + size + ")."), false);
        return 1;
    }

    private static int drop(CommandContext<CommandSourceStack> c, float size) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        HitResult hit = p.pick(800.0, 1f, false);
        Vec3 target = hit.getType() == HitResult.Type.MISS
                ? p.getEyePosition().add(p.getLookAngle().scale(300))
                : hit.getLocation();
        // Comes in steeply from high above and slightly behind the target, as if fired from orbit.
        Vec3 flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        Vec3 start = target.add(0, 900, 0).subtract(flat.normalize().scale(260));
        BlackHoleManager.spawn(level, start, target, size, 0, 20 * 14);
        c.getSource().sendSuccess(() -> Component.literal("TON 618 inbound. Impact in 14 seconds."), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        Strikes.cancelAll(c.getSource().getLevel());
        int n = BlackHoleManager.clear(c.getSource().getLevel());
        c.getSource().sendSuccess(() -> Component.literal("Removed " + n + " black hole(s)."), false);
        return n;
    }
}
