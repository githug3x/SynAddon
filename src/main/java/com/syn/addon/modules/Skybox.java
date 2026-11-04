package com.syn.addon.modules;

import com.syn.addon.SynAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.CheckedRandom;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Port of the original Skybox module: renders the custom better_sky layers
 * (horizon, nebulas, stars and fog) over the vanilla sky.
 */
public class Skybox extends Module {
    private static final Identifier NEBULA_1 = Identifier.of("better_sky", "textures/sky/nebula_2.png");
    private static final Identifier NEBULA_2 = Identifier.of("better_sky", "textures/sky/nebula_3.png");
    private static final Identifier HORIZON = Identifier.of("better_sky", "textures/sky/nebula_1.png");
    private static final Identifier STARS = Identifier.of("better_sky", "textures/sky/stars.png");
    private static final Identifier FOG = Identifier.of("better_sky", "textures/sky/fog.png");

    private static final float FOG_COLOR_RED = 0.3061791f;
    private static final float FOG_COLOR_GREEN = 0.2449433f;
    private static final float FOG_COLOR_BLUE = 0.3061791f;
    private static final float FOG_DENSITY = 1.0f;
    private static final float BLINDNESS = 0.0f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<SettingColor> horizonColor = sgGeneral.add(new ColorSetting.Builder()
        .name("horizon")
        .description("Color of the horizon layer.")
        .defaultValue(new SettingColor(196, 79, 186))
        .build()
    );

    public final Setting<SettingColor> nebula1Color = sgGeneral.add(new ColorSetting.Builder()
        .name("nebula1")
        .description("Color of the first nebula layer.")
        .defaultValue(new SettingColor(196, 79, 186))
        .build()
    );

    public final Setting<SettingColor> nebula2Color = sgGeneral.add(new ColorSetting.Builder()
        .name("nebula2")
        .description("Color of the second nebula layer.")
        .defaultValue(new SettingColor(196, 79, 186))
        .build()
    );

    public final Setting<SettingColor> uvStars1Color = sgGeneral.add(new ColorSetting.Builder()
        .name("uv-stars1")
        .description("Color of the first textured star layer.")
        .defaultValue(new SettingColor(196, 79, 186))
        .build()
    );

