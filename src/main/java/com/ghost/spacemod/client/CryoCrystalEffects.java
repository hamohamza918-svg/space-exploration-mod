package com.ghost.spacemod.client;

import com.ghost.spacemod.net.CryoSpikesPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** Faceted crystal meshes, grown locally and shattered into ballistic mesh fragments. */
public final class CryoCrystalEffects {
    // Sample the white palette tile already packaged with the lance, tint each face in code.
    private static final Identifier MATERIAL = Identifier.of("spacemod", "textures/item/cryo_lance_animated.png");
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final class Ring {
        final CryoSpikesPayload packet;
        long shatterTick = Long.MAX_VALUE;
        Ring(CryoSpikesPayload packet) { this.packet = packet; }
    }
    private CryoCrystalEffects() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CryoSpikesPayload.ID,(packet,context) -> context.client().execute(() -> {
            var world=context.client().world;
            if(world==null || !world.getRegistryKey().getValue().equals(packet.dimension())) return;
            if(packet.endTick()<packet.startTick() || packet.endTick()-packet.startTick()>240) return;
            for(var s:packet.spikes()) {
                if(!Double.isFinite(s.x()) || !Double.isFinite(s.y()) || !Double.isFinite(s.z()) ||
                        !Float.isFinite(s.height()) || !Float.isFinite(s.angle()) || s.height()<1 || s.height()>12) return;
            }
            if(RINGS.size()>=12) RINGS.remove(0); // 216 main crystals maximum across all casters
            RINGS.add(new Ring(packet));
        }));
    }
    public static void clear() { RINGS.clear(); }
    public static void tick(Identifier dimension,long now) {
        RINGS.removeIf(r -> !r.packet.dimension().equals(dimension) || now>Math.min(r.shatterTick,r.packet.endTick())+24);
    }
    public static void shatter(int caster,long tick) {
        for(Ring r:RINGS) if(r.packet.caster()==caster) r.shatterTick=Math.min(r.shatterTick,tick);
    }
    public static void render(WorldRenderContext c) {
        var camera=c.camera().getPos();
        var v=c.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(MATERIAL));
        Matrix4f m=c.matrixStack().peek().getPositionMatrix();
        double now=c.world().getTime()+c.tickCounter().getTickProgress(false);
        for(Ring ring:RINGS) {
            double age=now-ring.packet.startTick();
            if(age<0) continue;
            long breakTick=Math.min(ring.shatterTick,ring.packet.endTick());
            double broken=now-breakTick;
            float growth=ease((float)(Math.min(now,breakTick)-ring.packet.startTick())/9);
            for(var s:ring.packet.spikes()) {
                if(camera.squaredDistanceTo(s.x(),s.y(),s.z())>64*64) continue;
                float x=(float)(s.x()-camera.x), y=(float)(s.y()-camera.y), z=(float)(s.z()-camera.z);
                float h=s.height()*growth, width=0.3f+s.height()*0.045f;
                if(broken<0) {
                    crystal(v,m,x,y,z,h,width,s.angle(),255);
                    // Two small outward-facing splinters make each eruption a crystal cluster.
                    for(int k=0;k<2;k++) {
                        float a=s.angle()+(k==0?-.9f:.9f);
                        crystal(v,m,x+(float)Math.cos(a)*.4f,y,z+(float)Math.sin(a)*.4f,h*.42f,width*.55f,a,225);
                    }
                } else if(broken<24 && h>0.05f) {
                    float t=(float)broken/20, fade=1-(float)broken/24;
                    for(int k=0;k<4;k++) {
                        float a=s.angle()+k*1.7f, speed=1.4f+k*.6f;
                        crystal(v,m,x+(float)Math.cos(a)*t*speed,
                                y+h*(.15f+k*.18f)+t*(2+k*.4f)-3*t*t,
                                z+(float)Math.sin(a)*t*speed,
                                h*.22f*fade,width*.6f*fade,a+t*5,(int)(fade*240));
                    }
                }
            }
        }
    }
    private static float ease(float t) { t=Math.clamp(t,0,1); return 1-(1-t)*(1-t)*(1-t); }
    private static void crystal(VertexConsumer v,Matrix4f m,float x,float y,float z,float h,float w,float rotation,int alpha) {
        float leanX=(float)Math.cos(rotation)*h*.1f, leanZ=(float)Math.sin(rotation)*h*.1f;
        for(int i=0;i<6;i++) {
            double a=rotation+i*Math.PI/3,b=a+Math.PI/3;
            float ax=(float)Math.cos(a)*w, az=(float)Math.sin(a)*w;
            float bx=(float)Math.cos(b)*w, bz=(float)Math.sin(b)*w;
            int shade=i%3; int red=55+shade*40,green=130+shade*36;
            point(v,m,x+ax*.65f,y,z+az*.65f,red,green,235,alpha);
            point(v,m,x+bx*.65f,y,z+bz*.65f,red,green,235,alpha);
            point(v,m,x+bx+leanX*.45f,y+h*.57f,z+bz+leanZ*.45f,red+20,green+12,255,alpha);
            point(v,m,x+ax+leanX*.45f,y+h*.57f,z+az+leanZ*.45f,red+20,green+12,255,alpha);
            point(v,m,x+ax+leanX*.45f,y+h*.57f,z+az+leanZ*.45f,red+20,green+12,255,alpha);
            point(v,m,x+bx+leanX*.45f,y+h*.57f,z+bz+leanZ*.45f,red+20,green+12,255,alpha);
            point(v,m,x+leanX,y+h,z+leanZ,220,250,255,alpha);
            point(v,m,x+leanX,y+h,z+leanZ,220,250,255,alpha);
        }
    }
    static void point(VertexConsumer v,Matrix4f m,float x,float y,float z,int r,int g,int b,int a) {
        v.vertex(m,x,y,z).color(r,g,b,a).texture(.91f,.5f)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);
    }
}
