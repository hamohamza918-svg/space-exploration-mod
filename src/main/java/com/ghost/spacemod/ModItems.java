package com.ghost.spacemod;

import com.ghost.spacemod.weapon.WeaponItem;
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
    public static Item XYLITE_BLADE;
    public static Item ARC_CARBINE;
    public static Item CRYO_LANCE;
    public static Item RESONANCE_HAMMER;
    public static Item HELIOS_GLAIVE;
    public static Item BORER_DRILL;
    public static Item CORROSION_SPRAYER;
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

    /** Register a WeaponItem (right-click ability, sneak = secondary, melee on-hit). */
    public static Item registerWeapon(String name, String weaponId) {
        Identifier id = Identifier.of(SpaceMod.MOD_ID, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings().maxCount(1).registryKey(key);
        Item item = switch (weaponId) {
            case "cryo_lance" -> new com.ghost.spacemod.weapon.CryoLanceItem(settings);
            case "arc_carbine" -> new com.ghost.spacemod.weapon.ArcCarbineItem(settings);
            default -> new WeaponItem(settings, weaponId);
        };
        Registry.register(Registries.ITEM, key, item);
        ITEMS.add(item);
        return item;
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
        LASER_CUTTER = registerWeapon("laser_cutter", "laser_cutter");
        XYLITE_BLADE = registerWeapon("xylite_blade", "xylite_blade");
        ARC_CARBINE = registerWeapon("arc_carbine", "arc_carbine");
        CRYO_LANCE = registerWeapon("cryo_lance", "cryo_lance");
        RESONANCE_HAMMER = registerWeapon("resonance_hammer", "resonance_hammer");
        HELIOS_GLAIVE = registerWeapon("helios_glaive", "helios_glaive");
        BORER_DRILL = registerWeapon("borer_drill", "borer_drill");
        CORROSION_SPRAYER = registerWeapon("corrosion_sprayer", "corrosion_sprayer");
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
