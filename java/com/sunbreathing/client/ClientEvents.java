package com.sunbreathing.client;

import com.sunbreathing.SunBreathingMod;
import com.sunbreathing.client.anim.PlayerAnims;
import com.sunbreathing.client.fx.AfterimageRenderer;
import com.sunbreathing.client.fx.CameraFx;
import com.sunbreathing.client.fx.ClientScheduler;
import com.sunbreathing.client.fx.ConcentrationFx;
import com.sunbreathing.client.fx.SlashArcRenderer;
import com.sunbreathing.client.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only game events on the FORGE bus: input, per-tick VFX, world rendering, camera. */
@Mod.EventBusSubscriber(modid = SunBreathingMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Keybinds.handle();
        if (mc.isPaused()) return;
        ClientBreathState.tick();
        ClientConcentration.tick();
        ClientScheduler.tick();
        ConcentrationFx.tick();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) PlayerAnims.updateHitStop();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        AfterimageRenderer.render(event);
        SlashArcRenderer.render(event);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        CameraFx.onCameraAngles(event);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        CameraFx.onFov(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        RenderContext.push(event.getEntity());
    }

    /** If another mod cancels the render, Post never fires; don't leave a stale holder behind. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderLivingPreCancelled(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled()) RenderContext.pop(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        RenderContext.pop(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBreathState.reset();
        ClientConcentration.reset();
        ClientScheduler.clear();
        SlashArcRenderer.clear();
        AfterimageRenderer.clear();
        ConcentrationFx.reset();
        CameraFx.reset();
    }
}
