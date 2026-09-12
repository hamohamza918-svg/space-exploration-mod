package com.ghost.spacemod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Space Exploration Fabric mod — genuinely custom, from-scratch content
 * (own blocks, items, mobs and dimensions) for Minecraft 1.21.8.
 */
public class SpaceMod implements ModInitializer {

    public static final String MOD_ID = "spacemod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.register();
        ModBlocks.register();
        ModEntities.register();
        ModItemGroups.register();
        SpaceEnvironment.register();
        ModCommands.register();
        LOGGER.info("[Space Exploration] initialized");
    }
}
