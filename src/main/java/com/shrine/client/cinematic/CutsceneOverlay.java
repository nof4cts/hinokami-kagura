package com.shrine.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Letterbox bars (2.39:1), title cards and the skip hint, drawn over everything during a cutscene. */
public final class CutsceneOverlay {
    private CutsceneOverlay() {}

    public static void draw(GuiGraphics g, float partial) {
        if (!Cutscene.active()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = Cutscene.time(partial);
        // letterbox slides in over the first few ticks
        int target = Math.max(0, (int) ((h - w / 2.39f) / 2f));
        int bar = (int) (target * Mth.clamp(t / 8f, 0f, 1f));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        Object[] card = Cutscene.card(partial);
        if (card != null) {
            String big = (String) card[0];
            String small = (String) card[1];
            int style = (Integer) card[2];
            float age = (Float) card[3];
            if (style == 1) {
                // full black card: the name, huge, centred; a hard cut in and out
                g.fill(0, 0, w, h, 0xFF000000);
                float scale = 4.0f + 0.25f * Math.min(age, 14f) / 14f;
                g.pose().pushPose();
                g.pose().translate(w / 2f, h / 2f - 18, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawCenteredString(font, big, 0, -4, 0xFFFFFFFF);
                g.pose().popPose();
                int a = (int) (255 * Mth.clamp(age / 4f, 0f, 1f));
                g.pose().pushPose();
                g.pose().translate(w / 2f, h / 2f + 22, 0);
                g.pose().scale(1.5f, 1.5f, 1f);
                g.drawCenteredString(font, spaced(small), 0, 0, (a << 24) | 0xD8D8D8);
                g.pose().popPose();
            } else {
                // lower third: slides in from the left, holds, gone on the cut
                float in = 1f - (float) Math.pow(1f - Mth.clamp(age / 5f, 0f, 1f), 3);
                int x = (int) (w * 0.08f - 30 * (1 - in));
                int y = h - bar - 58;
                int a = (int) (255 * in);
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(3.2f, 3.2f, 1f);
                g.drawString(font, big, 0, 0, (a << 24) | 0xFFFFFF, true);
                g.pose().popPose();
                g.fill(x, y + 32, x + (int) (180 * in), y + 33, (a << 24) | 0xC01818);
                g.drawString(font, spaced(small), x, y + 38, (a << 24) | 0xE0E0E0, true);
            }
        }
        if (t > 10) {
            String hint = "HOLD SHIFT TO SKIP";
            g.drawString(font, hint, w - font.width(hint) - 8, h - bar - 12, 0x66FFFFFF, false);
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
