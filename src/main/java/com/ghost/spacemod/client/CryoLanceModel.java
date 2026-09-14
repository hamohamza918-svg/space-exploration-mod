package com.ghost.spacemod.client;

import com.ghost.spacemod.weapon.CryoLanceItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class CryoLanceModel extends GeoModel<CryoLanceItem> {
    @Override public Identifier getModelResource(GeoRenderState state) {
        return Identifier.of("spacemod", "cryo_lance");
    }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        return Identifier.of("spacemod", "textures/item/cryo_lance_animated.png");
    }
    @Override public Identifier getAnimationResource(CryoLanceItem item) {
        return Identifier.of("spacemod", "cryo_lance");
    }
}
