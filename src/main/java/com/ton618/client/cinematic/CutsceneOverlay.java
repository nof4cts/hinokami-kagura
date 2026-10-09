package com.ton618.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Letterbox bars (2.39:1), lower-third captions and the skip hint, drawn over everything during cutscenes. */
public final class CutsceneOverlay {
    private CutsceneOverlay() {}

    public static void draw(GuiGraphics g, float partial) {
        if (!Cutscene.active()) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = Math.max(0, (int) ((h - w / 2.39f) / 2f));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        Font font = mc.font;
        String[] cap = Cutscene.caption(partial);
        if (cap != null) {
            int y = h - bar - 34;
            int x = (int) (w * 0.08f);
            g.fill(x, y - 4, x + 1, y + 20, 0xCCFFB347);
            g.drawString(font, spaced(cap[0]), x + 7, y, 0xFFFFE2B8, false);
            if (!cap[1].isEmpty()) g.drawString(font, spaced(cap[1]), x + 7, y + 11, 0xAAFFD8A0, false);
        }
        float blink = 0.4f + 0.3f * Mth.sin((mc.level == null ? 0 : mc.level.getGameTime() + partial) * 0.2f);
        int a = (int) (blink * 255) << 24;
        String hint = "HOLD SHIFT TO SKIP";
        g.drawString(font, hint, w - font.width(hint) - 10, h - bar + Math.max(2, (bar - 8) / 2), a | 0xFFFFFF, false);
    }

    /** Wide tracking for a filmic title look. */
    private static String spaced(String s) {
        StringBuilder b = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            b.append(s.charAt(i));
            if (i < s.length() - 1) b.append(' ');
        }
        return b.toString();
    }
}
