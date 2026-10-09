package com.sunbreathing.client.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sunbreathing.client.ClientBreathState;
import com.sunbreathing.client.particle.SunPalette;
import com.sunbreathing.config.SunConfig;
import com.sunbreathing.forms.Form;
import com.sunbreathing.item.NichirinBladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;

/**
 * Breath HUD, drawn procedurally (no textures needed, scales cleanly with GUI scale):
 *
 *   [sun emblem] ==========breath bar==========
 *    12 rays       deep red -> orange -> gold fill, white-hot leading edge, rising sparks
 *    selected form ray lit; dark pie = cooldown
 *
 * Total Concentration: the emblem rotates slowly, the bar gets a breathing glow, and sparks
 * rise more often. Spending breath flashes the bar; not enough breath for the selected form
 * pulses the "cost" marker red.
 */
public final class BreathHudOverlay implements IGuiOverlay {
    public static final BreathHudOverlay INSTANCE = new BreathHudOverlay();

    private static final int BAR_W = 92, BAR_H = 5;
    private static final float EMBLEM_R = 10f;

    private BreathHudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;

        float max = ClientBreathState.maxBreath;
        float breath = ClientBreathState.displayBreath(partialTick);
        float conc = ClientBreathState.concentrationFade(partialTick);
        boolean holding = mc.player.getMainHandItem().getItem() instanceof NichirinBladeItem;
        if (!holding && !SunConfig.CLIENT.hudAlwaysVisible.get() && breath >= max - 0.05f && conc <= 0.01f) return;

        float time = (mc.player.tickCount + partialTick) / 20f;
        int cx = 18 + SunConfig.CLIENT.hudOffsetX.get();
        int cy = screenH - 22 + SunConfig.CLIENT.hudOffsetY.get();
        int barX = cx + 15, barY = cy - 2;

        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        Form selected = ClientBreathState.selected;
        float frac = Mth.clamp(breath / max, 0f, 1f);
        float cost = formCost(selected);
        boolean starved = breath < cost;

        // ---------- additive glows (behind)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float pulse = 0.5f + 0.5f * Mth.sin(time * Mth.TWO_PI / 3.2f); // ~3.2s breathing rhythm
        if (conc > 0f) {
            glowRect(bb, m, barX - 3, barY - 3, barX + BAR_W + 3, barY + BAR_H + 3, SunPalette.argb(0.45f, 0.35f * conc * (0.6f + 0.4f * pulse)));
            disc(bb, m, cx, cy, EMBLEM_R + 6f + 2f * pulse, SunPalette.argb(0.3f, 0.45f * conc), SunPalette.argb(0.7f, 0f), 28);
        }
        if (ClientBreathState.spendFlashTicks > 0) {
            float f = ClientBreathState.spendFlashTicks / 6f;
            glowRect(bb, m, barX - 2, barY - 2, barX + BAR_W + 2, barY + BAR_H + 2, SunPalette.argb(0.1f, 0.5f * f));
        }
        BufferUploader.drawWithShader(bb.end());

        // ---------- solid layers
        RenderSystem.defaultBlendFunc();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // Bar frame + background
        rect(bb, m, barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, 0xE0120402);
        rect(bb, m, barX, barY, barX + BAR_W, barY + BAR_H, 0xFF2A0B06);

        // Fill: horizontal gradient deep red -> orange -> gold across the FULL bar, clipped to fill.
        int fillW = Math.round(BAR_W * frac);
        for (int x = 0; x < fillW; x++) {
            float p = x / (float) BAR_W;
            float heat = 0.95f - p * 0.75f; // left = deep red (cool), right = gold (hot)
            float flick = 0.06f * Mth.sin(time * 9f + x * 0.7f);
            rect(bb, m, barX + x, barY, barX + x + 1, barY + BAR_H, SunPalette.argb(Mth.clamp(heat + flick, 0f, 1f), 1f));
        }
        // Top highlight
        if (fillW > 0) rect(bb, m, barX, barY, barX + fillW, barY + 1, 0x55FFF2C8);
        // White-hot leading edge
        if (fillW > 1 && frac < 0.999f) rect(bb, m, barX + fillW - 1, barY - 1, barX + fillW + 1, barY + BAR_H + 1, 0xFFFFF5E0);

        // Cost marker for the selected form
        int costX = barX + Math.round(BAR_W * Mth.clamp(cost / max, 0f, 1f));
        int markerCol = starved ? (((int) (170 + 85 * pulse)) << 24 | 0xFF2A10) : 0xA0FFFFFF;
        rect(bb, m, costX, barY - 2, costX + 1, barY + BAR_H + 2, markerCol);

        // Sun emblem: dark disc, rays, hot core
        int rays = Form.COUNT;
        float spin = ClientBreathState.concentration ? time * 0.6f : 0f;
        disc(bb, m, cx, cy, EMBLEM_R + 1.5f, 0xE0120402, 0xE0120402, 28);
        for (int i = 0; i < rays; i++) {
            float a = spin + i * Mth.TWO_PI / rays - Mth.HALF_PI;
            boolean sel = i == selected.ordinal();
            float len = sel ? EMBLEM_R + 5f : (i % 2 == 0 ? EMBLEM_R + 2.5f : EMBLEM_R + 1f);
            int col = sel ? 0xFFFFF0C0 : (Form.byIndex(i).implemented ? SunPalette.argb(0.45f, 0.95f) : 0xFF5A1A0E);
            ray(bb, m, cx, cy, a, EMBLEM_R * 0.55f, len, sel ? 1.9f : 1.2f, col);
        }
        disc(bb, m, cx, cy, EMBLEM_R * 0.62f, SunPalette.argb(0f, 1f), SunPalette.argb(0.55f, 1f), 24);

