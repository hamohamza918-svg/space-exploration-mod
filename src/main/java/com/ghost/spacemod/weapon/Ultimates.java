package com.ghost.spacemod.weapon;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Routes the Ultimate keybind to the held weapon's ultimate. */
public final class Ultimates {

    private Ultimates() {
    }

    public static void fire(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld w)) {
            return;
        }
        ItemStack held = p.getMainHandStack();
        if (!(held.getItem() instanceof WeaponItem weapon)) {
            return;
        }
        switch (weapon.weaponId()) {
            case "cryo_lance" -> WeaponAbilities.absoluteZero(w, p, held);
            default -> p.sendMessage(Text.literal("✦ Ultimate for this weapon is coming soon.").formatted(Formatting.GRAY), true);
        }
    }
}
