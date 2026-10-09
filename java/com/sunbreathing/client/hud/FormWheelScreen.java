package com.sunbreathing.client.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sunbreathing.client.ClientBreathState;
import com.sunbreathing.client.Keybinds;
import com.sunbreathing.client.particle.SunPalette;
import com.sunbreathing.forms.Form;
import com.sunbreathing.network.C2SSelectForm;
import com.sunbreathing.network.ModNetwork;
import com.sunbreathing.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Radial form selector. Hold the wheel key, point at a form, release to select (or click).
 * Twelve sectors laid out like a clock face, I at the top. Locked forms are drawn dim.
 * The game is NOT paused, so you can pick a form mid-fight.
 */
public class FormWheelScreen extends Screen {
    private static final float INNER = 34f, OUTER = 96f;
    private int hovered = -1;
    private int lastHovered = -1;
    private float open; // 0..1 open animation

    public FormWheelScreen() {
        super(Component.translatable("screen.sunbreathing.form_wheel"));
    }

    @Override
    protected void init() {
        hovered = -1;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_OPEN.get(), 1.0f, 0.6f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        open = Math.min(1f, open + 0.34f);
        // Close (and select) as soon as the wheel key is released.
        InputConstants.Key key = Keybinds.FORM_WHEEL.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM
                && !InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue())) {
            confirm();
        }
    }

    private int sectorAt(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER * 0.6) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2; // 0 at top, clockwise
        if (angle < 0) angle += Math.PI * 2;
        int n = Form.COUNT;
        return (int) Math.floor((angle + Math.PI / n) / (Math.PI * 2 / n)) % n;
    }

    private void confirm() {
        if (hovered >= 0) {
            Form f = Form.byIndex(hovered);
            if (f != ClientBreathState.selected) {
                ClientBreathState.selected = f; // optimistic; server confirms in next sync
                ModNetwork.sendToServer(new C2SSelectForm(f));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_SELECT.get(), 1.0f, 0.7f));
            }
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        hovered = sectorAt(mx, my);
        confirm();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hovered = sectorAt(mouseX, mouseY);
        if (hovered != lastHovered && hovered >= 0) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_SELECT.get(), 1.6f, 0.15f));
        }
        lastHovered = hovered;

        float ease = 1f - (1f - Math.min(1f, open + partialTick * 0.34f)) * (1f - Math.min(1f, open + partialTick * 0.34f));
        float cx = width / 2f, cy = height / 2f;
        float inner = INNER * (0.7f + 0.3f * ease), outer = OUTER * (0.7f + 0.3f * ease);
        int n = Form.COUNT;
        float step = Mth.TWO_PI / n;

        g.fill(0, 0, width, height, (int) (0x60 * ease) << 24);
        g.flush();

        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();

        // Sectors
        RenderSystem.defaultBlendFunc();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            Form f = Form.byIndex(i);
            float a0 = -Mth.HALF_PI + (i - 0.5f) * step + 0.012f;
            float a1 = -Mth.HALF_PI + (i + 0.5f) * step - 0.012f;
            boolean hot = i == hovered;
            boolean sel = f == ClientBreathState.selected;
            float o = hot ? outer + 6f : outer;
            int cIn = f.implemented ? (hot ? SunPalette.argb(0.2f, 0.9f * ease) : 0xC0200806) : 0xA0141010;
            int cOut = f.implemented ? (hot ? SunPalette.argb(0.55f, 0.95f * ease) : (sel ? SunPalette.argb(0.6f, 0.85f * ease) : 0xC03A0F08)) : 0xA0221A18;
            ring(bb, m, cx, cy, inner, o, a0, a1, cIn, cOut, 8);
        }
        BufferUploader.drawWithShader(bb.end());

        // Additive rim glow on hovered sector
        if (hovered >= 0) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            float a0 = -Mth.HALF_PI + (hovered - 0.5f) * step, a1 = -Mth.HALF_PI + (hovered + 0.5f) * step;
            ring(bb, m, cx, cy, outer + 6f, outer + 14f, a0, a1, SunPalette.argb(0.1f, 0.7f * ease), SunPalette.argb(0.5f, 0f), 8);
            BufferUploader.drawWithShader(bb.end());
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Labels: roman numerals on the ring, full name in the centre
        for (int i = 0; i < n; i++) {
            Form f = Form.byIndex(i);
            float a = -Mth.HALF_PI + i * step;
            float r = (inner + outer) / 2f + (i == hovered ? 3f : 0f);
            int col = !f.implemented ? 0x6A5A55 : (i == hovered ? 0xFFFFFF : 0xFFD9A0);
            String txt = f.roman();
            g.drawString(font, txt, (int) (cx + Mth.cos(a) * r - font.width(txt) / 2f), (int) (cy + Mth.sin(a) * r - 4), col, true);
        }
        Form centre = hovered >= 0 ? Form.byIndex(hovered) : ClientBreathState.selected;
        Component name = Component.translatable(centre.langKey());
        g.drawCenteredString(font, name, (int) cx, (int) cy - 9, centre.implemented ? 0xFFE7B0 : 0x8A7A75);
        Component sub = centre.implemented
                ? Component.translatable("screen.sunbreathing.form_number", centre.roman())
                : Component.translatable("screen.sunbreathing.locked");
        g.drawCenteredString(font, sub, (int) cx, (int) cy + 3, 0xA08070);
    }

    private static void ring(BufferBuilder bb, Matrix4f m, float cx, float cy, float r0, float r1, float a0, float a1,
                             int cIn, int cOut, int seg) {
        for (int s = 0; s < seg; s++) {
            float b0 = Mth.lerp(s / (float) seg, a0, a1), b1 = Mth.lerp((s + 1) / (float) seg, a0, a1);
            float x00 = cx + Mth.cos(b0) * r0, y00 = cy + Mth.sin(b0) * r0;
            float x01 = cx + Mth.cos(b0) * r1, y01 = cy + Mth.sin(b0) * r1;
            float x10 = cx + Mth.cos(b1) * r0, y10 = cy + Mth.sin(b1) * r0;
            float x11 = cx + Mth.cos(b1) * r1, y11 = cy + Mth.sin(b1) * r1;
            v(bb, m, x00, y00, cIn);
            v(bb, m, x01, y01, cOut);
            v(bb, m, x11, y11, cOut);
            v(bb, m, x00, y00, cIn);
            v(bb, m, x11, y11, cOut);
            v(bb, m, x10, y10, cIn);
        }
    }

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, int argb) {
        VertexConsumer c = bb.vertex(m, x, y, 0);
        c.color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).endVertex();
    }
}
