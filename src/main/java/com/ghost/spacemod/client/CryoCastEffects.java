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

/** Bounded, local GPU effects. World time keeps nearby viewers on the same timeline. */
public final class CryoCastEffects {
    private static final Identifier RUNE = Identifier.of("spacemod", "textures/effect/cryo_rune.png");
    private static final List<CryoCastPayload> ACTIVE = new ArrayList<>();
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
            if (age < 0 || age > p.duration() || camera.squaredDistanceTo(p.x(), p.y(), p.z()) > 64 * 64) continue;
            boolean ultimate = p.animation().equals("absolute_zero");
            boolean shatter = p.animation().equals("shatter");
            float fade = Math.min(1, age / 4) * Math.min(1, (p.duration() - age) / 8);
            float radius = ultimate ? 12 * Math.min(1, age / 13) : shatter ? 12 * (1 - age / p.duration()) :
                    p.animation().equals("nova") ? 1 + Math.min(6, age / 3) : 1.3f;
            var matrices = context.matrixStack();
            matrices.push();
            matrices.translate(p.x() - camera.x, p.y() - camera.y + 0.065, p.z() - camera.z);
            VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(RUNE));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 1.5f));
            disc(vertices, matrices.peek().getPositionMatrix(), radius, (int)(fade * 185));
            matrices.translate(0, 0.02, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-age * 3));
            disc(vertices, matrices.peek().getPositionMatrix(), radius * 0.62f, (int)(fade * 220));
            matrices.pop();
        }
    }
    private static void disc(VertexConsumer v, Matrix4f m, float r, int alpha) {
        vertex(v,m,-r,-r,0,0,alpha); vertex(v,m,-r,r,0,1,alpha);
        vertex(v,m,r,r,1,1,alpha); vertex(v,m,r,-r,1,0,alpha);
    }
    private static void vertex(VertexConsumer v, Matrix4f m, float x, float z, float u, float t, int alpha) {
        v.vertex(m,x,0,z).color(125,230,255,alpha).texture(u,t)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);
    }
}
