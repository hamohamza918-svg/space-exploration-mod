package com.ghost.spacemod;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** From-scratch items (1.21.8 registry-key-in-settings pattern). */
public final class ModItems {

    public static final List<Item> ITEMS = new ArrayList<>();

    public static Item RAW_XYLITE;
    public static Item XYLITE_INGOT;
    public static Item RAW_TITANIUM;
    public static Item TITANIUM_INGOT;
    public static Item OXYGEN_TANK;
    public static Item GUIDANCE_CIRCUIT;
    public static Item HEAT_SHIELD_PLATE;
    public static Item ROCKET_FUEL;
    public static Item LAUNCH_PAD_CORE;
    public static Item LASER_CUTTER;
    public static Item SPACE_HELMET;
    public static Item SPACE_CHESTPLATE;
    public static Item SPACE_LEGGINGS;
    public static Item SPACE_BOOTS;
    public static Item THERMAL_HELMET;
    public static Item THERMAL_CHESTPLATE;
    public static Item THERMAL_LEGGINGS;
    public static Item THERMAL_BOOTS;

    private ModItems() {
    }

    public static Item register(String name, Item.Settings settings) {
        Identifier id = Identifier.of(SpaceMod.MOD_ID, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item item = new Item(settings.registryKey(key));
        Registry.register(Registries.ITEM, key, item);
        ITEMS.add(item);
        return item;
    }

    private static Item basic(String name) {
        return register(name, new Item.Settings());
    }

    public static void register() {
        RAW_XYLITE = basic("raw_xylite");
        XYLITE_INGOT = basic("xylite_ingot");
        RAW_TITANIUM = basic("raw_titanium");
        TITANIUM_INGOT = basic("titanium_ingot");
        OXYGEN_TANK = basic("oxygen_tank");
        GUIDANCE_CIRCUIT = basic("guidance_circuit");
        HEAT_SHIELD_PLATE = basic("heat_shield_plate");
        ROCKET_FUEL = basic("rocket_fuel");
        LAUNCH_PAD_CORE = basic("launch_pad_core");
        LASER_CUTTER = register("laser_cutter", new Item.Settings().maxCount(1));
        SPACE_HELMET = register("space_helmet", new Item.Settings().maxCount(1));
        SPACE_CHESTPLATE = register("space_chestplate", new Item.Settings().maxCount(1));
        SPACE_LEGGINGS = register("space_leggings", new Item.Settings().maxCount(1));
        SPACE_BOOTS = register("space_boots", new Item.Settings().maxCount(1));
        THERMAL_HELMET = register("thermal_helmet", new Item.Settings().maxCount(1));
        THERMAL_CHESTPLATE = register("thermal_chestplate", new Item.Settings().maxCount(1));
        THERMAL_LEGGINGS = register("thermal_leggings", new Item.Settings().maxCount(1));
        THERMAL_BOOTS = register("thermal_boots", new Item.Settings().maxCount(1));

        SpaceMod.LOGGER.info("[Space Exploration] items registered");
    }
}
