package com.ghost.spacemod.client;

import com.ghost.spacemod.net.CryoCastPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** Bounded, local GPU effects for the Cryo Lance casts. World time keeps nearby viewers in sync. */
public final class CryoCastEffects {
    private static final Identifier RUNE = Identifier.of("spacemod", "textures/effect/cryo_rune.png");
    private static final List<CryoCastPayload> ACTIVE = new ArrayList<>();
    private static final int[] RING_RADII = {4, 7, 10, 12};
    private static final float[] RING_HEIGHT = {3.0f, 4.5f, 6.0f, 7.5f};

    private CryoCastEffects() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CryoCastPayload.ID, (packet, context) ->
                context.client().execute(() -> {
                    var world = context.client().world;
                    if (world == null || !world.getRegistryKey().getValue().equals(packet.dimension())) return;
                    if (!List.of("stream", "nova", "absolute_zero", "shatter").contains(packet.animation())) return;
                    if (packet.duration() < 1 || packet.duration() > 240) return;
                    if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())) return;
                    ACTIVE.removeIf(old -> old.caster() == packet.caster() &&
                            (old.animation().equals(packet.animation()) || packet.animation().equals("shatter")));
                    if (ACTIVE.size() >= 24) ACTIVE.remove(0);
                    ACTIVE.add(packet);
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) { ACTIVE.clear(); return; }
            ACTIVE.removeIf(p -> !p.dimension().equals(client.world.getRegistryKey().getValue()) ||
                    client.world.getTime() - p.startTick() > p.duration());
        });
        WorldRenderEvents.AFTER_ENTITIES.register(CryoCastEffects::render);
    }

    private static void render(WorldRenderContext context) {
        if (context.matrixStack() == null || context.consumers() == null) return;
        Vec3d camera = context.camera().getPos();
        for (CryoCastPayload p : ACTIVE) {
            float age = context.world().getTime() - p.startTick() + context.tickCounter().getTickProgress(false);
            if (age < 0 || age > p.duration() || camera.squaredDistanceTo(p.x(), p.y(), p.z()) > 80 * 80) continue;
            boolean ultimate = p.animation().equals("absolute_zero");
            boolean shatter = p.animation().equals("shatter");
            float fade = Math.min(1, age / 4) * Math.min(1, (p.duration() - age) / 8);
            float radius = ultimate ? 12 * Math.min(1, age / 13) : shatter ? 12 * (1 - age / p.duration()) :
                    p.animation().equals("nova") ? 1 + Math.min(6, age / 3) : 1.3f;

            var matrices = context.matrixStack();
            matrices.push();
            matrices.translate(p.x() - camera.x, p.y() - camera.y, p.z() - camera.z);
            VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(RUNE));

            // ground circle (under the caster)
            discPair(matrices, vertices, 0.065f, radius, age, fade);
            // mirrored circle overhead
            discPair(matrices, vertices, 2.45f, radius, age, fade * 0.85f);

            // crystalline ice spikes for the ultimate (replaces vanilla ice blocks)
            if (ultimate) {
                Matrix4f mat = matrices.peek().getPositionMatrix();
                for (int ri = 0; ri < RING_RADII.length; ri++) {
                    float ringStart = 22 + ri * 6f;
                    if (age < ringStart) continue;
                    float grow = Math.min(1f, (age - ringStart) / 10f);
                    float rr = RING_RADII[ri];
                    for (int s = 0; s < 9; s++) {
                        double a = Math.PI * 2 * s / 9 + ri * 0.3;
                        float ox = (float) (Math.cos(a) * rr);
                        float oz = (float) (Math.sin(a) * rr);
                        float h = RING_HEIGHT[ri] * grow * (0.8f + 0.4f * ((s + ri) % 3) / 2f);
                        float w = 0.28f + ri * 0.03f;
                        int alpha = (int) (fade * 170);
                        crystal(vertices, mat, ox, oz, w, h, alpha, a);
                    }
                }
            }
            matrices.pop();
        }
    }

    /** Two counter-rotating rune discs at the given height offset. */
    private static void discPair(net.minecraft.client.util.math.MatrixStack matrices, VertexConsumer v,
                                 float yOff, float radius, float age, float fade) {
        matrices.push();
        matrices.translate(0, yOff, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 1.5f));
        disc(v, matrices.peek().getPositionMatrix(), radius, (int) (fade * 185));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-age * 4.5f));
        disc(v, matrices.peek().getPositionMatrix(), radius * 0.62f, (int) (fade * 220));
        matrices.pop();
    }

    private static void disc(VertexConsumer v, Matrix4f m, float r, int alpha) {
        flat(v, m, -r, -r, 0, 0, alpha); flat(v, m, -r, r, 0, 1, alpha);
        flat(v, m, r, r, 1, 1, alpha); flat(v, m, r, -r, 1, 0, alpha);
    }

    private static void flat(VertexConsumer v, Matrix4f m, float x, float z, float u, float t, int alpha) {
        v.vertex(m, x, 0, z).color(125, 230, 255, alpha).texture(u, t)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);
    }

    /** A tapered 4-sided crystal spire (base square -> apex point), translucent + glowing. */
    private static void crystal(VertexConsumer v, Matrix4f m, float ox, float oz, float w, float h, int alpha, double spin) {
        float ax = ox, ay = h, az = oz;                 // apex
        float c = (float) Math.cos(spin) * w, s = (float) Math.sin(spin) * w;
        // four base corners rotated slightly by spin for varied facets
        float[][] base = {
                {ox - c - s, oz - s + c}, {ox + c - s, oz + s + c},
                {ox + c + s, oz + s - c}, {ox - c + s, oz - s - c}
        };
        int tip = 235; // brighter tip
        for (int i = 0; i < 4; i++) {
            float[] b1 = base[i], b2 = base[(i + 1) % 4];
            // tapered quad: two base corners + apex (apex duplicated to collapse top edge)
            face(v, m, b1[0], 0, b1[1], 0, 1, alpha);
            face(v, m, b2[0], 0, b2[1], 1, 1, alpha);
            face(v, m, ax, ay, az, 1, 0, tip);
            face(v, m, ax, ay, az, 0, 0, tip);
        }
    }

    private static void face(VertexConsumer v, Matrix4f m, float x, float y, float z, float u, float t, int alpha) {
        v.vertex(m, x, y, z).color(150, 235, 255, alpha).texture(u, t)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);
    }
}
