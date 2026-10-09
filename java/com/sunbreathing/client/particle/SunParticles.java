package com.sunbreathing.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * All Sun Breathing particle classes. They share three traits:
 *  - additive blending ({@link SunRenderTypes#ADDITIVE}) so overlaps glow,
 *  - full-bright lighting (fire emits light; it shouldn't darken in caves),
 *  - colour sampled from {@link SunPalette} as the particle "cools" over its life.
 *
 * Velocity arguments are used as-is (the vanilla 7-arg Particle constructor randomises them,
 * which would make slash-following sparks impossible to aim). For FLASH and GLOW the x velocity
 * argument is reinterpreted as SIZE; see the spawn helpers in SunFx.
 */
public final class SunParticles {
    private SunParticles() {}

    private static final int FULL_BRIGHT = 0xF000F0;

    abstract static class Base extends TextureSheetParticle {
        protected final SpriteSet sprites;
        protected final float[] rgb = new float[3];
        protected float heatOffset;

        Base(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.sprites = sprites;
            this.hasPhysics = false;
            this.heatOffset = random.nextFloat() * 0.15f;
        }

        protected float life() {
            return Mth.clamp(age / (float) lifetime, 0f, 1f);
        }

        protected void applyHeat(float t) {
            SunPalette.ramp(t, rgb);
            setColor(rgb[0], rgb[1], rgb[2]);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return SunRenderTypes.ADDITIVE;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }
    }

    // ------------------------------------------------------------------ flame
    public static final class Flame extends Base {
        private final float baseSize;

        Flame(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz, sprites);
            this.lifetime = 9 + random.nextInt(8);
            this.baseSize = 0.18f + random.nextFloat() * 0.22f;
            this.quadSize = baseSize;
            this.friction = 0.86f;
            this.gravity = -0.025f; // flames rise
            this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
            setSpriteFromAge(sprites);
            applyHeat(heatOffset);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            float l = life();
            applyHeat(heatOffset + l * 0.95f);
            this.alpha = l < 0.55f ? 1f : 1f - (l - 0.55f) / 0.45f;
            this.oRoll = this.roll;
            this.roll += 0.06f;
        }

        @Override
        public float getQuadSize(float partial) {
            float l = Mth.clamp((age + partial) / lifetime, 0f, 1f);
            // swell quickly, then shrink as it burns out
            return baseSize * (0.6f + 0.9f * Mth.sin(Mth.PI * Math.min(1f, l * 1.3f)));
        }
    }

    // ------------------------------------------------------------------ ember
    public static final class Ember extends Base {
        private final float wobblePhase;

        Ember(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz, sprites);
            this.lifetime = 18 + random.nextInt(24);
            this.quadSize = 0.035f + random.nextFloat() * 0.035f;
            this.friction = 0.95f;
            this.gravity = -0.012f;
            this.wobblePhase = random.nextFloat() * Mth.TWO_PI;
            pickSprite(sprites);
            applyHeat(0.15f + heatOffset);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            float l = life();
            this.xd += Mth.sin(age * 0.45f + wobblePhase) * 0.0025;
            this.zd += Mth.cos(age * 0.37f + wobblePhase) * 0.0025;
            applyHeat(0.15f + heatOffset + l * 0.8f);
            float flicker = 0.75f + 0.25f * Mth.sin(age * 1.7f + wobblePhase);
            this.alpha = (1f - l * l) * flicker;
        }
    }

    // ------------------------------------------------------------------ flash
    /** Big radial light burst at impact points. vx = size. */
    public static final class Flash extends Base {
        private final float size;

        Flash(ClientLevel level, double x, double y, double z, double size, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites);
            this.lifetime = 5;
            this.size = (float) Math.max(0.3, size);
            this.gravity = 0f;
            pickSprite(sprites);
            applyHeat(0f);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            float l = life();
            applyHeat(l * 0.45f);
            this.alpha = 1f - l;
        }

        @Override
        public float getQuadSize(float partial) {
            float l = Mth.clamp((age + partial) / lifetime, 0f, 1f);
            return size * (0.55f + 0.45f * (1f - (1f - l) * (1f - l)));
        }
    }

    // ------------------------------------------------------------------ glow
    /** Soft, faint aura puff (Total Concentration). vx = size; drifts gently upward. */
    public static final class Glow extends Base {
        private final float size;

        Glow(ClientLevel level, double x, double y, double z, double size, SpriteSet sprites) {
            super(level, x, y, z, 0, 0.012, 0, sprites);
            this.lifetime = 10 + random.nextInt(6);
            this.size = (float) Math.max(0.2, size);
            this.gravity = -0.004f;
            this.friction = 0.9f;
            pickSprite(sprites);
            applyHeat(0.35f + heatOffset);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            float l = life();
            applyHeat(0.35f + heatOffset + l * 0.4f);
            this.alpha = 0.22f * Mth.sin(Mth.PI * l);
        }

        @Override
        public float getQuadSize(float partial) {
            return size;
        }
    }

    // ------------------------------------------------------------------ light ray
    /**
     * A thin streak stretched along its direction of travel and turned to face the camera
     * (a "velocity-aligned billboard"). Used for impact rays and slash sparks.
     */
    public static final class Ray extends Base {
        private final Vec3 dir;
        private final float length;
        private final float width;

        Ray(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz, sprites);
            Vec3 v = new Vec3(vx, vy, vz);
            double speed = v.length();
            this.dir = speed < 1.0e-4 ? new Vec3(0, 1, 0) : v.scale(1.0 / speed);
            this.length = (float) Mth.clamp(speed * 2.2, 0.25, 2.2);
            this.width = 0.035f + random.nextFloat() * 0.03f;
            this.lifetime = 5 + random.nextInt(4);
            this.friction = 0.7f;
            this.gravity = 0f;
            pickSprite(sprites);
            applyHeat(0f);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            float l = life();
            applyHeat(l * 0.6f);
            this.alpha = 1f - l * l;
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partial) {
            Vec3 cam = camera.getPosition();
            float px = (float) (Mth.lerp(partial, xo, x) - cam.x);
            float py = (float) (Mth.lerp(partial, yo, y) - cam.y);
            float pz = (float) (Mth.lerp(partial, zo, z) - cam.z);
            float l = Mth.clamp((age + partial) / lifetime, 0f, 1f);
            float len = length * (1f - l * 0.6f);

            Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
            Vector3f toCam = new Vector3f(-px, -py, -pz);
            Vector3f side = new Vector3f(d).cross(toCam);
            if (side.lengthSquared() < 1.0e-6f) side.set(0, 1, 0);
            side.normalize().mul(width);

            // Streak runs from the particle position backwards along its direction (a trailing tail).
            float hx = px, hy = py, hz = pz;
            float tx = px - d.x * len, ty = py - d.y * len, tz = pz - d.z * len;

            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int light = getLightColor(partial);
            buffer.vertex(tx - side.x, ty - side.y, tz - side.z).uv(u1, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(tx + side.x, ty + side.y, tz + side.z).uv(u1, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(hx + side.x, hy + side.y, hz + side.z).uv(u0, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(hx - side.x, hy - side.y, hz - side.z).uv(u0, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }
    }

    // ------------------------------------------------------------------ providers
    public record FlameProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new Flame(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record EmberProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new Ember(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    public record FlashProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z,
                                       double size, double unused1, double unused2) {
            return new Flash(level, x, y, z, size, sprites);
        }
    }

    public record GlowProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z,
                                       double size, double unused1, double unused2) {
            return new Glow(level, x, y, z, size, sprites);
        }
    }

    public record RayProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new Ray(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
