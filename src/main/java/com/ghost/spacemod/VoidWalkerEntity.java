package com.ghost.spacemod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;

/**
 * A from-scratch hostile alien — its own entity type and spawn. It reuses the zombie
 * behaviour/skeleton for now (a bespoke model is a later, client-side step) but does not
 * burn in daylight, since it stalks airless worlds.
 */
public class VoidWalkerEntity extends ZombieEntity {

    public VoidWalkerEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected boolean burnsInDaylight() {
        return false;
    }

    @Override
    protected boolean canConvertInWater() {
        return false;
    }
}
