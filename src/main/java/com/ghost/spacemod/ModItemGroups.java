package com.ghost.spacemod;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The mod's own creative tab. */
public final class ModItemGroups {

    public static final RegistryKey<ItemGroup> SPACE =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(SpaceMod.MOD_ID, "space"));

    private ModItemGroups() {
    }

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, SPACE, FabricItemGroup.builder()
                .icon(() -> new ItemStack(ModItems.XYLITE_INGOT))
                .displayName(Text.translatable("itemgroup.spacemod.space"))
                .entries((context, entries) -> {
                    for (Item item : ModItems.ITEMS) {
                        entries.add(item);
                    }
                    for (Item item : ModBlocks.ITEMS) {
                        entries.add(item);
                    }
                })
                .build());
        SpaceMod.LOGGER.info("[Space Exploration] creative tab registered");
    }
}
