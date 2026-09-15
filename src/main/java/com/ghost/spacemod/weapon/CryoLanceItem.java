package com.ghost.spacemod.weapon;

import com.ghost.spacemod.client.CryoLanceModel;
import com.ghost.spacemod.net.CryoCastPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

/** Moving geometry; casts only start after the server accepts an ability. */
public final class CryoLanceItem extends WeaponItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public CryoLanceItem(Settings settings) {
        super(settings, "cryo_lance");
        GeoItem.registerSyncedAnimatable(this);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CryoLanceItem>("cast", 2,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("idle")))
                .triggerableAnim("stream", RawAnimation.begin().thenPlay("stream"))
                .triggerableAnim("nova", RawAnimation.begin().thenPlay("nova"))
                .triggerableAnim("absolute_zero", RawAnimation.begin().thenPlay("absolute_zero"))
                .triggerableAnim("shatter", RawAnimation.begin().thenPlay("shatter")));
    }
    @Override public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        // Anonymous provider keeps client renderer construction out of dedicated-server startup.
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<CryoLanceItem> renderer;
            @Override public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new CryoLanceModel());
                return renderer;
            }
        });
    }
    public static void cast(ServerWorld world, ServerPlayerEntity player, ItemStack stack,
                            String animation, Vec3d center, int duration) {
        if (stack.getItem() instanceof CryoLanceItem item && player.getWorld() == world) {
            item.triggerAnim(player, GeoItem.getOrAssignId(stack, world), "cast", animation);
        }
        String design = animation.equals("absolute_zero") ? EffectConfig.toJson() : "";
        CryoCastPayload packet = new CryoCastPayload(world.getRegistryKey().getValue(),
                player.getId(), animation, center.x, center.y, center.z, world.getTime(), duration, design);
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer.squaredDistanceTo(center) <= 64 * 64 && ServerPlayNetworking.canSend(viewer, CryoCastPayload.ID))
                ServerPlayNetworking.send(viewer, packet);
        }
    }
}
