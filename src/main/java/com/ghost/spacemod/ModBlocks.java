package com.ghost.spacemod;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** From-scratch blocks (1.21.8 registry-key-in-settings pattern). */
public final class ModBlocks {

    /** All block items, for adding to the creative tab in order. */
    public static final List<Item> ITEMS = new ArrayList<>();

    public static Block XYLITE_ORE;
    public static Block DEEPSLATE_XYLITE_ORE;
    public static Block TITANIUM_ORE;
    public static Block DEEPSLATE_TITANIUM_ORE;
    public static Block MOON_STONE;
    public static Block MARS_STONE;
    public static Block EUROPA_ICE;
    public static Block VENUS_ROCK;
    public static Block FABRICATOR;

    private ModBlocks() {
    }

    private static Block register(String name, AbstractBlock.Settings settings) {
        return registerBlock(name, Block::new, settings);
    }

    private static Block registerBlock(String name, java.util.function.Function<AbstractBlock.Settings, Block> factory,
                                       AbstractBlock.Settings settings) {
        Identifier id = Identifier.of(SpaceMod.MOD_ID, name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = factory.apply(settings.registryKey(blockKey));
        Registry.register(Registries.BLOCK, blockKey, block);

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Item item = new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey());
        Registry.register(Registries.ITEM, itemKey, item);
        ITEMS.add(item);
        return block;
    }

    public static void register() {
        AbstractBlock.Settings ore = AbstractBlock.Settings.copy(Blocks.STONE).strength(3.0f, 3.0f).requiresTool();
        AbstractBlock.Settings deepOre = AbstractBlock.Settings.copy(Blocks.DEEPSLATE).strength(4.5f, 3.0f).requiresTool();

        XYLITE_ORE = register("xylite_ore", ore.sounds(BlockSoundGroup.AMETHYST_BLOCK).luminance(s -> 5));
        DEEPSLATE_XYLITE_ORE = register("deepslate_xylite_ore", deepOre.luminance(s -> 5));
        TITANIUM_ORE = register("titanium_ore", AbstractBlock.Settings.copy(Blocks.STONE).strength(3.0f, 3.0f).requiresTool());
        DEEPSLATE_TITANIUM_ORE = register("deepslate_titanium_ore", AbstractBlock.Settings.copy(Blocks.DEEPSLATE).strength(4.5f, 3.0f).requiresTool());
        MOON_STONE = register("moon_stone", AbstractBlock.Settings.copy(Blocks.STONE).strength(2.5f, 6.0f).requiresTool());
        MARS_STONE = register("mars_stone", AbstractBlock.Settings.copy(Blocks.TERRACOTTA).strength(2.5f, 6.0f).requiresTool());
        EUROPA_ICE = register("europa_ice", AbstractBlock.Settings.copy(Blocks.PACKED_ICE).strength(2.0f).requiresTool());
        VENUS_ROCK = register("venus_rock", AbstractBlock.Settings.copy(Blocks.BASALT).strength(3.0f, 6.0f).requiresTool());
        FABRICATOR = registerBlock("fabricator", FabricatorBlock::new,
                AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(4.0f).requiresTool().luminance(s -> 8));

        SpaceMod.LOGGER.info("[Space Exploration] blocks registered");
    }
}
