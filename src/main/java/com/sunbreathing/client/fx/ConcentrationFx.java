package com.sunbreathing.client.fx;

import com.sunbreathing.client.ClientConcentration;
import com.sunbreathing.client.sound.BreathingLoopSound;
import com.sunbreathing.item.NichirinBladeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Total Concentration presentation for every visible player:
 *  - a subtle sun-orange aura (sparse soft glow puffs hugging the body),
 *  - a short ember trail that only appears while moving,
 *  - heat shimmer rising off an ignited blade,
 *  - and, for the local player only, the breathing loop.
 */
public final class ConcentrationFx {
    private static BreathingLoopSound loop;
    private static int tickCounter;

    private ConcentrationFx() {}

    public static void onStateChanged(AbstractClientPlayer player, boolean active) {
        if (!active) return;
        // Ignition burst: ring of flame + embers rising around the feet.
        Vec3 base = player.position();
        int n = SunFx.count(18);
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            SunFx.flame(base.add(dir.scale(0.45)).add(0, 0.1, 0), dir.scale(0.06).add(0, 0.05, 0));
        }
        for (int i = 0; i < SunFx.count(12); i++) {
            SunFx.ember(base.add(SunFx.randomUnit().multiply(0.5, 0.2, 0.5)).add(0, 0.4, 0),
                    new Vec3(0, SunFx.rand(0.04, 0.09), 0));
        }

        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && (loop == null || loop.isStopped())) {
            loop = new BreathingLoopSound(mc.player);
            mc.getSoundManager().play(loop);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        tickCounter++;
        for (AbstractClientPlayer p : mc.level.players()) {
            float ignite = ClientConcentration.ignite(p.getId(), 1f);
            if (ignite <= 0.05f || p.isInvisible()) continue;
            boolean firstPersonSelf = p == mc.player && mc.options.getCameraType().isFirstPerson();
            aura(p, ignite, firstPersonSelf);
            emberTrail(p, ignite);
            bladeHeat(p, ignite, firstPersonSelf);
        }
    }

    private static void aura(AbstractClientPlayer p, float ignite, boolean firstPersonSelf) {
        // Keep it subtle (and out of your own face in first person).
        if (firstPersonSelf || tickCounter % 2 != 0) return;
        int n = SunFx.count(Math.round(2 * ignite));
        for (int i = 0; i < n; i++) {
            double h = SunFx.rand(0.1, p.getBbHeight() * 0.95);
            Vec3 off = SunFx.randomUnit().multiply(0.38, 0, 0.38);
            SunFx.glow(p.position().add(off).add(0, h, 0), SunFx.rand(0.35, 0.6));
        }
    }

    private static void emberTrail(AbstractClientPlayer p, float ignite) {
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed < 0.06) return;
        int n = SunFx.count(Math.round((float) (speed * 10 * ignite)));
        Vec3 back = new Vec3(-dx, 0, -dz).normalize();
        for (int i = 0; i < n; i++) {
            Vec3 pos = p.position().add(back.scale(SunFx.rand(0.2, 0.6))).add(0, SunFx.rand(0.2, 1.4), 0)
                    .add(SunFx.randomUnit().scale(0.15));
            SunFx.ember(pos, back.scale(0.02).add(0, SunFx.rand(0.01, 0.04), 0));
        }
    }

    private static void bladeHeat(AbstractClientPlayer p, float ignite, boolean firstPersonSelf) {
        if (!(p.getMainHandItem().getItem() instanceof NichirinBladeItem)) return;
        if (tickCounter % 3 != 0) return;
        Vec3 fwd = Vec3.directionFromRotation(0f, p.yBodyRot);
        Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
        // Rough position of a held blade: at the right hand, extending forward/up.
        Vec3 handle = p.position().add(0, p.getBbHeight() * 0.45, 0).add(right.scale(0.38));
        if (firstPersonSelf) handle = p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(right.scale(0.35)).add(0, -0.3, 0);
        for (int i = 0; i < SunFx.count(2); i++) {
            Vec3 along = handle.add(fwd.scale(SunFx.rand(0.2, 0.9))).add(0, SunFx.rand(0.0, 0.25), 0);
            SunFx.ember(along, new Vec3(0, 0.025 * ignite, 0));
        }
    }

    public static void reset() {
        if (loop != null) Minecraft.getInstance().getSoundManager().stop(loop);
        loop = null;
    }
}
