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
        CryoCrystalEffects.register();
        ClientPlayNetworking.registerGlobalReceiver(CryoCastPayload.ID, (packet, context) ->
                context.client().execute(() -> {
                    var world = context.client().world;
                    if (world == null || !world.getRegistryKey().getValue().equals(packet.dimension())) return;
                    if (!List.of("stream", "nova", "absolute_zero", "shatter").contains(packet.animation())) return;
                    if (packet.duration() < 1 || packet.duration() > 240) return;
                    if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())) return;
                    if (packet.animation().equals("shatter")) CryoCrystalEffects.shatter(packet.caster(), packet.startTick());
                    ACTIVE.removeIf(old -> old.caster() == packet.caster() &&
                            (old.animation().equals(packet.animation()) || packet.animation().equals("shatter")));
                    if (ACTIVE.size() >= 24) ACTIVE.remove(0);
                    ACTIVE.add(packet);
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { ACTIVE.clear(); CryoCrystalEffects.clear(); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) { ACTIVE.clear(); CryoCrystalEffects.clear(); return; }
            CryoCrystalEffects.tick(client.world.getRegistryKey().getValue(), client.world.getTime());
            ACTIVE.removeIf(p -> !p.dimension().equals(client.world.getRegistryKey().getValue()) ||
                    client.world.getTime() - p.startTick() > p.duration());
        });
        WorldRenderEvents.AFTER_ENTITIES.register(CryoCastEffects::render);
    }
    private static void render(WorldRenderContext context) {
        if (context.matrixStack() == null || context.consumers() == null) return;
        CryoCrystalEffects.render(context);
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
            if (ultimate || shatter) {
                // Matching ceiling sigil: flip it so the face points down toward the caster.
                matrices.translate(0, 8.5, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 2));
                disc(vertices, matrices.peek().getPositionMatrix(), radius, (int)(fade * 200));
                matrices.translate(0, .02, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-age * 3));
                disc(vertices, matrices.peek().getPositionMatrix(), radius * .62f, (int)(fade * 220));
            } else {
                matrices.translate(0, 3, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                disc(vertices, matrices.peek().getPositionMatrix(), radius, (int)(fade * 180));
            }
            matrices.pop();
            if (ultimate || shatter) storm(context, p, age, fade, shatter);
        }
    }
    private static void storm(WorldRenderContext c, CryoCastPayload p, float age, float fade, boolean shatter) {
        var camera=c.camera().getPos();
        var matrices=c.matrixStack();
        matrices.push();
        matrices.translate(p.x()-camera.x,p.y()-camera.y,p.z()-camera.z);
        var m=matrices.peek().getPositionMatrix();
        var material=Identifier.of("spacemod","textures/item/cryo_lance_animated.png");
        var v=c.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(material));
        // A thin expanding frost shock front, drawn as a continuous mesh band.
        float front=shatter ? age*.95f : (age-13)*.7f;
        if(front>0 && front<19) {
            int alpha=(int)(Math.max(0,1-front/19)*210);
            band(v,m,front,.12f,.55f,alpha);
            band(v,m,front*.86f,.16f,.15f,alpha);
        }
        // Orbiting slivers connect the lower and upper circles without obscuring the player.
        for(int i=0;i<48;i++) {
            double a=i*Math.PI*2/48+age*.018;
            float h=(age*.075f+i*.47f)%8.5f;
            float r=shatter ? 3+age*.55f : 3+(float)Math.sin(i*2.3)*.7f;
            float x=(float)Math.cos(a)*r,z=(float)Math.sin(a)*r;
            int alpha=(int)(fade*150);
            CryoCrystalEffects.point(v,m,x-.035f,h,z,170,235,255,alpha);
            CryoCrystalEffects.point(v,m,x,h+.26f,z,220,250,255,alpha);
            CryoCrystalEffects.point(v,m,x+.035f,h,z,170,235,255,alpha);
            CryoCrystalEffects.point(v,m,x,h-.26f,z,100,200,255,alpha);
        }
        matrices.pop();
    }
    private static void band(VertexConsumer v,Matrix4f m,float r,float y,float height,int alpha) {
        for(int i=0;i<96;i++) {
            double a=i*Math.PI/48,b=(i+1)*Math.PI/48;
            float x=(float)Math.cos(a)*r,z=(float)Math.sin(a)*r;
            float bx=(float)Math.cos(b)*r,bz=(float)Math.sin(b)*r;
            CryoCrystalEffects.point(v,m,x,y,z,120,220,255,alpha);
            CryoCrystalEffects.point(v,m,bx,y,bz,120,220,255,alpha);
            CryoCrystalEffects.point(v,m,bx,y+height,bz,220,250,255,0);
            CryoCrystalEffects.point(v,m,x,y+height,z,220,250,255,0);
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
