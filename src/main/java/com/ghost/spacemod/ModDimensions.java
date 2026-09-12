package com.ghost.spacemod;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Registry-world keys for the planet dimensions + their real surface gravity. */
public final class ModDimensions {

    public static final RegistryKey<World> MOON = key("moon");
    public static final RegistryKey<World> MARS = key("mars");
    public static final RegistryKey<World> EUROPA = key("europa");
    public static final RegistryKey<World> VENUS = key("venus");

    private ModDimensions() {
    }

    private static RegistryKey<World> key(String name) {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(SpaceMod.MOD_ID, name));
    }

    public static boolean isSpace(RegistryKey<World> k) {
        return k == MOON || k == MARS || k == EUROPA || k == VENUS;
    }

    /** Real surface gravity relative to Earth. */
    public static double gravity(RegistryKey<World> k) {
        if (k == MOON) return 0.165;
        if (k == MARS) return 0.379;
        if (k == EUROPA) return 0.134;
        if (k == VENUS) return 0.904;
        return 1.0;
    }

    public static RegistryKey<World> byName(String name) {
        return switch (name.toLowerCase()) {
            case "moon" -> MOON;
            case "mars" -> MARS;
            case "europa" -> EUROPA;
            case "venus" -> VENUS;
            case "earth" -> World.OVERWORLD;
            default -> null;
        };
    }
}
