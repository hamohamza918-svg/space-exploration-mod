package com.ghost.spacemod.weapon;

import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative weapon abilities, implemented to the v2 design spec.
 * Everything (damage, cooldowns, movement, status, block edits) runs and is validated on the
 * server; particles/sounds broadcast to nearby clients.
 *
 * Design notes:
 *  - "Sustained" weapons are click-to-channel (one click auto-runs the stream for its duration).
 *  - Scorch/craters are custom damage-zones with cosmetic blocks; no real fire is ever placed,
 *    so effects can't spread through or burn player builds. TERRAIN_DESTRUCTION stays off.
 */
public final class WeaponAbilities {

    public static boolean TERRAIN_DESTRUCTION = false;

    // colors
    private static final int C_XYLITE = 0x9b5cff, C_ARC = 0x57d6ff, C_CRYO = 0x8be6ff,
            C_HAMMER = 0xd0d0d0, C_HELIOS = 0xff7a1a, C_DRILL = 0xc57a45, C_ACID = 0x7cff3a, C_LASER = 0x6effa0;

    // ---- state ----------------------------------------------------------
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static final Map<String, int[]> COMBO = new HashMap<>();        // playerUuid -> {stage, lastTick}
    private static final Map<Integer, int[]> CORROSION = new HashMap<>();   // entityId -> {stacks, expiryTick}
    private static final Map<Integer, Long> CUT_MARK = new HashMap<>();     // entityId -> expiryTick
    private static final Map<Integer, Long> FROZEN = new HashMap<>();       // entityId -> tick until frozen (shatter window)
    private static final Map<String, SunState> SUNS = new HashMap<>();      // playerUuid -> orbiting suns
    private static final Map<String, AZState> ABSOLUTE_ZERO = new HashMap<>(); // playerUuid -> active ultimate

    private WeaponAbilities() {
    }

    private static long now() {
        return ServerScheduler.now();
    }

    // ---- entry points ----------------------------------------------------

    public static void trigger(ServerWorld w, ServerPlayerEntity p, String id, boolean secondary, ItemStack stack) {
        switch (id) {
            case "xylite_blade" -> { if (secondary) blinkExecution(w, p, stack); else riftCleave(w, p, stack); }
            case "arc_carbine" -> { if (secondary) capacitorBurst(w, p, stack); else arcLink(w, p, stack); }
            case "cryo_lance" -> { if (secondary) glacialRupture(w, p, stack); else permafrostStream(w, p, stack); }
            case "resonance_hammer" -> { if (secondary) seismicLeap(w, p, stack); else faultbreaker(w, p, stack); }
            case "helios_glaive" -> { if (secondary) sunSpin(w, p, stack); else solarSweep(w, p, stack); }
            case "borer_drill" -> { if (secondary) pistonBreaker(w, p, stack); else breachCharge(w, p, stack); }
            case "corrosion_sprayer" -> { if (secondary) sludgeBomb(w, p, stack); else causticJet(w, p, stack); }
            case "laser_cutter" -> { if (secondary) overcharge(w, p, stack); else focusedCut(w, p, stack); }
            default -> { }
        }
    }

    public static void onHit(ServerWorld w, ServerPlayerEntity p, String id, LivingEntity target) {
        // Melee left-click keeps a light on-theme touch; the spec's depth is in the abilities.
        Vec3d at = target.getPos().add(0, target.getHeight() * 0.6, 0);
        DamageSource src = w.getDamageSources().playerAttack(p);
        switch (id) {
            case "cryo_lance" -> { applyFrostHit(w, target, 1); impact(w, at, C_CRYO, SoundEvents.BLOCK_GLASS_BREAK); }
            case "helios_glaive" -> { target.setOnFireFor(3f); impact(w, at, C_HELIOS, SoundEvents.ITEM_FIRECHARGE_USE); }
            case "resonance_hammer" -> { knock(p, target, 1.4); impact(w, at, C_HAMMER, SoundEvents.ENTITY_GENERIC_EXPLODE.value()); }
            case "corrosion_sprayer" -> addCorrosion(target, 1);
            default -> { }
        }
    }

    // ---- shared helpers --------------------------------------------------

    private static boolean ready(ServerPlayerEntity p, ItemStack stack, String key, int cdTicks) {
        String k = p.getUuidAsString() + ":" + key;
        long t = now();
        Long exp = COOLDOWNS.get(k);
        if (exp != null && exp > t) {
            return false;
        }
        COOLDOWNS.put(k, t + cdTicks);
        if (cdTicks > 20) {
            p.getItemCooldownManager().set(stack, cdTicks);
        }
        return true;
    }

    private static DustParticleEffect dust(int rgb, float size) {
        return new DustParticleEffect(rgb, size);
    }

    private static void particle(ServerWorld w, ParticleEffect fx, Vec3d at, int count, double spread, double speed) {
        w.spawnParticles(fx, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static void sound(ServerWorld w, Vec3d at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, vol, pitch, 0L);
    }

    private static void impact(ServerWorld w, Vec3d at, int rgb, SoundEvent s) {
        particle(w, ParticleTypes.EXPLOSION, at, 2, 0.3, 0.0);
        particle(w, dust(rgb, 1.9f), at, 30, 0.5, 0.03);
        particle(w, ParticleTypes.END_ROD, at, 14, 0.35, 0.2);
        particle(w, ParticleTypes.FLASH, at, 1, 0, 0);
        sound(w, at, s, 1.1f, 1.15f);
    }

    private static void knock(ServerPlayerEntity from, LivingEntity le, double power) {
        Vec3d d = le.getPos().subtract(from.getPos());
        le.takeKnockback(power, -d.x, -d.z);
    }

    private static Box around(Vec3d c, double r) {
        return new Box(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }

    private static List<Entity> living(ServerWorld w, ServerPlayerEntity p, Box box) {
        return w.getOtherEntities(p, box, e -> e instanceof LivingEntity && e.isAlive());
    }

    /** Corrosion-amplified damage from our weapons (each stack = +5% incoming, cap +15%). */
    private static void hurt(ServerWorld w, ServerPlayerEntity p, LivingEntity le, float amount) {
        le.damage(w, w.getDamageSources().playerAttack(p), amount * (1f + corrosionBonus(le)));
    }

    private static float corrosionBonus(LivingEntity le) {
        int[] c = CORROSION.get(le.getId());
        if (c == null || c[1] < now()) {
            return 0f;
        }
        return Math.min(3, c[0]) * 0.05f;
    }

    private static void addCorrosion(LivingEntity le, int add) {
        int[] c = CORROSION.get(le.getId());
        int stacks = (c != null && c[1] >= now()) ? c[0] : 0;
        stacks = Math.min(3, stacks + add);
        CORROSION.put(le.getId(), new int[]{stacks, (int) (now() + 80)}); // 4s
    }

    private static void applyFrostHit(ServerWorld w, LivingEntity le, int frostTicks) {
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2, true, false, false));
        le.setFrozenTicks(Math.min(le.getMinFreezeDamageTicks() + 40, le.getFrozenTicks() + 80));
        particle(w, ParticleTypes.SNOWFLAKE, le.getPos().add(0, le.getHeight() * 0.5, 0), 10, 0.4, 0.02);
    }

    private static boolean isFrozen(LivingEntity le) {
        Long t = FROZEN.get(le.getId());
        return t != null && t >= now();
    }

