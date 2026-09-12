package com.ghost.spacemod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** From-scratch entities. */
public final class ModEntities {

    public static EntityType<VoidWalkerEntity> VOID_WALKER;

    private ModEntities() {
    }

    public static void register() {
        Identifier id = Identifier.of(SpaceMod.MOD_ID, "void_walker");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);
        VOID_WALKER = Registry.register(Registries.ENTITY_TYPE, key,
                EntityType.Builder.create(VoidWalkerEntity::new, SpawnGroup.MONSTER)
                        .dimensions(0.6f, 1.95f)
                        .build(key));
        FabricDefaultAttributeRegistry.register(VOID_WALKER, ZombieEntity.createZombieAttributes());
        SpaceMod.LOGGER.info("[Space Exploration] entities registered");
    }
}
