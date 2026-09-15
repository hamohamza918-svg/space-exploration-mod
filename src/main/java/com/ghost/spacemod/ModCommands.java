package com.ghost.spacemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

/** {@code /planet <moon|mars|europa|venus|earth>} — travel between dimensions. */
public final class ModCommands {

    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("planet")
                    .then(CommandManager.argument("dest", StringArgumentType.word())
                            .executes(ctx -> travel(ctx.getSource(), StringArgumentType.getString(ctx, "dest")))));
            dispatcher.register(CommandManager.literal("spacemod")
                    .then(CommandManager.literal("reload").executes(ctx -> {
                        com.ghost.spacemod.weapon.EffectConfig.load();
                        ctx.getSource().sendFeedback(() -> Text.literal("[Space Exploration] effect config reloaded."), true);
                        return 1;
                    })));
        });
        SpaceMod.LOGGER.info("[Space Exploration] commands registered");
    }

    private static int travel(ServerCommandSource src, String dest) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.literal("Only players can travel."));
            return 0;
        }
        RegistryKey<World> key = ModDimensions.byName(dest);
        if (key == null) {
            src.sendError(Text.literal("Unknown planet: " + dest + " (moon, mars, europa, venus, earth)"));
            return 0;
        }
        ServerWorld world = src.getServer().getWorld(key);
        if (world == null) {
            src.sendError(Text.literal("That world isn't loaded."));
            return 0;
        }
        double y = key == World.OVERWORLD ? world.getSpawnPos().getY() + 1 : 1.0;
        player.teleportTo(new TeleportTarget(world, new Vec3d(0.5, y, 0.5), Vec3d.ZERO,
                player.getYaw(), player.getPitch(), TeleportTarget.NO_OP));
        player.sendMessage(Text.literal("Traveling to " + dest + "..."), false);
        return 1;
    }
}
