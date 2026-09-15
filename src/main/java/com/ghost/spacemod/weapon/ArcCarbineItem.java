package com.ghost.spacemod.weapon;

import com.ghost.spacemod.client.ArcCarbineModel;
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

/** Animated Arc Carbine: idle coil spin, fire recoil, ultimate charge. */
public final class ArcCarbineItem extends WeaponItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ArcCarbineItem(Settings settings) {
        super(settings, "arc_carbine");
        GeoItem.registerSyncedAnimatable(this);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ArcCarbineItem>("arc", 2,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("idle")))
                .triggerableAnim("fire", RawAnimation.begin().thenPlay("fire"))
                .triggerableAnim("ultimate", RawAnimation.begin().thenPlay("ultimate")));
    }

    @Override public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<ArcCarbineItem> renderer;
            @Override public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new ArcCarbineModel());
                return renderer;
            }
        });
    }

    /** Trigger a model animation + broadcast the client cast effect (storm circle). */
    public static void cast(ServerWorld world, ServerPlayerEntity player, ItemStack stack,
                            String modelAnim, String effect, Vec3d center, int duration) {
        if (stack.getItem() instanceof ArcCarbineItem item && player.getWorld() == world) {
            item.triggerAnim(player, GeoItem.getOrAssignId(stack, world), "arc", modelAnim);
        }
        if (effect != null) {
            CryoCastPayload packet = new CryoCastPayload(world.getRegistryKey().getValue(),
                    player.getId(), effect, center.x, center.y, center.z, world.getTime(), duration, "");
            for (ServerPlayerEntity viewer : world.getPlayers()) {
                if (viewer.squaredDistanceTo(center) <= 64 * 64 && ServerPlayNetworking.canSend(viewer, CryoCastPayload.ID)) {
                    ServerPlayNetworking.send(viewer, packet);
                }
            }
        }
    }
}
