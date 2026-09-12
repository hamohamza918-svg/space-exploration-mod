package com.ghost.spacemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.ZombieEntityRenderer;

/** Client-only setup — entity renderers etc. */
public class SpaceModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Reuse the zombie renderer/model for the Void Walker for now.
        EntityRendererRegistry.register(ModEntities.VOID_WALKER, ZombieEntityRenderer::new);
        SpaceMod.LOGGER.info("[Space Exploration] client renderers registered");
    }
}
