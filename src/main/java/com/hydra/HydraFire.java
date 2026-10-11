package com.hydra;

import com.hydra.entity.HydraEntity;
import com.hydra.entity.HydraHeadEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fuego morado de HYDRA: bolas de fuego que se mueven solas (sin entidad extra),
 * explotan al chocar y dejan charcos de fuego morado.
 */
public final class HydraFire {
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.62f, 0.10f, 1.0f), 2.2f);
    private static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.22f, 0.0f, 0.38f), 1.7f);

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Patch> PATCHES = new ArrayList<>();

    private static final class Bolt {
        final ServerWorld world;
        final Entity owner;
        Vec3d pos;
        final Vec3d vel;
        final float damage;
        int life = 140;

        Bolt(ServerWorld world, Entity owner, Vec3d pos, Vec3d vel, float damage) {
            this.world = world;
            this.owner = owner;
            this.pos = pos;
            this.vel = vel;
            this.damage = damage;
        }
    }

    private static final class Patch {
        final ServerWorld world;
        final Vec3d pos;
        final double radius;
        int life;

        Patch(ServerWorld world, Vec3d pos, double radius, int life) {
            this.world = world;
            this.pos = pos;
            this.radius = radius;
            this.life = life;
        }
    }

    private HydraFire() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    public static void clear() {
        BOLTS.clear();
        PATCHES.clear();
    }

    public static boolean isTarget(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (e instanceof HydraEntity || e instanceof HydraHeadEntity) return false;
        return !(e instanceof PlayerEntity p && p.isCreative());
    }

    /** Dispara una bola de fuego morado. */
    public static void shoot(ServerWorld world, Entity owner, Vec3d from, Vec3d dir, double speed, float damage) {
        if (owner == null || dir.lengthSquared() < 1.0E-6) return;
        BOLTS.add(new Bolt(world, owner, from, dir.normalize().multiply(speed), damage));
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDER_DRAGON_SHOOT,
                SoundCategory.HOSTILE, 4.0f, 0.55f);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, from.x, from.y, from.z, 10, 0.5, 0.5, 0.5, 0.03);
    }

    private static void tick() {
        Iterator<Bolt> it = BOLTS.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            b.life--;
            Vec3d next = b.pos.add(b.vel);
            boolean hit = false;
            Vec3d impact = next;

            BlockHitResult bhr = b.world.raycast(new RaycastContext(
                    b.pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, b.owner));
            if (bhr.getType() != HitResult.Type.MISS) {
                hit = true;
                impact = bhr.getPos();
            }
            if (!hit) {
                Box box = new Box(b.pos, next).expand(1.3);
                if (!b.world.getEntitiesByClass(LivingEntity.class, box, HydraFire::isTarget).isEmpty()) {
                    hit = true;
                }
            }

            // estela de fuego morado
            Vec3d mid = b.pos.add(next).multiply(0.5);
            b.world.spawnParticles(PURPLE, next.x, next.y, next.z, 8, 0.35, 0.35, 0.35, 0.01);
            b.world.spawnParticles(DARK, mid.x, mid.y, mid.z, 4, 0.3, 0.3, 0.3, 0.01);
            b.world.spawnParticles(ParticleTypes.DRAGON_BREATH, next.x, next.y, next.z, 4, 0.25, 0.25, 0.25, 0.02);
            b.world.spawnParticles(ParticleTypes.WITCH, next.x, next.y, next.z, 2, 0.3, 0.3, 0.3, 0.0);

            if (hit) {
                explode(b, impact);
                it.remove();
            } else if (b.life <= 0) {
                it.remove();
            } else {
                b.pos = next;
            }
        }

        Iterator<Patch> pit = PATCHES.iterator();
        while (pit.hasNext()) {
            Patch p = pit.next();
            p.life--;
            if (p.life % 3 == 0) {
                p.world.spawnParticles(PURPLE, p.pos.x, p.pos.y + 0.3, p.pos.z, 10, p.radius * 0.45, 0.25, p.radius * 0.45, 0.02);
                p.world.spawnParticles(ParticleTypes.DRAGON_BREATH, p.pos.x, p.pos.y + 0.2, p.pos.z, 4, p.radius * 0.4, 0.2, p.radius * 0.4, 0.01);
            }
            if (p.life % 10 == 0) {
                Box area = new Box(p.pos.x - p.radius, p.pos.y - 1.0, p.pos.z - p.radius,
                        p.pos.x + p.radius, p.pos.y + 2.5, p.pos.z + p.radius);
                for (LivingEntity e : p.world.getEntitiesByClass(LivingEntity.class, area, HydraFire::isTarget)) {
                    e.setOnFireFor(4);
                    e.damage(p.world.getDamageSources().magic(), 2.0f * BattleManager.damageMult());
                }
            }
            if (p.life <= 0) pit.remove();
        }
    }

    private static void explode(Bolt b, Vec3d at) {
        ServerWorld w = b.world;
        w.createExplosion(b.owner, at.x, at.y, at.z, 2.6f, false, World.ExplosionSourceType.MOB);
        w.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.8, 0.8, 0.8, 0.0);
        w.spawnParticles(PURPLE, at.x, at.y + 0.5, at.z, 70, 1.8, 1.2, 1.8, 0.08);
        w.spawnParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 40, 1.5, 1.0, 1.5, 0.25);

        Box area = new Box(at.x - 4.5, at.y - 4.5, at.z - 4.5, at.x + 4.5, at.y + 4.5, at.z + 4.5);
        DamageSource src = w.getDamageSources().indirectMagic(b.owner, b.owner);
        float dmg = b.damage * BattleManager.damageMult();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, area, HydraFire::isTarget)) {
            e.damage(src, dmg);
            e.setOnFireFor(8);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 0));
        }
        PATCHES.add(new Patch(w, at, 3.2, 100));
    }
}
