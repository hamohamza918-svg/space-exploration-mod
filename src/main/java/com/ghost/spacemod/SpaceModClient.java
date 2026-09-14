package com.ghost.spacemod;

import com.ghost.spacemod.net.UltimatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Client-only setup — entity renderers + the Ultimate keybind. */
public class SpaceModClient implements ClientModInitializer {

    private static KeyBinding ultimateKey;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.VOID_WALKER, ZombieEntityRenderer::new);

        ultimateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spacemod.ultimate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.category.spacemod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ultimateKey.wasPressed()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new UltimatePayload());
                }
            }
        });

        SpaceMod.LOGGER.info("[Space Exploration] client renderers + ultimate keybind registered");
    }
}