    public final Setting<SettingColor> uvStars2Color = sgGeneral.add(new ColorSetting.Builder()
        .name("uv-stars2")
        .description("Color of the second textured star layer.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    public final Setting<SettingColor> stars1Color = sgGeneral.add(new ColorSetting.Builder()
        .name("stars1")
        .description("Color of the first star layer.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    public final Setting<SettingColor> stars2Color = sgGeneral.add(new ColorSetting.Builder()
        .name("stars2")
        .description("Color of the second star layer.")
        .defaultValue(new SettingColor(242, 163, 237))
        .build()
    );

    public final Setting<Boolean> stars = sgGeneral.add(new BoolSetting.Builder()
        .name("stars")
        .description("Renders the star layers.")
        .defaultValue(true)
        .build()
    );

    private final SkyLayer horizon;
    private final SkyLayer nebula1;
    private final SkyLayer nebula2;
    private final SkyLayer stars1;
    private final SkyLayer stars2;
    private final SkyLayer stars3;
    private final SkyLayer stars4;
    private final SkyLayer fog;

    private final Vector3f axis1;
    private final Vector3f axis2;
    private final Vector3f axis3;
    private final Vector3f axis4;

    public Skybox() {
        super(SynAddon.CATEGORY, "Skybox", "Custom skybox.");

        horizon = SkyLayer.cylinder(16, 50.0f, 100.0f);
        fog = SkyLayer.cylinder(16, 50.0f, 70.0f);

        nebula1 = SkyLayer.farFog(40.0f, 60.0f, 30, 11515L);
        nebula2 = SkyLayer.farFog(40.0f, 60.0f, 10, 14151L);

        stars1 = SkyLayer.stars(0.1f, 0.3f, 3500, 41315L);
        stars2 = SkyLayer.stars(0.1f, 0.35f, 2000, 35151L);
        stars3 = SkyLayer.uvStars(0.4f, 1.2f, 1000, 61354L);
        stars4 = SkyLayer.uvStars(0.4f, 1.2f, 1000, 61355L);

        CheckedRandom random = new CheckedRandom(131L);
        axis1 = new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()).normalize();
        axis2 = new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()).normalize();
        axis3 = new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()).normalize();
        axis4 = new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()).normalize();

        WorldRenderEvents.BEFORE_ENTITIES.register(this::onRender);
    }

    private void onRender(WorldRenderContext context) {
        if (!isActive() || mc.world == null || mc.player == null) return;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        float time = ((float) mc.world.getTimeOfDay() + tickDelta) % 360000.0f * 1.7453292E-5f;
        float time2 = time * 2.0f;
        float time3 = time * 3.0f;

        float visible = 1.0f - BLINDNESS;
        float nebulaAlpha = visible * 0.2f;
        float starAlpha = visible * 0.6f;

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        Quaternionf rotation = new Quaternionf();

        if (visible > 0.0f) {
            horizon.render(matrices, consumers, HORIZON, horizonColor.get(),
                rotation.rotationXYZ(0.0f, time, 0.0f), 0.7f * visible);
            nebula1.render(matrices, consumers, NEBULA_1, nebula1Color.get(),
                rotation.rotationXYZ(0.0f, -time, 0.0f), nebulaAlpha);
            nebula2.render(matrices, consumers, NEBULA_2, nebula2Color.get(),
                rotation.rotationXYZ(0.0f, time2, 0.0f), nebulaAlpha);

            if (stars.get()) {
                stars3.render(matrices, consumers, STARS, uvStars1Color.get(),
                    rotation.setAngleAxis(time, axis3.x, axis3.y, axis3.z), starAlpha);
                stars4.render(matrices, consumers, STARS, uvStars2Color.get(),
                    rotation.setAngleAxis(time2, axis4.x, axis4.y, axis4.z), starAlpha);
            }
        }

        float fogAlpha = FOG_DENSITY - 1.0f;
        if (fogAlpha > 1.0f) fogAlpha = 1.0f;
        fog.render(matrices, consumers, FOG, FOG_COLOR_RED, FOG_COLOR_GREEN, FOG_COLOR_BLUE, fogAlpha);

        if (visible > 0.0f && stars.get()) {
            stars1.renderFlat(matrices, consumers, stars1Color.get(),
                rotation.setAngleAxis(time3, axis1.x, axis1.y, axis1.z), starAlpha);
            stars2.renderFlat(matrices, consumers, stars2Color.get(),
                rotation.setAngleAxis(time2, axis2.x, axis2.y, axis2.z), starAlpha);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
    }

    private static final class SkyLayer {
        private final float[] vertices;
        private final boolean textured;

        private SkyLayer(float[] vertices, boolean textured) {
            this.vertices = vertices;
            this.textured = textured;
        }

        private void render(MatrixStack matrices, VertexConsumerProvider consumers, Identifier texture,
                            SettingColor color, Quaternionf rotation, float alpha) {
            render(matrices, consumers, texture,
                color.r / 255.0f, color.g / 255.0f, color.b / 255.0f, alpha, rotation);
        }

        private void render(MatrixStack matrices, VertexConsumerProvider consumers, Identifier texture,
                            float red, float green, float blue, float alpha) {
            render(matrices, consumers, texture, red, green, blue, alpha, null);
        }

        private void render(MatrixStack matrices, VertexConsumerProvider consumers, Identifier texture,
                            float red, float green, float blue, float alpha, Quaternionf rotation) {
            matrices.push();
            if (rotation != null) matrices.multiply(rotation);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            VertexConsumer buffer = consumers.getBuffer(RenderLayers.entityTranslucent(texture));

            for (int i = 0; i < vertices.length; i += 5) {
                buffer.vertex(matrix, vertices[i], vertices[i + 1], vertices[i + 2])
                    .color(red, green, blue, alpha)
                    .texture(vertices[i + 3], vertices[i + 4])
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(0.0f, 1.0f, 0.0f);
            }

            matrices.pop();
        }

        private void renderFlat(MatrixStack matrices, VertexConsumerProvider consumers, SettingColor color,
                                Quaternionf rotation, float alpha) {
            matrices.push();
            matrices.multiply(rotation);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            VertexConsumer buffer = consumers.getBuffer(RenderLayers.debugQuads());
            float red = color.r / 255.0f;
            float green = color.g / 255.0f;
            float blue = color.b / 255.0f;

            for (int i = 0; i < vertices.length; i += 3) {
                buffer.vertex(matrix, vertices[i], vertices[i + 1], vertices[i + 2])
                    .color(red, green, blue, alpha);
            }

            matrices.pop();
        }

        private static SkyLayer cylinder(int segments, float height, float radius) {
            float[] data = new float[segments * 4 * 5];
            int index = 0;

            for (int i = 0; i < segments; i++) {
                float angle = (float) i * (float) Math.PI * 2.0f / (float) segments;
                float nextAngle = (float) (i + 1) * (float) Math.PI * 2.0f / (float) segments;
                float x = (float) Math.sin(angle) * radius;
                float z = (float) Math.cos(angle) * radius;
                float nextX = (float) Math.sin(nextAngle) * radius;
                float nextZ = (float) Math.cos(nextAngle) * radius;
                float u = (float) i / (float) segments;
                float nextU = (float) (i + 1) / (float) segments;

                index = put(data, index, x, -height, z, u, 0.0f);
                index = put(data, index, x, height, z, u, 1.0f);
                index = put(data, index, nextX, height, nextZ, nextU, 1.0f);
                index = put(data, index, nextX, -height, nextZ, nextU, 0.0f);
            }

            return new SkyLayer(data, true);
        }

        private static SkyLayer farFog(float minSize, float maxSize, int count, long seed) {
            CheckedRandom random = new CheckedRandom(seed);
            float[] data = new float[count * 4 * 5];
            int index = 0;

            for (int i = 0; i < count; i++) {
                float x = random.nextFloat() * 2.0f - 1.0f;
                float y = random.nextFloat() - 0.5f;
                float z = random.nextFloat() * 2.0f - 1.0f;
                float size = randRange(minSize, maxSize, random);

                float length = x * x + y * y + z * z;
                if (!(length < 1.0f) || !(length > 0.001f)) continue;

                length = 2.0f / (float) Math.sqrt(length);
                size *= 2.0f;

                x *= length;
                y *= length;
                z *= length;

                float px = x * 100.0f;
                float py = y * 100.0f;
                float pz = z * 100.0f;

                float angle = (float) Math.atan2(x, z);
                float sinAngle = (float) Math.sin(angle);
                float cosAngle = (float) Math.cos(angle);
                angle = (float) Math.atan2((float) Math.sqrt(x * x + z * z), y);
                float sinPitch = (float) Math.sin(angle);
                float cosPitch = (float) Math.cos(angle);
                angle = random.nextFloat() * (float) Math.PI * 2.0f;
                float sinRoll = (float) Math.sin(angle);
                float cosRoll = (float) Math.cos(angle);

                for (int j = 0; j < 4; j++) {
                    float ax = (float) ((j & 2) - 1) * size;
                    float ay = (float) (((j + 1) & 2) - 1) * size;
                    float bx = ax * cosRoll - ay * sinRoll;
                    float by = ay * cosRoll + ax * sinRoll;
                    float cx = bx * sinPitch;
                    float cy = -bx * cosPitch;
                    float dx = cy * sinAngle - by * cosAngle;
                    float dz = by * sinAngle + cy * cosAngle;
                    float u = (float) ((j >> 1) & 1);
                    float v = (float) (((j + 1) >> 1) & 1);

                    index = put(data, index, px + dx, py + cx, pz + dz, u, v);
                }
            }

            return new SkyLayer(data, true);
        }

        private static SkyLayer uvStars(float minSize, float maxSize, int count, long seed) {
            CheckedRandom random = new CheckedRandom(seed);
            float[] data = new float[count * 4 * 5];
            int index = 0;

            for (int i = 0; i < count; i++) {
                float x = random.nextFloat() * 2.0f - 1.0f;
                float y = random.nextFloat() * 2.0f - 1.0f;
                float z = random.nextFloat() * 2.0f - 1.0f;
                float size = randRange(minSize, maxSize, random);

                float length = x * x + y * y + z * z;
                if (!(length < 1.0f) || !(length > 0.001f)) continue;

                length = 1.0f / (float) Math.sqrt(length);
                x *= length;
                y *= length;
                z *= length;

                float px = x * 100.0f;
                float py = y * 100.0f;
                float pz = z * 100.0f;

                float angle = (float) Math.atan2(x, z);
                float sinAngle = (float) Math.sin(angle);
                float cosAngle = (float) Math.cos(angle);
                angle = (float) Math.atan2((float) Math.sqrt(x * x + z * z), y);
                float sinPitch = (float) Math.sin(angle);
                float cosPitch = (float) Math.cos(angle);
                angle = random.nextFloat() * (float) Math.PI * 2.0f;
                float sinRoll = (float) Math.sin(angle);
                float cosRoll = (float) Math.cos(angle);
                float vOffset = (float) random.nextInt(4) / 4.0f;

                for (int j = 0; j < 4; j++) {
                    float ax = (float) ((j & 2) - 1) * size;
                    float ay = (float) (((j + 1) & 2) - 1) * size;
                    float bx = ax * cosRoll - ay * sinRoll;
                    float by = ay * cosRoll + ax * sinRoll;
                    float cx = bx * sinPitch;
                    float cy = -bx * cosPitch;
                    float dx = cy * sinAngle - by * cosAngle;
                    float dz = by * sinAngle + cy * cosAngle;
                    float u = (float) ((j >> 1) & 1);
                    float v = (float) (((j + 1) >> 1) & 1) / 4.0f + vOffset;

                    index = put(data, index, px + dx, py + cx, pz + dz, u, v);
                }
            }

            return new SkyLayer(data, true);
        }

        private static SkyLayer stars(float minSize, float maxSize, int count, long seed) {
            CheckedRandom random = new CheckedRandom(seed);
            float[] data = new float[count * 4 * 3];
            int index = 0;

            for (int i = 0; i < count; i++) {
                float x = random.nextFloat() * 2.0f - 1.0f;
                float y = random.nextFloat() * 2.0f - 1.0f;
                float z = random.nextFloat() * 2.0f - 1.0f;
                float size = randRange(minSize, maxSize, random);

                float length = x * x + y * y + z * z;
                if (!(length < 1.0f) || !(length > 0.001f)) continue;

                length = 1.0f / (float) Math.sqrt(length);
                x *= length;
                y *= length;
                z *= length;

                float px = x * 100.0f;
                float py = y * 100.0f;
                float pz = z * 100.0f;

                float angle = (float) Math.atan2(x, z);
                float sinAngle = (float) Math.sin(angle);
                float cosAngle = (float) Math.cos(angle);
                angle = (float) Math.atan2((float) Math.sqrt(x * x + z * z), y);
                float sinPitch = (float) Math.sin(angle);
                float cosPitch = (float) Math.cos(angle);
                angle = random.nextFloat() * (float) Math.PI * 2.0f;
                float sinRoll = (float) Math.sin(angle);
                float cosRoll = (float) Math.cos(angle);

                for (int j = 0; j < 4; j++) {
                    float ax = (float) ((j & 2) - 1) * size;
                    float ay = (float) (((j + 1) & 2) - 1) * size;
                    float bx = ax * cosRoll - ay * sinRoll;
                    float by = ay * cosRoll + ax * sinRoll;
                    float cx = bx * sinPitch;
                    float cy = -bx * cosPitch;
                    float dx = cy * sinAngle - by * cosAngle;
                    float dz = by * sinAngle + cy * cosAngle;

                    index = putFlat(data, index, px + dx, py + cx, pz + dz);
                }
            }

            return new SkyLayer(data, false);
        }

        private static float randRange(float min, float max, CheckedRandom random) {
            return min + random.nextFloat() * (max - min);
        }

        private static int put(float[] data, int index, float x, float y, float z, float u, float v) {
            data[index] = x;
            data[index + 1] = y;
            data[index + 2] = z;
            data[index + 3] = u;
            data[index + 4] = v;
            return index + 5;
        }

        private static int putFlat(float[] data, int index, float x, float y, float z) {
            data[index] = x;
            data[index + 1] = y;
            data[index + 2] = z;
            return index + 3;
        }
    }
}
