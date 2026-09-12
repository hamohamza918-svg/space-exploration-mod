package com.ghost.spacemod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-tick oxygen, real-gravity and hazard systems for the planet dimensions. */
public final class SpaceEnvironment {

    private static final Map<UUID, Integer> OXYGEN = new HashMap<>();
    private static int counter = 0;

    private SpaceEnvironment() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            counter++;
            if (counter % 20 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                RegistryKey<World> dim = player.getWorld().getRegistryKey();
                if (!ModDimensions.isSpace(dim)) {
                    OXYGEN.remove(player.getUuid());
                    continue;
                }
                applyGravity(player, dim);
                applyOxygen(player);
                applyHazard(player, dim);
            }
        });
        SpaceMod.LOGGER.info("[Space Exploration] environment systems armed");
    }

    private static void applyGravity(ServerPlayerEntity p, RegistryKey<World> dim) {
        double g = ModDimensions.gravity(dim);
        int jump = Math.max(0, Math.min(4, (int) Math.round((1.0 - g) * 3.5)));
        if (jump > 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, jump - 1, true, false, false));
        }
        if (g <= 0.5) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false, false));
        }
    }

    private static boolean isHelmet(ItemStack s) {
        return s.isOf(ModItems.SPACE_HELMET) || s.isOf(ModItems.THERMAL_HELMET);
    }

    private static int suitTier(ServerPlayerEntity p) {
        ItemStack h = p.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack c = p.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack l = p.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack b = p.getEquippedStack(EquipmentSlot.FEET);
        boolean thermal = h.isOf(ModItems.THERMAL_HELMET) && c.isOf(ModItems.THERMAL_CHESTPLATE)
                && l.isOf(ModItems.THERMAL_LEGGINGS) && b.isOf(ModItems.THERMAL_BOOTS);
        if (thermal) return 2;
        boolean basic = (h.isOf(ModItems.SPACE_HELMET) || h.isOf(ModItems.THERMAL_HELMET))
                && (c.isOf(ModItems.SPACE_CHESTPLATE) || c.isOf(ModItems.THERMAL_CHESTPLATE))
                && (l.isOf(ModItems.SPACE_LEGGINGS) || l.isOf(ModItems.THERMAL_LEGGINGS))
                && (b.isOf(ModItems.SPACE_BOOTS) || b.isOf(ModItems.THERMAL_BOOTS));
        return basic ? 1 : 0;
    }

    private static void applyOxygen(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        int air = OXYGEN.getOrDefault(id, 100);
        if (isHelmet(p.getEquippedStack(EquipmentSlot.HEAD))) {
            air = 100;
        } else {
            air -= 10;
            if (air <= 0) {
                air = 0;
                ServerWorld w = (ServerWorld) p.getWorld();
                p.damage(w, w.getDamageSources().generic(), 2.0f);
            }
        }
        OXYGEN.put(id, air);
    }

    private static void applyHazard(ServerPlayerEntity p, RegistryKey<World> dim) {
        ServerWorld w = (ServerWorld) p.getWorld();
        int tier = suitTier(p);
        if (dim == ModDimensions.EUROPA) {
            if (tier < 1) {
                p.setFrozenTicks(Math.min(p.getMinFreezeDamageTicks() + 20, p.getFrozenTicks() + 40));
                p.damage(w, w.getDamageSources().freeze(), 1.0f);
            } else {
                p.setFrozenTicks(0);
            }
        } else if (dim == ModDimensions.VENUS) {
            if (tier < 2) {
                p.setOnFireFor(3);
                p.damage(w, w.getDamageSources().onFire(), 2.0f);
            }
        } else if (dim == ModDimensions.MARS) {
            if (tier < 1) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, true, false, false));
                p.damage(w, w.getDamageSources().generic(), 1.0f);
            }
        }
    }
}