        // Cooldown: dark pie sweeping over the core
        float cd = ClientBreathState.cooldownFraction(selected);
        if (cd > 0f) pie(bb, m, cx, cy, EMBLEM_R * 0.62f, cd, 0xC0100302);
        BufferUploader.drawWithShader(bb.end());

        // ---------- rising sparks above the bar (stateless: derived from time)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int sparks = 5 + Math.round(5 * conc);
        for (int i = 0; i < sparks; i++) {
            float seed = i * 12.9898f;
            float period = 1.4f + (i % 3) * 0.35f;
            float ph = ((time + i * 0.37f) % period) / period;
            float sx = barX + ((Mth.sin(seed) * 0.5f + 0.5f) * Math.max(4, fillW));
            float sy = barY - ph * 9f;
            float a = (1f - ph) * 0.9f;
            if (sx > barX + fillW) continue;
            rectF(bb, m, sx, sy, sx + 1f, sy + 1f, SunPalette.argb(0.15f + ph * 0.6f, a));
        }
        BufferUploader.drawWithShader(bb.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();

        // ---------- text: selected form
        Font font = mc.font;
        Component label = Component.literal(selected.roman() + " · ").append(Component.translatable(selected.langKey()));
        int textCol = selected.implemented ? 0xFFE7B0 : 0x8A5A4A;
        g.drawString(font, label, barX, barY - 11, textCol, true);
    }

    /** Server config is synced on join; fall back to the form's default if it isn't loaded yet. */
    private static float formCost(Form form) {
        try {
            float cost = SunConfig.SERVER.form(form).breathCost.get().floatValue();
            if (ClientBreathState.concentration) cost *= SunConfig.SERVER.concentrationCostMultiplier.get().floatValue();
            return cost;
        } catch (IllegalStateException notLoaded) {
            return (float) form.defaultBreathCost;
        }
    }

    // ------------------------------------------------------------------ primitives (triangles)
    private static void tri(BufferBuilder bb, Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, int c1, int c2, int c3) {
        col(bb.vertex(m, x1, y1, 0), c1);
        col(bb.vertex(m, x2, y2, 0), c2);
        col(bb.vertex(m, x3, y3, 0), c3);
    }

    private static void col(com.mojang.blaze3d.vertex.VertexConsumer v, int argb) {
        v.color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).endVertex();
    }

    private static void rect(BufferBuilder bb, Matrix4f m, int x1, int y1, int x2, int y2, int c) {
        rectF(bb, m, x1, y1, x2, y2, c);
    }

    private static void rectF(BufferBuilder bb, Matrix4f m, float x1, float y1, float x2, float y2, int c) {
        tri(bb, m, x1, y1, x1, y2, x2, y2, c, c, c);
        tri(bb, m, x1, y1, x2, y2, x2, y1, c, c, c);
    }

    /** Rect whose colour fades to transparent at the border: a cheap soft glow. */
    private static void glowRect(BufferBuilder bb, Matrix4f m, float x1, float y1, float x2, float y2, int c) {
        int clear = c & 0x00FFFFFF;
        float pad = 4f;
        rectF(bb, m, x1, y1, x2, y2, c);
        // four feathered edges
        quad(bb, m, x1 - pad, y1 - pad, x2 + pad, y1 - pad, x2, y1, x1, y1, clear, clear, c, c);
        quad(bb, m, x1, y2, x2, y2, x2 + pad, y2 + pad, x1 - pad, y2 + pad, c, c, clear, clear);
        quad(bb, m, x1 - pad, y1 - pad, x1, y1, x1, y2, x1 - pad, y2 + pad, clear, c, c, clear);
        quad(bb, m, x2, y1, x2 + pad, y1 - pad, x2 + pad, y2 + pad, x2, y2, c, clear, clear, c);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy,
                             float dx, float dy, int ca, int cb, int cc, int cd) {
        tri(bb, m, ax, ay, dx, dy, cx, cy, ca, cd, cc);
        tri(bb, m, ax, ay, cx, cy, bx, by, ca, cc, cb);
    }

    private static void disc(BufferBuilder bb, Matrix4f m, float cx, float cy, float r, int inner, int outer, int seg) {
        for (int i = 0; i < seg; i++) {
            float a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
            tri(bb, m, cx, cy, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r,
                    inner, outer, outer);
        }
    }

    private static void ray(BufferBuilder bb, Matrix4f m, float cx, float cy, float angle, float r0, float r1, float halfW, int c) {
        float dx = Mth.cos(angle), dy = Mth.sin(angle);
        float px = -dy * halfW, py = dx * halfW;
        int tip = c & 0x00FFFFFF | (((c >>> 24) / 3) << 24);
        tri(bb, m, cx + dx * r0 + px, cy + dy * r0 + py, cx + dx * r1, cy + dy * r1, cx + dx * r0 - px, cy + dy * r0 - py, c, tip, c);
    }

    /** Pie from 12 o'clock clockwise covering {@code frac} of the circle. */
    private static void pie(BufferBuilder bb, Matrix4f m, float cx, float cy, float r, float frac, int c) {
        int seg = Math.max(1, Math.round(28 * frac));
        float total = Mth.TWO_PI * frac;
        for (int i = 0; i < seg; i++) {
            float a0 = -Mth.HALF_PI + total * i / seg, a1 = -Mth.HALF_PI + total * (i + 1) / seg;
            tri(bb, m, cx, cy, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, c, c, c);
        }
    }
}
