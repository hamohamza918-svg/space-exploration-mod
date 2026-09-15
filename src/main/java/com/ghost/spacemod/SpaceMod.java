package com.ghost.spacemod;

import com.ghost.spacemod.net.UltimatePayload;
import com.ghost.spacemod.weapon.ServerScheduler;
import com.ghost.spacemod.weapon.Ultimates;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        ServerScheduler.init();
        com.ghost.spacemod.weapon.EffectConfig.load();

        PayloadTypeRegistry.playS2C().register(com.ghost.spacemod.net.CryoCastPayload.ID,
                com.ghost.spacemod.net.CryoCastPayload.CODEC);

        // Ultimate keybind networking (client sends, server executes)
        PayloadTypeRegistry.playC2S().register(UltimatePayload.ID, UltimatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UltimatePayload.ID, (payload, context) -> {
            var player = context.player();
            player.getServer().execute(() -> Ultimates.fire(player));
        });

        LOGGER.info("[Space Exploration] initialized");
    }
}
