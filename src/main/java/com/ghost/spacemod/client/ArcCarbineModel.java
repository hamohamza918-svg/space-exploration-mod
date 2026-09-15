package com.ghost.spacemod.client;

import com.ghost.spacemod.weapon.ArcCarbineItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class ArcCarbineModel extends GeoModel<ArcCarbineItem> {
    @Override public Identifier getModelResource(GeoRenderState state) {
        return Identifier.of("spacemod", "arc_carbine");
    }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        return Identifier.of("spacemod", "textures/item/arc_carbine_animated.png");
    }
    @Override public Identifier getAnimationResource(ArcCarbineItem item) {
        return Identifier.of("spacemod", "arc_carbine");
    }
}
