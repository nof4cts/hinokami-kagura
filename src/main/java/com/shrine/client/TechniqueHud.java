package com.shrine.client;

import com.shrine.client.cinematic.Cutscene;
import com.shrine.common.Move;
import com.shrine.network.S2CCooldowns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

/** A slim list of the moves at the bottom left: key, name, and a cooldown bar that fills as it recovers. */
public final class TechniqueHud implements IGuiOverlay {
    public static final TechniqueHud INSTANCE = new TechniqueHud();
    private static long[] readyAt = new long[Move.values().length];
    private static int[] total = new int[Move.values().length];

    private TechniqueHud() {}

    public static void cooldowns(S2CCooldowns msg) {
        readyAt = msg.readyAt;
        total = msg.total;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || Cutscene.active()) return;
        Font font = mc.font;
        long now = mc.level.getGameTime();
        int x = 6, y = h - 6 - Move.values().length * 11;
        for (Move m : Move.values()) {
            int i = m.ordinal();
            long left = readyAt[i] - now;
            float frac = total[i] <= 0 ? 1f : 1f - Math.max(0f, left / (float) total[i]);
            boolean ready = left <= 0;
            String key = m == Move.BARRAGE ? "hold " + Keybinds.keyFor(m).getTranslatedKeyMessage().getString()
                    : Keybinds.keyFor(m).getTranslatedKeyMessage().getString();
            String name = Component.translatable("move.shrine." + m.key).getString().toUpperCase(Locale.ROOT);
            g.fill(x, y + 9, x + 70, y + 10, 0x55000000);
            g.fill(x, y + 9, x + (int) (70 * frac), y + 10, ready ? 0xFFE02020 : 0xFF702020);
            g.drawString(font, "[" + key + "] " + name, x, y, ready ? 0xFFFFFFFF : 0xFF808080, true);
            y += 11;
        }
    }
}
