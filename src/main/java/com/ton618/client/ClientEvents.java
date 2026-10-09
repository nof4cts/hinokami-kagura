package com.ton618.client;

import com.ton618.Ton618Mod;
import com.ton618.client.cinematic.Cutscene;
import com.ton618.client.cinematic.CutsceneOverlay;
import com.ton618.client.cinematic.Pose;
import com.ton618.client.render.PostFx;
import net.minecraft.client.player.Input;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only hooks. */
public final class ClientEvents {
    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = Ton618Mod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        /** (Re)compile the effect shaders whenever resources load, so F3+T picks up shader edits. */
        @SubscribeEvent
        public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) PostFx::reload);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "remote_hud", RemoteHud.INSTANCE);
        }
    }

    @Mod.EventBusSubscriber(modid = Ton618Mod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) Cutscene.frame(event.renderTickTime);
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            PostFx.render(event);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            ClientHoles.tick();
            Cutscene.tick();
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onCutsceneAngles(ViewportEvent.ComputeCameraAngles event) {
            Pose pose = Cutscene.pose();
            if (pose != null && !pose.space()) event.setRoll(pose.roll());
        }

        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            ScreenFx.onCameraAngles(event);
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            Pose pose = Cutscene.pose();
            if (pose != null && !pose.space() && event.usedConfiguredFov()) event.setFOV(pose.fov());
        }

        /** No walking around during the cutscene. */
        @SubscribeEvent
        public static void onMovementInput(MovementInputUpdateEvent event) {
            if (!Cutscene.active()) return;
            Input in = event.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }

        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            if (Cutscene.active()) event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
            if (Cutscene.active()) event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onGuiPost(RenderGuiEvent.Post event) {
            CutsceneOverlay.draw(event.getGuiGraphics(), event.getPartialTick());
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Cutscene.stop();
            ClientHoles.clear();
            ScreenFx.reset();
        }
    }
}