    private static void setFrozen(ServerWorld w, LivingEntity le, int ticks) {
        FROZEN.put(le.getId(), now() + ticks);
        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 6, true, false, false));
        le.setFrozenTicks(le.getMinFreezeDamageTicks() + 100);
        particle(w, ParticleTypes.SNOWFLAKE, le.getPos().add(0, 1, 0), 30, 0.4, 0.05);
    }

    private static void tempBlock(ServerWorld w, BlockPos pos, Block block, int ticks) {
        BlockState old = w.getBlockState(pos);
        if (!old.isAir() && !old.isReplaceable()) {
            return;
        }
        w.setBlockState(pos, block.getDefaultState());
        ServerScheduler.runLater(ticks, () -> {
            if (w.getBlockState(pos).isOf(block)) {
                w.setBlockState(pos, old);
            }
        });
    }

    private static void dash(ServerPlayerEntity p, Vec3d dir, double power, double up) {
        p.setVelocity(dir.x * power, up, dir.z * power);
        p.velocityModified = true;
    }

    /** First living entity along the player's aim, stopping at walls. */
    private static LivingEntity rayTarget(ServerWorld w, ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector();
        for (double d = 0.5; d < range; d += 0.3) {
            Vec3d pt = eye.add(dir.multiply(d));
            BlockPos bp = BlockPos.ofFloored(pt.x, pt.y, pt.z);
            if (w.getBlockState(bp).isSolidBlock(w, bp)) {
                break;
            }
            for (Entity e : living(w, p, around(pt, 0.75))) {
                if (e instanceof LivingEntity le) {
                    return le;
                }
            }
        }
        return null;
    }

    /** End point of the aim ray (wall or max range). */
    private static Vec3d aimPoint(ServerWorld w, ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector();
        Vec3d end = eye.add(dir.multiply(range));
        for (double d = 0.5; d < range; d += 0.3) {
            Vec3d pt = eye.add(dir.multiply(d));
            BlockPos bp = BlockPos.ofFloored(pt.x, pt.y, pt.z);
            if (w.getBlockState(bp).isSolidBlock(w, bp)) {
                return pt;
            }
        }
        return end;
    }

    private static void strikeLightning(ServerWorld w, Vec3d at) {
        LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, w);
        bolt.setPosition(at.x, at.y, at.z);
        bolt.setCosmetic(true);
        w.spawnEntity(bolt);
    }

    private static void beamParticles(ServerWorld w, Vec3d from, Vec3d to, int rgb, ParticleEffect spark) {
        Vec3d diff = to.subtract(from);
        double len = diff.length();
        Vec3d step = diff.normalize().multiply(0.4);
        Vec3d cur = from;
        DustParticleEffect core = dust(rgb, 1.4f);
        for (double d = 0; d < len; d += 0.4) {
            particle(w, core, cur, 2, 0.05, 0.0);
            if (spark != null) {
                particle(w, spark, cur, 1, 0.03, 0.0);
            }
            cur = cur.add(step);
        }
    }

    // ==== XYLITE BLADE ====================================================

    private static void riftCleave(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "rift", 6)) {
            return;
        }
        String u = p.getUuidAsString();
        int[] c = COMBO.get(u);
        if (c == null || now() - c[1] > 30) {
            c = new int[]{0, 0};
        }
        int stage = c[0] % 3 + 1;
        COMBO.put(u, new int[]{stage, (int) now()});

        float dmg = stage == 1 ? 5f : stage == 2 ? 6f : 9f;
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector();
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        // crescent that flips side per swing
        double flip = stage == 2 ? -1 : 1;
        for (double a = -1.0; a <= 1.0; a += 0.1) {
            Vec3d pt = eye.add(dir.multiply(2.6)).add(right.multiply(a * 2.2 * flip)).add(0, -a * a * 0.7 + 0.5, 0);
            particle(w, dust(C_XYLITE, 1.5f), pt, 2, 0.04, 0.0);
            particle(w, ParticleTypes.END_ROD, pt, 1, 0.0, 0.0);
        }
        sound(w, eye, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, stage == 3 ? 0.9f : 1.3f + stage * 0.1f);

        double range = stage == 3 ? 6.0 : 4.0;
        for (Entity e : living(w, p, around(eye.add(dir.multiply(range * 0.5)), range))) {
            if (e instanceof LivingEntity le) {
                Vec3d to = le.getPos().add(0, le.getHeight() * 0.5, 0).subtract(eye);
                if (to.normalize().dotProduct(dir) > 0.45) {
                    hurt(w, p, le, dmg);
                    impact(w, le.getPos().add(0, le.getHeight() * 0.6, 0), C_XYLITE, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT);
                    scrapeLines(w, le.getPos());
                }
            }
        }
        if (stage == 3) {
            // final crescent travels forward, cutting through
            ServerScheduler.runTimer(1, 1, 6, new Runnable() {
                double d = 1;
                @Override
                public void run() {
                    Vec3d pt = eye.add(dir.multiply(d)).add(0, -0.2, 0);
                    for (int i = -2; i <= 2; i++) {
                        Vec3d q = pt.add(right.multiply(i * 0.5));
                        particle(w, dust(C_XYLITE, 1.6f), q, 2, 0.05, 0.0);
                    }
                    for (Entity e : living(w, p, around(pt, 1.6))) {
                        if (e instanceof LivingEntity le) {
                            hurt(w, p, le, 3f);
                        }
                    }
                    d += 1.0;
                }
            });
        }
    }

    private static void scrapeLines(ServerWorld w, Vec3d at) {
        particle(w, dust(C_XYLITE, 1.0f), at.add(0, 0.1, 0), 12, 0.6, 0.0);
    }

    private static void blinkExecution(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "blink", 160)) {
            return;
        }
        Vec3d startPos = p.getPos();
        particle(w, ParticleTypes.REVERSE_PORTAL, startPos.add(0, 1, 0), 40, 0.5, 0.1);
        sound(w, startPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
        dash(p, p.getRotationVector(), 2.4, 0.35); // ~7 blocks
        ServerScheduler.runTimer(1, 1, 10, () -> particle(w, dust(C_XYLITE, 1.5f), p.getPos().add(0, 1, 0), 16, 0.3, 0.0));
        ServerScheduler.runLater(8, () -> {
            LivingEntity t = rayTarget(w, p, 3.5);
            Vec3d riftAt = t != null ? t.getPos() : p.getPos().add(p.getRotationVector().multiply(2));
            if (t != null) {
                hurt(w, p, t, 8f);
                impact(w, t.getPos().add(0, t.getHeight() * 0.6, 0), C_XYLITE, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT);
            }
            // delayed rift snaps shut
            ServerScheduler.runLater(12, () -> {
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    particle(w, dust(C_XYLITE, 1.4f), riftAt.add(Math.cos(a) * 1.5, 0.8, Math.sin(a) * 1.5), 2, 0.05, 0.0);
                }
                sound(w, riftAt, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.1f, 0.7f);
                for (Entity e : living(w, p, around(riftAt.add(0, 1, 0), 2.2))) {
                    if (e instanceof LivingEntity le) {
                        hurt(w, p, le, 4f);
                        impact(w, le.getPos().add(0, 1, 0), C_XYLITE, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK);
                    }
                }
            });
        });
    }

    // ==== ARC CARBINE =====================================================

    private static void arcLink(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "arclink", 10)) {
            return;
        }
        sound(w, p.getEyePos(), SoundEvents.ENTITY_BLAZE_SHOOT, 1.1f, 1.7f);
        LivingEntity primary = rayTarget(w, p, 32);
        Vec3d end = primary != null ? primary.getPos().add(0, primary.getHeight() * 0.5, 0) : aimPoint(w, p, 32);
        beamParticles(w, p.getEyePos(), end, C_ARC, ParticleTypes.ELECTRIC_SPARK);
        strikeLightning(w, end);
        if (primary == null) {
            return;
        }
        float dmg = primary.isTouchingWater() ? 7.5f : 5f;
        hurt(w, p, primary, dmg);
        impact(w, end, C_ARC, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT);
        // chain to two nearest, wet targets prioritized
        List<LivingEntity> near = new ArrayList<>();
        for (Entity e : living(w, p, around(primary.getPos(), 6))) {
            if (e instanceof LivingEntity le && le != primary) {
                near.add(le);
            }
        }
        near.sort(Comparator.comparingDouble((LivingEntity le) -> (le.isTouchingWater() ? -1000 : 0) + le.squaredDistanceTo(primary)));
        float[] chain = {3f, 2f};
        Vec3d prev = end;
        for (int i = 0; i < Math.min(2, near.size()); i++) {
            LivingEntity le = near.get(i);
            Vec3d lp = le.getPos().add(0, le.getHeight() * 0.5, 0);
            beamParticles(w, prev, lp, C_ARC, ParticleTypes.ELECTRIC_SPARK);
            hurt(w, p, le, chain[i]);
            particle(w, ParticleTypes.ELECTRIC_SPARK, lp, 12, 0.3, 0.1);
            prev = lp;
        }
        // ground crawl + water surface discharge
        particle(w, ParticleTypes.ELECTRIC_SPARK, end.add(0, -1, 0), 20, 1.2, 0.05);
        if (primary.isTouchingWater()) {
            for (Entity e : living(w, p, around(primary.getPos(), 3.5))) {
                if (e instanceof LivingEntity le && le.isTouchingWater()) {
                    hurt(w, p, le, 3f);
                    particle(w, ParticleTypes.ELECTRIC_SPARK, le.getPos().add(0, 0.2, 0), 8, 0.4, 0.1);
                }
            }
        }
    }

    private static void capacitorBurst(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "capacitor", 140)) {
            return;
        }
        Map<Integer, Integer> hits = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            ServerScheduler.runLater(1 + i * 3, () -> {
                sound(w, p.getEyePos(), SoundEvents.ENTITY_BLAZE_SHOOT, 0.9f, 1.9f);
                LivingEntity t = rayTarget(w, p, 30);
                Vec3d end = t != null ? t.getPos().add(0, t.getHeight() * 0.5, 0) : aimPoint(w, p, 30);
                beamParticles(w, p.getEyePos(), end, C_ARC, ParticleTypes.ELECTRIC_SPARK);
                if (t != null) {
                    hurt(w, p, t, 3f);
                    int n = hits.merge(t.getId(), 1, Integer::sum);
                    impact(w, end, C_ARC, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT);
                    if (n == 4) { // all four on one enemy -> discharge
                        sound(w, end, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.3f, 1.0f);
                        strikeLightning(w, t.getPos());
                        for (Entity e : living(w, p, around(t.getPos(), 3))) {
                            if (e instanceof LivingEntity le) {
                                hurt(w, p, le, 5f);
                                particle(w, ParticleTypes.ELECTRIC_SPARK, le.getPos().add(0, 1, 0), 14, 0.4, 0.15);
                            }
                        }
                    }
                }
            });
        }
    }

    // ==== CRYO LANCE ======================================================

    private static void permafrostStream(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "permafrost", 40)) {
            return;
        }
        CryoLanceItem.cast(w, p, stack, "stream", p.getPos(), 40);
        sound(w, p.getEyePos(), SoundEvents.BLOCK_GLASS_BREAK, 0.9f, 1.4f);
        // 2s channel: frost + 3 dmg/sec; a target kept in the stream for 2s freezes 1s
        Map<Integer, Integer> contact = new HashMap<>();
        ServerScheduler.runTimer(1, 2, 20, () -> { // 40 ticks total (2s), every 2 ticks
            if (!p.isAlive() || p.getWorld() != w || p.getMainHandStack() != stack) return;
            Vec3d eye = p.getEyePos();
            Vec3d dir = p.getRotationVector();
            for (double d = 1; d < 8; d += 0.5) {
                Vec3d pt = eye.add(dir.multiply(d));
                BlockPos bp = BlockPos.ofFloored(pt.x, pt.y, pt.z);
                if (w.getBlockState(bp).isSolidBlock(w, bp)) {
                    tempBlock(w, bp, Blocks.PACKED_ICE, 60);
                    tempBlock(w, BlockPos.ofFloored(pt.x, pt.y, pt.z).down(), Blocks.BLUE_ICE, 80); // slick patch
                    break;
                }
                particle(w, ParticleTypes.SNOWFLAKE, pt, 2, 0.1, 0.01);
                particle(w, dust(C_CRYO, 1.0f), pt, 1, 0.03, 0.0);
            }
            LivingEntity t = rayTarget(w, p, 8);
            if (t != null) {
                int ticks = contact.merge(t.getId(), 2, Integer::sum);
                if (ticks % 10 == 0) {
                    hurt(w, p, t, 1.5f); // ~3/sec
                }
                applyFrostHit(w, t, 2);
                if (ticks >= 40 && !isFrozen(t)) {
                    setFrozen(w, t, 20); // 1s freeze after 2s sustained
                    sound(w, t.getPos(), SoundEvents.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
                }
            }
        });
    }

    private static void glacialRupture(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "glacial", 200)) {
            return;
        }
        CryoLanceItem.cast(w, p, stack, "nova", p.getPos(), 24);
        Vec3d c = p.getPos();
        sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        sound(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.4f);
        java.util.Set<Integer> struck = new java.util.HashSet<>();
        ServerScheduler.runTimer(1, 2, 9, new Runnable() {
            double r = 1;
            @Override
            public void run() {
                for (int i = 0; i < 36; i++) {
                    double a = i / 36.0 * Math.PI * 2;
                    Vec3d pt = c.add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
                    particle(w, ParticleTypes.SNOWFLAKE, pt, 3, 0.06, 0.03);
                    if (i % 4 == 0) {
                        tempBlock(w, BlockPos.ofFloored(pt.x, pt.y - 1, pt.z), Blocks.PACKED_ICE, 100);
                    }
                }
                for (Entity e : living(w, p, around(c, r))) {
                    if (e instanceof LivingEntity le && struck.add(le.getId()) && le.squaredDistanceTo(c.x, le.getY(), c.z) <= r * r) {
                        boolean frozen = isFrozen(le);
                        hurt(w, p, le, frozen ? 10f : 6f);
                        if (frozen) {
                            FROZEN.remove(le.getId()); // consume freeze -> shatter
                            particle(w, ParticleTypes.ITEM_SNOWBALL, le.getPos().add(0, 1, 0), 30, 0.5, 0.2);
                            impact(w, le.getPos().add(0, 1, 0), C_CRYO, SoundEvents.BLOCK_GLASS_BREAK);
                        } else {
                            applyFrostHit(w, le, 2);
                        }
                    }
                }
                r += 6.0 / 9.0; // reach ~6 blocks
            }
        });
    }

    // ==== RESONANCE HAMMER ===============================================

    private static void faultbreaker(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "fault", 12)) {
            return;
        }
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector();
        sound(w, p.getPos(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1.3f, 0.8f);
        LivingEntity direct = rayTarget(w, p, 3.5);
        if (direct != null) {
            hurt(w, p, direct, 12f);
            impact(w, direct.getPos().add(0, 1, 0), C_HAMMER, SoundEvents.ENTITY_GENERIC_EXPLODE.value());
        }
        // narrow ground rupture forward 8 blocks
        Vec3d flat = new Vec3d(dir.x, 0, dir.z).normalize();
        java.util.Set<Integer> struck = new java.util.HashSet<>();
        ServerScheduler.runTimer(1, 1, 8, new Runnable() {
            double d = 1;
            @Override
            public void run() {
                Vec3d pt = p.getPos().add(flat.multiply(d));
                particle(w, ParticleTypes.EXPLOSION, pt.add(0, 0.3, 0), 1, 0.1, 0.0);
                particle(w, dust(C_HAMMER, 1.6f), pt.add(0, 0.4, 0), 10, 0.3, 0.05);
                particle(w, ParticleTypes.CRIT, pt.add(0, 0.3, 0), 6, 0.2, 0.1);
                blockBurst(w, pt);
                for (Entity e : living(w, p, around(pt.add(0, 0.5, 0), 1.5))) {
                    if (e instanceof LivingEntity le && struck.add(le.getId())) {
                        hurt(w, p, le, 6f);
                        le.setVelocity(le.getVelocity().x, 0.7, le.getVelocity().z);
                        le.velocityModified = true;
                    }
                }
                d += 1.0;
            }
        });
    }

    private static void seismicLeap(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "seismic", 280)) {
            return;
        }
        sound(w, p.getPos(), SoundEvents.ENTITY_RAVAGER_ROAR, 1f, 1.1f);
        particle(w, ParticleTypes.CLOUD, p.getPos(), 20, 0.3, 0.05);
        dash(p, p.getRotationVector(), 1.1, 1.3);
        // detect landing (or timeout) then slam with distance falloff
        ServerScheduler.runTimer(6, 2, 30, new Runnable() {
            boolean done = false;
            int ticks = 0;
            @Override
            public void run() {
                if (done) {
                    return;
                }
                ticks += 2;
                if (p.isOnGround() || ticks > 58) {
                    done = true;
                    slamFalloff(w, p);
                }
            }
        });
    }

    private static void slamFalloff(ServerWorld w, ServerPlayerEntity p) {
        Vec3d c = p.getPos();
        sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.6f, 0.5f);
        sound(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.0f);
        particle(w, ParticleTypes.EXPLOSION_EMITTER, c, 2, 0.5, 0.0);
        rings(w, c, 6, C_HAMMER);
        floorBuckle(w, c, 6);
        for (Entity e : living(w, p, around(c, 6))) {
            if (e instanceof LivingEntity le) {
                double dist = Math.sqrt(le.squaredDistanceTo(c.x, le.getY(), c.z));
                float dmg = dist <= 2 ? 14f : (float) Math.max(5, 14 - (dist - 2) * (9.0 / 4.0));
                hurt(w, p, le, dmg);
                le.takeKnockback(1.6, c.x - le.getX(), c.z - le.getZ());
                le.setVelocity(le.getVelocity().x, 0.6, le.getVelocity().z);
                le.velocityModified = true;
            }
        }
    }

    /** Rising block fragments in concentric rings + dust (no terrain destroyed). */
    private static void floorBuckle(ServerWorld w, Vec3d c, double radius) {
        for (int ring = 1; ring <= (int) radius; ring++) {
            final int rr = ring;
            ServerScheduler.runLater(ring, () -> {
                for (int i = 0; i < 10 + rr * 4; i++) {
                    double a = i / (10.0 + rr * 4) * Math.PI * 2;
                    Vec3d pt = c.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr);
                    BlockPos below = BlockPos.ofFloored(pt.x, pt.y - 1, pt.z);
                    BlockState bs = w.getBlockState(below);
                    if (!bs.isAir()) {
                        w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, bs),
                                pt.x, pt.y, pt.z, 6, 0.2, 0.3, 0.2, 0.15);
                    }
                    particle(w, ParticleTypes.CLOUD, pt, 2, 0.2, 0.02);
                }
            });
        }
    }

    /** Spray of the ground block's break particles at a point (cosmetic, no blocks removed). */
    private static void blockBurst(ServerWorld w, Vec3d at) {
        BlockPos below = BlockPos.ofFloored(at.x, at.y - 1, at.z);
        BlockState bs = w.getBlockState(below);
        if (!bs.isAir()) {
            w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, bs),
                    at.x, at.y, at.z, 6, 0.2, 0.3, 0.2, 0.15);
        }
    }

    private static void rings(ServerWorld w, Vec3d c, double radius, int rgb) {
        for (int ring = 1; ring <= 3; ring++) {
            final double rr = radius * ring / 3.0;
            ServerScheduler.runLater(ring * 2, () -> {
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    Vec3d edge = c.add(Math.cos(a) * rr, 0.3, Math.sin(a) * rr);
                    particle(w, ParticleTypes.POOF, edge, 3, 0.1, 0.03);
                    particle(w, dust(rgb, 1.5f), edge, 2, 0.1, 0.0);
                }
            });
        }
    }

    // ==== HELIOS GLAIVE ==================================================

    private static void solarSweep(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "solar", 10)) {
            return;
        }
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector();
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        for (double a = -1.1; a <= 1.1; a += 0.1) {
            Vec3d pt = eye.add(dir.multiply(2.4)).add(right.multiply(a * 2.6)).add(0, -a * a * 0.5 + 0.3, 0);
            particle(w, ParticleTypes.FLAME, pt, 4, 0.06, 0.01);
            particle(w, dust(C_HELIOS, 1.4f), pt, 2, 0.05, 0.0);
        }
        sound(w, eye, SoundEvents.ITEM_FIRECHARGE_USE, 1.3f, 0.8f);
        for (Entity e : living(w, p, around(eye.add(dir.multiply(3)), 5))) {
            if (e instanceof LivingEntity le) {
                Vec3d to = le.getPos().add(0, le.getHeight() * 0.5, 0).subtract(eye);
                double dist = to.length();
                if (to.normalize().dotProduct(dir) > 0.4) {
                    float dmg = 7f + (dist > 3.5 ? 2f : 0f); // outer edge rewards spacing
                    hurt(w, p, le, dmg);
                    le.setOnFireFor(3f); // 3 burn over 3s (approx via fire ticks)
                    impact(w, le.getPos().add(0, 1, 0), C_HELIOS, SoundEvents.ITEM_FIRECHARGE_USE);
                }
            }
        }
        // custom scorch patch at aim (no real fire)
        scorchPatch(w, p, aimPoint(w, p, 6).add(0, 0.1, 0), 2.5, 60);
    }

    /** Damage-zone scorch with cosmetic embers; never places real fire (safe for builds). */
    private static void scorchPatch(ServerWorld w, ServerPlayerEntity p, Vec3d center, double radius, int ticks) {
        ServerScheduler.runTimer(1, 5, ticks / 5, () -> {
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                double rr = radius * (0.4 + 0.6 * ((i % 3) / 2.0));
                particle(w, ParticleTypes.FLAME, center.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), 2, 0.1, 0.01);
            }
            particle(w, ParticleTypes.LARGE_SMOKE, center, 3, radius * 0.5, 0.02);
            for (Entity e : living(w, p, around(center, radius))) {
                if (e instanceof LivingEntity le && le.isOnGround()) {
                    hurt(w, p, le, 1f);
                    le.setOnFireFor(1f);
                }
            }
        });
    }

    private static void sunSpin(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        String u = p.getUuidAsString();
        SunState existing = SUNS.get(u);
        if (existing != null && !existing.launched) {
            existing.launched = true; // reactivate -> launch remaining suns toward aim
            existing.launchDir = p.getRotationVector();
            sound(w, p.getPos(), SoundEvents.ITEM_FIRECHARGE_USE, 1.3f, 0.6f);
            return;
        }
        if (!ready(p, stack, "sunspin", 240)) {
            return;
        }
        SunState st = new SunState();
        SUNS.put(u, st);
        sound(w, p.getPos(), SoundEvents.ITEM_FIRECHARGE_USE, 1.2f, 1.1f);
        ServerScheduler.runTimer(1, 2, 50, new Runnable() { // ~5s
            int t = 0;
            @Override
            public void run() {
                t++;
                if (!p.isAlive()) {
                    SUNS.remove(u);
                    return;
                }
                for (int k = 0; k < 3; k++) {
                    if (st.consumed[k]) {
                        continue;
                    }
                    Vec3d pos;
                    if (st.launched) {
                        st.launchPos[k] = (st.launchPos[k] == null ? p.getPos().add(0, 1, 0) : st.launchPos[k]).add(st.launchDir.multiply(0.6));
                        pos = st.launchPos[k];
                    } else {
                        double ang = t * 0.35 + k * (Math.PI * 2 / 3);
                        pos = p.getPos().add(Math.cos(ang) * 2.2, 1.1, Math.sin(ang) * 2.2);
                    }
                    particle(w, ParticleTypes.FLAME, pos, 8, 0.15, 0.02);
                    particle(w, dust(C_HELIOS, 2.0f), pos, 4, 0.1, 0.0);
                    for (Entity e : living(w, p, around(pos, 1.4))) {
                        if (e instanceof LivingEntity le && st.hit[k].add(le.getId())) {
                            hurt(w, p, le, 3f);
                            le.setOnFireFor(2f);
                            impact(w, le.getPos().add(0, 1, 0), C_HELIOS, SoundEvents.ITEM_FIRECHARGE_USE);
                            if (st.launched) {
                                st.consumed[k] = true;
                            }
                        }
                    }
                }
                if (t >= 50) {
                    SUNS.remove(u);
                }
            }
        });
    }

    private static final class SunState {
        boolean launched = false;
        boolean[] consumed = new boolean[3];
        @SuppressWarnings("unchecked")
        java.util.Set<Integer>[] hit = new java.util.HashSet[]{new java.util.HashSet<>(), new java.util.HashSet<>(), new java.util.HashSet<>()};
        Vec3d launchDir = new Vec3d(0, 0, 1);
        Vec3d[] launchPos = new Vec3d[3];
    }

    // ==== BORER DRILL ====================================================

    private static void breachCharge(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "breach", 14)) {
            return;
        }
        sound(w, p.getPos(), SoundEvents.BLOCK_GRINDSTONE_USE, 1.3f, 0.7f);
        Map<Integer, Integer> dealt = new HashMap<>();
        // spin-up then drive forward ~1s (2 dmg / 4 ticks, cap 10 per target)
        ServerScheduler.runLater(4, () -> {
            dash(p, p.getRotationVector(), 1.6, 0.05);
            ServerScheduler.runTimer(1, 4, 6, () -> {
                Vec3d front = p.getEyePos().add(p.getRotationVector().multiply(2));
                particle(w, ParticleTypes.CRIT, front, 20, 0.4, 0.4);
                particle(w, dust(C_DRILL, 1.5f), p.getPos().add(0, 1, 0).subtract(p.getRotationVector().multiply(1)), 10, 0.3, 0.1);
                for (Entity e : living(w, p, around(front, 1.4))) {
                    if (e instanceof LivingEntity le) {
                        int prior = dealt.getOrDefault(le.getId(), 0);
                        if (prior < 10) {
                            hurt(w, p, le, 2f);
                            dealt.put(le.getId(), prior + 2);
                        }
                    }
                }
            });
        });
    }

    private static void pistonBreaker(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "piston", 200)) {
            return;
        }
        float[] dmg = {3f, 3f, 7f};
        int[] delays = {2, 8, 20}; // pause before final
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            ServerScheduler.runLater(delays[i], () -> {
                Vec3d c = p.getPos();
                boolean last = idx == 2;
                sound(w, c, SoundEvents.BLOCK_ANVIL_LAND, last ? 1.4f : 0.9f, last ? 0.7f : 1.4f);
                particle(w, ParticleTypes.CRIT, c.add(0, 0.2, 0), last ? 40 : 16, 0.5, last ? 0.3 : 0.15);
                double r = last ? 3.5 : 2.0;
                if (last) {
                    floorBuckle(w, c, 3);
                    rings(w, c, 3.5, C_DRILL);
                }
                for (Entity e : living(w, p, around(c, r))) {
                    if (e instanceof LivingEntity le) {
                        hurt(w, p, le, dmg[idx]);
                        if (last) {
                            le.takeKnockback(1.5, c.x - le.getX(), c.z - le.getZ());
                        }
                    }
                }
            });
        }
    }

    // ==== CORROSION SPRAYER ==============================================

    private static void causticJet(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "caustic", 12)) {
            return;
        }
        sound(w, p.getEyePos(), SoundEvents.ENTITY_SPIDER_HURT, 1.0f, 0.6f);
        ServerScheduler.runTimer(1, 4, 10, () -> { // 40 ticks, every 4 (2s)
            Vec3d eye = p.getEyePos();
            Vec3d dir = p.getRotationVector();
            for (double d = 1; d <= 5; d += 0.5) {
                Vec3d pt = eye.add(dir.multiply(d));
                particle(w, dust(C_ACID, 1.4f), pt, 6, 0.5, 0.0);
                particle(w, ParticleTypes.SNEEZE, pt, 2, 0.3, 0.02);
            }
            for (Entity e : living(w, p, around(eye.add(dir.multiply(3)), 4))) {
                if (e instanceof LivingEntity le) {
                    Vec3d to = le.getPos().add(0, le.getHeight() * 0.5, 0).subtract(eye);
                    if (to.normalize().dotProduct(dir) > 0.5) {
                        hurt(w, p, le, 1f); // ~2/sec at this cadence
                        addCorrosion(le, 1);
                        le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 0, true, false, false));
                    }
                }
            }
        });
    }

    private static void sludgeBomb(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "sludge", 240)) {
            return;
        }
        Vec3d target = aimPoint(w, p, 20);
        sound(w, target, SoundEvents.ENTITY_SPIDER_DEATH, 1.1f, 0.7f);
        particle(w, ParticleTypes.SNEEZE, target, 20, 0.4, 0.1);
        for (Entity e : living(w, p, around(target, 1.5))) {
            if (e instanceof LivingEntity le) {
                hurt(w, p, le, 4f);
                addCorrosion(le, 1);
            }
        }
        // 4-block cloud, 6s, 1 dmg/sec + maintains corrosion; readable edge
        ServerScheduler.runTimer(1, 4, 30, () -> {
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                particle(w, dust(C_ACID, 1.3f), target.add(Math.cos(a) * 4, 0.2, Math.sin(a) * 4), 1, 0.05, 0.0); // edge marker
            }
            particle(w, dust(C_ACID, 1.6f), target.add(0, 0.5, 0), 30, 3.5, 0.0);
            particle(w, ParticleTypes.LARGE_SMOKE, target, 4, 3.0, 0.02);
            for (Entity e : living(w, p, around(target, 4))) {
                if (e instanceof LivingEntity le) {
                    hurt(w, p, le, 0.5f);
                    addCorrosion(le, 1);
                }
            }
        });
    }

    // ==== LASER CUTTER ===================================================

    private static void focusedCut(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "focused", 10)) {
            return;
        }
        sound(w, p.getEyePos(), SoundEvents.ITEM_FIRECHARGE_USE, 0.9f, 1.6f);
        int[] onTarget = {0, -1}; // ticksOnSame, lastTargetId
        ServerScheduler.runTimer(1, 2, 16, () -> { // ~1.6s
            LivingEntity t = rayTarget(w, p, 26);
            Vec3d end = t != null ? t.getPos().add(0, t.getHeight() * 0.5, 0) : aimPoint(w, p, 26);
            beamParticles(w, p.getEyePos(), end, C_LASER, ParticleTypes.END_ROD);
            particle(w, ParticleTypes.CRIT, end, 4, 0.1, 0.1); // spark shower at contact
            if (t != null) {
                if (t.getId() == onTarget[1]) {
                    onTarget[0] += 2;
                } else {
                    onTarget[1] = t.getId();
                    onTarget[0] = 2;
                }
                hurt(w, p, t, 0.4f); // ~4/sec
                if (onTarget[0] >= 30) { // 1.5s on same target
                    CUT_MARK.put(t.getId(), now() + 200);
                    particle(w, dust(C_LASER, 1.8f), end, 12, 0.3, 0.0);
                }
            }
        });
    }

    // ==== ULTIMATE: CRYO LANCE — ABSOLUTE ZERO ===========================

    private static final class AZState {
        final java.util.List<BlockPos> spikes = new ArrayList<>();
        final Map<BlockPos, BlockState> originals = new HashMap<>();
        final java.util.Set<Integer> frozen = new java.util.HashSet<>();
        boolean resolved = false;
        ServerWorld world;
        Vec3d center;
        ItemStack stack;
        double radius;
    }

    /** Anime-style ultimate. First press: charge → freeze wave → ice-spike eruption. Second press: SHATTER. */
    public static void absoluteZero(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        String u = p.getUuidAsString();
        AZState active = ABSOLUTE_ZERO.get(u);
        if (active != null && !active.resolved) {
            shatterAbsoluteZero(active.world, p, active); // re-press = shatter
            return;
        }
        if (!ready(p, stack, "absolute_zero", 600)) { // 30s
            p.sendMessage(net.minecraft.text.Text.literal("✦ Absolute Zero is recharging.").formatted(net.minecraft.util.Formatting.AQUA), true);
            return;
        }
        AZState st = new AZState();
        st.radius = 12;
        st.world = w;
        st.center = p.getPos();
        st.stack = stack;
        CryoLanceItem.cast(w, p, stack, "absolute_zero", st.center, 230);
        ABSOLUTE_ZERO.put(u, st);
        final Vec3d center = p.getPos();
        final int cx = (int) Math.floor(center.x), cz = (int) Math.floor(center.z), cy = (int) Math.floor(center.y);

        sound(w, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.3f, 0.5f);
        sound(w, center, SoundEvents.BLOCK_GLASS_BREAK, 0.6f, 0.5f);

        // --- activation + charge: magic circle draws + rotates, frost gathers (0–0.6s) ---
        ServerScheduler.runTimer(1, 2, 6, new Runnable() {
            int t = 0;
            @Override
            public void run() {
                if (st.resolved) return;
                t++;
                sound(w, center, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.5f + t * 0.08f); // rising resonance
                // frost + shards gather inward toward the lance
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16 + t * 0.6;
                    double gather = 6.0 - t * 0.8;
                    Vec3d o = center.add(Math.cos(a) * gather, 0.3 + t * 0.25, Math.sin(a) * gather);
                    particle(w, ParticleTypes.SNOWFLAKE, o, 2, 0.05, 0.0);
                    particle(w, dust(C_CRYO, 1.5f), o, 1, 0.0, 0.0);
                    particle(w, ParticleTypes.ITEM_SNOWBALL, o, 1, 0.0, 0.03);
                }
                particle(w, dust(0xffffff, 1.3f), p.getPos().add(0, 1, 0), 8, 0.4, 0.02);
                if (t == 6) { // reach telegraph: flash the full danger ring
                    for (int i = 0; i < 64; i++) {
                        double a = Math.PI * 2 * i / 64;
                        particle(w, dust(C_CRYO, 1.6f), center.add(Math.cos(a) * st.radius, 0.2, Math.sin(a) * st.radius), 1, 0.0, 0.0);
                    }
                }
            }
        });

        // --- ground strike (0.6s) ---
        ServerScheduler.runLater(13, () -> {
            if (st.resolved) return;
            sound(w, center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.2f);
            sound(w, center, SoundEvents.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
            // frost shockburst instead of a vanilla explosion
            particle(w, ParticleTypes.SNOWFLAKE, center.add(0, 0.6, 0), 120, 1.2, 0.35);
            particle(w, ParticleTypes.ITEM_SNOWBALL, center.add(0, 0.6, 0), 60, 0.8, 0.5);
            particle(w, dust(C_CRYO, 2.2f), center.add(0, 0.6, 0), 60, 1.0, 0.1);
            particle(w, ParticleTypes.END_ROD, center.add(0, 0.8, 0), 20, 0.6, 0.15);
            particle(w, ParticleTypes.FLASH, center.add(0, 1, 0), 1, 0, 0);
            lightPillar(w, center);                 // vertical frost-light column
            shockRing(w, center, st.radius);        // expanding wave front
        });

        // --- freeze wave: expanding frost front, follows ground, stops at walls (0.6–1.5s) ---
        java.util.Set<Integer> waveHit = new java.util.HashSet<>();
        ServerScheduler.runTimer(14, 2, 9, new Runnable() {
            double r = 2;
            @Override
            public void run() {
                if (st.resolved) return;
                double next = r + (st.radius - 2) / 9.0;
                for (double ang = 0; ang < Math.PI * 2; ang += Math.PI / 32) {
                    int x = (int) Math.round(cx + Math.cos(ang) * r);
                    int z = (int) Math.round(cz + Math.sin(ang) * r);
                    if (!wallClear(w, cx, cy, cz, x, z)) {
                        continue;
                    }
                    int sy = surfaceY(w, x, z, cy);
                    if (sy == Integer.MIN_VALUE) {
                        continue;
                    }
                    BlockPos surf = new BlockPos(x, sy, z);
                    if (w.getBlockState(surf).getBlock() == Blocks.WATER) {
                        azPlace(w, st, surf, Blocks.ICE);
                    } else if ((x + z) % 2 == 0) {
                        azPlace(w, st, surf.up(), Blocks.SNOW);
                    }
                    particle(w, ParticleTypes.SNOWFLAKE, new Vec3d(x + 0.5, sy + 1.1, z + 0.5), 1, 0.1, 0.01);
                }
                for (Entity e : living(w, p, around(center, next))) {
                    if (e instanceof LivingEntity le && le.squaredDistanceTo(center.x, le.getY(), center.z) <= next * next && waveHit.add(le.getId())) {
                        hurtCapped(w, p, le, 4f);
                        applyFrostHit(w, le, 4);
                    }
                }
                r = next;
            }
        });

        // --- eruption: real blue-ice spires burst in outward rings, taller toward the edge ---
        java.util.Set<Integer> spikeHit = new java.util.HashSet<>();
        int[] ringRadii = {4, 7, 10, 12};
        for (int ri = 0; ri < ringRadii.length; ri++) {
            final int rr = ringRadii[ri];
            final int baseH = 4 + ri * 2; // 4,6,8,10 — outer edge = a frozen crown
            ServerScheduler.runLater(22 + ri * 6, () -> {
                if (st.resolved) return;
                sound(w, center, SoundEvents.BLOCK_GLASS_BREAK, 1.3f, 0.6f + rr * 0.02f);
                sound(w, center, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
                for (double ang = 0; ang < Math.PI * 2; ang += Math.PI / 6) { // 12 spires/ring w/ gaps
                    int x = (int) Math.round(cx + Math.cos(ang) * rr);
                    int z = (int) Math.round(cz + Math.sin(ang) * rr);
                    if (!wallClear(w, cx, cy, cz, x, z)) {
                        continue;
                    }
                    int sy = surfaceY(w, x, z, cy);
                    if (sy == Integer.MIN_VALUE) {
                        continue;
                    }
                    int h = baseH + ((x + z) % 3); // slight variation
                    iceSpire(w, st, x, sy, z, h);
                }
                for (Entity e : living(w, p, around(center, rr + 1.5))) {
                    if (e instanceof LivingEntity le && spikeHit.add(le.getId())) {
                        hurtCapped(w, p, le, 8f);
                        setFrozen(w, le, 140);
                        st.frozen.add(le.getId());
                        iceShell(w, st, le); // encase frozen enemies
                    }
                }
            });
        }

        // --- frozen battlefield: low mist, falling snow overhead + creaks until shatter ---
        ServerScheduler.runTimer(50, 8, 22, () -> {
            if (st.resolved) {
                return;
            }
            particle(w, ParticleTypes.SNOWFLAKE, center.add(0, 0.4, 0), 24, st.radius * 0.5, 0.01);           // low mist
            particle(w, ParticleTypes.SNOWFLAKE, center.add(0, 6, 0), 30, st.radius * 0.6, 0.0);              // falling snow
            particle(w, dust(0xdff4ff, 1.2f), center.add(0, 0.3, 0), 16, st.radius * 0.45, 0.0);
            sound(w, center, SoundEvents.BLOCK_GLASS_STEP, 0.5f, 0.55f);
            sound(w, center, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 0.3f, 0.7f);                       // eerie wind-ish
        });
        ServerScheduler.runLater(230, () -> {
            AZState cur = ABSOLUTE_ZERO.get(u);
            if (cur == st && !st.resolved) {
                shatterAbsoluteZero(w, p, st);
            }
        });
    }

    private static void shatterAbsoluteZero(ServerWorld w, ServerPlayerEntity p, AZState st) {
        st.resolved = true;
        String u = p.getUuidAsString();
        Vec3d center = st.center;
        CryoLanceItem.cast(w, p, st.stack, "shatter", center, 20);
        sound(w, center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.7f);
        // fracture spikes outer -> center in sequence
        java.util.List<BlockPos> spikes = new ArrayList<>(st.spikes);
        spikes.sort(Comparator.comparingDouble((BlockPos b) -> -b.getSquaredDistance(center.x, center.y, center.z)));
        int per = Math.max(1, spikes.size() / 8);
        for (int i = 0; i < spikes.size(); i++) {
            final BlockPos sp = spikes.get(i);
            ServerScheduler.runLater(1 + i / per, () -> {
                BlockState old = st.originals.remove(sp);
                BlockState cur = w.getBlockState(sp);
                if (cur.isOf(Blocks.PACKED_ICE) || cur.isOf(Blocks.BLUE_ICE) || cur.isOf(Blocks.ICE)) {
                    w.setBlockState(sp, old != null ? old : Blocks.AIR.getDefaultState());
                }
                Vec3d v = new Vec3d(sp.getX() + 0.5, sp.getY() + 0.5, sp.getZ() + 0.5);
                particle(w, ParticleTypes.ITEM_SNOWBALL, v, 10, 0.3, 0.2);
                particle(w, dust(C_CRYO, 1.5f), v, 6, 0.3, 0.05);
                sound(w, v, SoundEvents.BLOCK_GLASS_BREAK, 0.5f, 1.2f);
            });
        }
        // shatter frozen enemies
        java.util.Set<Integer> hit = new java.util.HashSet<>();
        for (Entity e : living(w, p, around(center, st.radius))) {
            if (e instanceof LivingEntity le && hit.add(le.getId())) {
                float dmg = st.frozen.contains(le.getId()) ? 6f * 1.5f : 6f; // frozen shells take extra
                hurtCapped(w, p, le, dmg);
                impact(w, le.getPos().add(0, 1, 0), C_CRYO, SoundEvents.BLOCK_GLASS_BREAK);
                particle(w, ParticleTypes.ITEM_SNOWBALL, le.getPos().add(0, 1, 0), 24, 0.5, 0.25);
            }
        }
        // final deep boom + revert any remaining frost, then clear state
        int totalDelay = 4 + spikes.size() / per;
        ServerScheduler.runLater(totalDelay + 2, () -> {
            sound(w, center, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f);
            sound(w, center, SoundEvents.BLOCK_GLASS_BREAK, 1.6f, 0.6f);
            particle(w, ParticleTypes.ITEM_SNOWBALL, center.add(0, 0.8, 0), 160, 2.0, 0.6);
            particle(w, ParticleTypes.SNOWFLAKE, center.add(0, 0.8, 0), 120, 1.6, 0.3);
            particle(w, dust(C_CRYO, 2.4f), center.add(0, 0.8, 0), 80, 1.4, 0.1);
        });
        ServerScheduler.runLater(totalDelay + 40, () -> {
            st.originals.forEach((pos, old) -> {
                BlockState now = w.getBlockState(pos);
                if (now.isOf(Blocks.PACKED_ICE) || now.isOf(Blocks.BLUE_ICE) || now.isOf(Blocks.SNOW) || now.isOf(Blocks.ICE)) {
                    w.setBlockState(pos, old);
                }
            });
            ABSOLUTE_ZERO.remove(u, st);
        });
    }

    /** Rotating rune-ring "magic circle" drawn from particles on the ground. */
    private static void magicCircle(ServerWorld w, Vec3d center, double radius, double rot, int rgb) {
        double[] rings = {radius, radius * 0.66, radius * 0.33};
        for (int ri = 0; ri < rings.length; ri++) {
            double rr = rings[ri];
            double dir = ri % 2 == 0 ? rot : -rot;
            int pts = (int) (rr * 6);
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2 * i / pts + dir;
                Vec3d o = center.add(Math.cos(a) * rr, 0.15, Math.sin(a) * rr);
                particle(w, dust(rgb, 1.1f), o, 1, 0.0, 0.0);
            }
            // rune ticks
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 - dir;
                Vec3d o = center.add(Math.cos(a) * rr, 0.2, Math.sin(a) * rr);
                particle(w, ParticleTypes.END_ROD, o, 1, 0.02, 0.0);
            }
        }
    }

    private static void azPlace(ServerWorld w, AZState st, BlockPos pos, Block block) {
        BlockState old = w.getBlockState(pos);
        if (!old.isAir() && !old.isReplaceable() && old.getBlock() != Blocks.WATER) {
            return;
        }
        st.originals.putIfAbsent(pos, old);
        w.setBlockState(pos, block.getDefaultState());
    }

    /** One ice block that is also tracked as a "spike" so shatter fractures + reverts it. */
    private static void azSpike(ServerWorld w, AZState st, BlockPos pos, Block block) {
        BlockState old = w.getBlockState(pos);
        if (!old.isAir() && !old.isReplaceable() && old.getBlock() != Blocks.WATER) {
            return;
        }
        st.originals.putIfAbsent(pos, old);
        w.setBlockState(pos, block.getDefaultState());
        st.spikes.add(pos);
    }

    /** A jagged, leaning, pointed ice crystal: wide plus-base → tapering trunk → sharp ice tip. */
    private static void iceSpire(ServerWorld w, AZState st, int x, int sy, int z, int height) {
        // wide plus-shaped foot so it grows out of the ground, not a floating column
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            azSpike(w, st, new BlockPos(x + d[0], sy + 1, z + d[1]), Blocks.BLUE_ICE);
        }
        // pseudo-random lean + jag driven by position (deterministic, no RNG)
        int leanDx = ((x * 7 + z) % 3) - 1;
        int leanDz = ((x + z * 5) % 3) - 1;
        int lx = x, lz = z;
        int lean1 = Math.max(2, (int) (height * 0.4));
        int lean2 = Math.max(3, (int) (height * 0.72));
        for (int y = 2; y <= height; y++) {
            double frac = (double) y / height;
            Block b = frac < 0.45 ? Blocks.BLUE_ICE : (frac > 0.8 ? Blocks.ICE : Blocks.PACKED_ICE);
            azSpike(w, st, new BlockPos(lx, sy + y, lz), b);
            // side jags lower down for a rough crystalline silhouette
            if (frac < 0.6 && y % 2 == 0) {
                int sdx = ((x + y) % 2 == 0) ? 1 : -1;
                azSpike(w, st, new BlockPos(lx + sdx, sy + y, lz), Blocks.PACKED_ICE);
            }
            if (y == lean1 || y == lean2) { lx += leanDx; lz += leanDz; }
        }
        // sharp translucent tip a block above the trunk
        BlockPos tipPos = new BlockPos(lx, sy + height + 1, lz);
        azSpike(w, st, tipPos, Blocks.ICE);
        Vec3d tip = new Vec3d(lx + 0.5, sy + height + 1.5, lz + 0.5);
        particle(w, ParticleTypes.END_ROD, tip, 8, 0.15, 0.02);
        particle(w, dust(C_CRYO, 1.7f), tip, 10, 0.25, 0.0);
        particle(w, ParticleTypes.ITEM_SNOWBALL, new Vec3d(x + 0.5, sy + 1, z + 0.5), 18, 0.35, 0.2);
    }

    /** Encase a frozen enemy in a translucent ice shell (walls around it, not on it). */
    private static void iceShell(ServerWorld w, AZState st, LivingEntity le) {
        BlockPos feet = le.getBlockPos();
        for (int dy = 0; dy <= 1; dy++) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                azSpike(w, st, feet.add(d[0], dy, d[1]), Blocks.PACKED_ICE);
            }
        }
        particle(w, ParticleTypes.SNOWFLAKE, le.getPos().add(0, 1, 0), 20, 0.5, 0.05);
    }

    /** Vertical column of frost-light shooting up from a point. */
    private static void lightPillar(ServerWorld w, Vec3d base) {
        for (double y = 0; y < 16; y += 0.5) {
            Vec3d pt = base.add(0, y, 0);
            particle(w, ParticleTypes.END_ROD, pt, 2, 0.12, 0.02);
            particle(w, dust(C_CRYO, 1.8f), pt, 2, 0.18, 0.0);
            if (y < 6) {
                particle(w, ParticleTypes.SNOWFLAKE, pt, 2, 0.2, 0.02);
            }
        }
    }

    /** Bright flat ring racing outward along the ground over a few ticks. */
    private static void shockRing(ServerWorld w, Vec3d center, double maxR) {
        ServerScheduler.runTimer(1, 1, 8, new Runnable() {
            double r = 1;
            @Override
            public void run() {
                for (int i = 0; i < 48; i++) {
                    double a = Math.PI * 2 * i / 48;
                    Vec3d pt = center.add(Math.cos(a) * r, 0.25, Math.sin(a) * r);
                    particle(w, ParticleTypes.END_ROD, pt, 1, 0.0, 0.0);
                    particle(w, dust(C_CRYO, 1.5f), pt, 1, 0.05, 0.0);
                }
                r += maxR / 8.0;
            }
        });
    }

    /** Per-cast damage is already capped by the caller's hit-set; applies corrosion too. */
    private static void hurtCapped(ServerWorld w, ServerPlayerEntity p, LivingEntity le, float amount) {
        hurt(w, p, le, amount);
    }

    private static int surfaceY(ServerWorld w, int x, int z, int aroundY) {
        for (int y = aroundY + 4; y >= aroundY - 6; y--) {
            BlockPos bp = new BlockPos(x, y, z);
            BlockState bs = w.getBlockState(bp);
            // surface = a solid block (or water) whose top is open OR only blocked by grass/plants/snow
            if ((bs.isSolidBlock(w, bp) || bs.getBlock() == Blocks.WATER) && w.getBlockState(bp.up()).isReplaceable()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** True if a horizontal line from center to (x,z) isn't blocked by a solid wall. */
    private static boolean wallClear(ServerWorld w, int cx, int cy, int cz, int x, int z) {
        int steps = Math.max(Math.abs(x - cx), Math.abs(z - cz));
        if (steps == 0) {
            return true;
        }
        for (int s = 1; s < steps; s++) {
            double fx = cx + (x - cx) * (s / (double) steps);
            double fz = cz + (z - cz) * (s / (double) steps);
            BlockPos bp = new BlockPos((int) Math.round(fx), cy + 1, (int) Math.round(fz));
            if (w.getBlockState(bp).isSolidBlock(w, bp)) {
                return false;
            }
        }
        return true;
    }

    private static void overcharge(ServerWorld w, ServerPlayerEntity p, ItemStack stack) {
        if (!ready(p, stack, "overcharge", 200)) {
            return;
        }
        // 1s charge (rising pitch) then a piercing beam with per-pierce falloff
        for (int i = 0; i < 5; i++) {
            ServerScheduler.runLater(i * 4, () -> sound(w, p.getEyePos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6f, 1.0f + now() % 5 * 0.1f));
        }
        ServerScheduler.runLater(20, () -> {
            Vec3d eye = p.getEyePos();
            Vec3d dir = p.getRotationVector();
            sound(w, eye, SoundEvents.ITEM_FIRECHARGE_USE, 1.5f, 0.8f);
            Vec3d end = eye.add(dir.multiply(40));
            List<LivingEntity> line = new ArrayList<>();
            for (double d = 1; d < 40; d += 0.3) {
                Vec3d pt = eye.add(dir.multiply(d));
                BlockPos bp = BlockPos.ofFloored(pt.x, pt.y, pt.z);
                if (w.getBlockState(bp).isSolidBlock(w, bp)) {
                    end = pt;
                    break;
                }
                for (Entity e : living(w, p, around(pt, 0.9))) {
                    if (e instanceof LivingEntity le && !line.contains(le)) {
                        line.add(le);
                    }
                }
            }
            beamParticles(w, eye, end, C_LASER, ParticleTypes.END_ROD);
            particle(w, dust(C_LASER, 2.2f), end, 20, 0.3, 0.05);
            float dmg = 12f;
            for (LivingEntity le : line) {
                float d = dmg;
                if (CUT_MARK.getOrDefault(le.getId(), 0L) >= now()) {
                    d += 4f;
                    CUT_MARK.remove(le.getId());
                    particle(w, ParticleTypes.FLASH, le.getPos().add(0, 1, 0), 1, 0, 0);
                }
                hurt(w, p, le, d);
                impact(w, le.getPos().add(0, 1, 0), C_LASER, SoundEvents.ENTITY_GENERIC_EXPLODE.value());
                dmg = Math.max(4f, dmg - 3f); // falloff per pierce
            }
        });
    }
}
