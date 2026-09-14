package com.ghost.spacemod.weapon;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * A weapon whose right-click fires an ability (sneak = secondary) and whose melee hit runs an
 * on-hit effect. All logic is dispatched to {@link WeaponAbilities} on the server.
 */
public class WeaponItem extends Item {

    private final String weaponId;

    public WeaponItem(Item.Settings settings, String weaponId) {
        super(settings);
        this.weaponId = weaponId;
    }

    public String weaponId() {
        return weaponId;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
            WeaponAbilities.trigger(sw, sp, weaponId, sp.isSneaking(), sp.getStackInHand(hand));
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayerEntity sp && attacker.getWorld() instanceof ServerWorld sw) {
            WeaponAbilities.onHit(sw, sp, weaponId, target);
        }
        super.postHit(stack, target, attacker);
    }
}
