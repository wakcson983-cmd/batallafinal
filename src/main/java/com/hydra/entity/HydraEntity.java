package com.hydra.entity;

import com.hydra.BattleManager;
import com.hydra.HydraFire;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** HYDRA fase 1: dragon gigante de tres cabezas, 600 de vida (3x el Ender Dragon). */
public class HydraEntity extends MobEntity implements Monster {
    public static final float MAX_HP = 600.0f;
    public static final int ROAR_TICKS = 50;

    private static final TrackedData<Integer> ROAR = DataTracker.registerData(HydraEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> BREATHING = DataTracker.registerData(HydraEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> DYING = DataTracker.registerData(HydraEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private enum Mode { DESCEND, ORBIT, BREATH, DIVE }

    private Mode mode = Mode.DESCEND;
    private int modeTicks;
    private int attackCooldown = 80;
    private int diveCooldown = 500;
    private int roarCooldown = 260;
    private int contactCooldown;
    private double orbitAngle;
    private boolean transitioning;
    private int transitionTicks;

    public HydraEntity(EntityType<? extends HydraEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPersistent();
        this.experiencePoints = 0;
        this.orbitAngle = this.getRandom().nextDouble() * Math.PI * 2.0;
    }

    public static DefaultAttributeContainer.Builder createHydraAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HP)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 160.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ROAR, 0);
        builder.add(BREATHING, false);
        builder.add(DYING, false);
    }

    // ---------------------------------------------------------- datos para el modelo
    public float getRoarProgress() {
        return this.dataTracker.get(ROAR) / (float) ROAR_TICKS;
    }

    public boolean isBreathing() {
        return this.dataTracker.get(BREATHING);
    }

    public boolean isDyingAnim() {
        return this.dataTracker.get(DYING);
    }

    // ---------------------------------------------------------- comportamiento base
    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (!this.getWorld().isClient) {
            this.move(MovementType.SELF, this.getVelocity());
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ENDER_DRAGON_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 4.0f;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) return false;
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
            return super.damage(source, amount);
        }
        if (transitioning || !BattleManager.isActive()) return false;
        if (source.getAttacker() == null && source.getSource() == null) return false;
        if (source.getAttacker() instanceof HydraEntity || source.getAttacker() instanceof HydraHeadEntity) return false;
        if (this.getHealth() - amount <= 0.0f) {
            if (this.getWorld() instanceof ServerWorld sw) startTransition(sw);
            return true;
        }
        return super.damage(source, amount);
    }

    // ---------------------------------------------------------- tick
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        serverTick();
    }

    private void serverTick() {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        BattleManager.adopt(this);

        int roar = this.dataTracker.get(ROAR);
        if (roar > 0) this.dataTracker.set(ROAR, roar - 1);

        if (transitioning) {
            tickTransition(world);
            return;
        }

        boolean battle = BattleManager.isActive();
        ServerPlayerEntity target = battle ? BattleManager.nearestPlayer(world, this.getPos()) : null;
        modeTicks++;

        switch (mode) {
            case DESCEND -> doDescend(world);
            case ORBIT -> doOrbit(world, target, battle);
            case BREATH -> doBreath(world, target);
            case DIVE -> doDive(target);
        }

        // rugido cada cierto tiempo
        if (mode != Mode.DESCEND && --roarCooldown <= 0) {
            roarCooldown = 350 + this.getRandom().nextInt(450);
            roar(world);
        }

        // golpe de alas / cuerpo
        if (battle && --contactCooldown <= 0) {
            contactCooldown = 12;
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                    this.getBoundingBox().expand(2.0, 1.0, 2.0), HydraFire::isTarget)) {
                e.damage(world.getDamageSources().mobAttack(this), 14.0f * BattleManager.damageMult());
                e.takeKnockback(1.6, this.getX() - e.getX(), this.getZ() - e.getZ());
            }
        }
    }

    // ---------------------------------------------------------- caida del cielo
    private void doDescend(ServerWorld world) {
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ());
        double wanted = Math.max(top, BattleManager.anchor().y) + 36.0;
        double dy = this.getY() - wanted;
        if (dy <= 1.0) {
            mode = Mode.ORBIT;
            modeTicks = 0;
            landingFx(world);
            return;
        }
        Vec3d a = BattleManager.anchor();
        double vx = MathHelper.clamp((a.x + 20.0 - this.getX()) * 0.02, -0.4, 0.4);
        double vz = MathHelper.clamp((a.z + 20.0 - this.getZ()) * 0.02, -0.4, 0.4);
        this.setVelocity(vx, -MathHelper.clamp(dy * 0.07, 0.35, 1.6), vz);
        applyYaw(this.getYaw() + 2.0f);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 4.0, this.getZ(), 14, 5.0, 2.5, 5.0, 0.03);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 4.0, this.getZ(), 10, 5.0, 2.5, 5.0, 0.2);
    }

    private void landingFx(ServerWorld world) {
        BattleManager.lightning(world, new Vec3d(this.getX(), this.getY() - 30.0, this.getZ()));
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WITHER_SPAWN,
                SoundCategory.HOSTILE, 10.0f, 0.5f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 3.0, this.getZ(), 3, 4.0, 2.0, 4.0, 0.0);
        roar(world);
    }

    // ---------------------------------------------------------- movimiento
    private void doOrbit(ServerWorld world, ServerPlayerEntity target, boolean battle) {
        double speed = battle ? 0.95 : 0.55;
        double radius = battle ? 30.0 : 38.0;
        double alt = battle ? 24.0 : 36.0;
        orbitAngle += battle ? 0.028 : 0.015;

        Vec3d center = target != null ? target.getPos() : BattleManager.anchor();
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(center.x), (int) Math.floor(center.z));
        double cy = Math.max(center.y, top) + alt + Math.sin(this.age * 0.05) * 3.0;
        Vec3d desired = new Vec3d(center.x + Math.cos(orbitAngle) * radius, cy, center.z + Math.sin(orbitAngle) * radius);
        steerTo(desired, speed);

        if (target != null) {
            faceTowards(target.getX(), target.getZ());
        } else {
            Vec3d v = this.getVelocity();
            if (v.horizontalLengthSquared() > 1.0E-4) faceTowards(this.getX() + v.x, this.getZ() + v.z);
        }

        if (battle && target != null) {
            attackCooldown--;
            diveCooldown--;
            if (diveCooldown <= 0 && this.distanceTo(target) < 70.0f) {
                mode = Mode.DIVE;
                modeTicks = 0;
                diveCooldown = 360 + this.getRandom().nextInt(200);
                world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                        SoundCategory.HOSTILE, 8.0f, 0.7f);
            } else if (attackCooldown <= 0) {
                mode = Mode.BREATH;
                modeTicks = 0;
                attackCooldown = (int) ((90 + this.getRandom().nextInt(60)) / BattleManager.fireRateMult());
                this.dataTracker.set(BREATHING, true);
            }
        }
    }

    private void doBreath(ServerWorld world, ServerPlayerEntity target) {
        this.setVelocity(this.getVelocity().multiply(0.9));
        if (target == null) {
            endBreath();
            return;
        }
        faceTowards(target.getX(), target.getZ());
        if (modeTicks >= 10 && modeTicks % 6 == 0) {
            Vec3d aim = target.getEyePos();
            for (int n = 0; n < 3; n++) {
                Vec3d from = mouth(n);
                Vec3d dir = aim.subtract(from).add(
                        (this.getRandom().nextDouble() - 0.5) * 5.0,
                        (this.getRandom().nextDouble() - 0.5) * 3.0,
                        (this.getRandom().nextDouble() - 0.5) * 5.0);
                HydraFire.shoot(world, this, from, dir, 1.25, 9.0f);
            }
        }
        if (modeTicks >= 70) endBreath();
    }

    private void endBreath() {
        mode = Mode.ORBIT;
        modeTicks = 0;
        this.dataTracker.set(BREATHING, false);
    }

    private void doDive(ServerPlayerEntity target) {
        if (target == null || modeTicks > 60) {
            mode = Mode.ORBIT;
            modeTicks = 0;
            return;
        }
        steerTo(target.getEyePos().add(0.0, 2.0, 0.0), 1.5);
        faceTowards(target.getX(), target.getZ());
        if (this.distanceTo(target) < 9.0f || modeTicks > 55) {
            mode = Mode.ORBIT;
            modeTicks = 0;
            this.setVelocity(this.getVelocity().add(0.0, 0.9, 0.0));
        }
    }

    private Vec3d mouth(int n) {
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d fwd = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
        Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0.0, -MathHelper.sin(yaw));
        double f = n == 1 ? 16.0 : 15.0;
        double side = (n - 1) * 5.5;
        double up = n == 1 ? 10.5 : 9.5;
        return this.getPos().add(fwd.multiply(f)).add(right.multiply(side)).add(0.0, up, 0.0);
    }

    private void steerTo(Vec3d desired, double maxSpeed) {
        Vec3d d = desired.subtract(this.getPos());
        double dist = d.length();
        Vec3d want = dist < 1.0E-3 ? Vec3d.ZERO : d.multiply(1.0 / dist).multiply(Math.min(maxSpeed, dist * 0.12 + 0.05));
        this.setVelocity(this.getVelocity().multiply(0.82).add(want.multiply(0.18)));
    }

    private void faceTowards(double x, double z) {
        double dx = x - this.getX();
        double dz = z - this.getZ();
        float want = (float) (MathHelper.atan2(-dx, dz) * (180.0 / Math.PI));
        float diff = MathHelper.wrapDegrees(want - this.getYaw());
        applyYaw(this.getYaw() + MathHelper.clamp(diff, -4.0f, 4.0f));
    }

    private void applyYaw(float yaw) {
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
        this.setPitch(0.0f);
    }

    public void roar(ServerWorld world) {
        this.dataTracker.set(ROAR, ROAR_TICKS);
        world.playSound(null, this.getX(), this.getY() + 8.0, this.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                SoundCategory.HOSTILE, 12.0f, 0.5f);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 8.0, this.getZ(), 40, 4.0, 3.0, 4.0, 0.05);
    }

    // ---------------------------------------------------------- "muerte" de la fase 1
    private void startTransition(ServerWorld world) {
        transitioning = true;
        transitionTicks = 0;
        this.dataTracker.set(DYING, true);
        this.dataTracker.set(BREATHING, false);
        this.setHealth(1.0f);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_DEATH,
                SoundCategory.HOSTILE, 14.0f, 0.6f);
        BattleManager.onPhaseOneDown(world);
    }

    private void tickTransition(ServerWorld world) {
        transitionTicks++;
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ());
        boolean grounded = this.getY() <= top + 2.5;
        this.setVelocity(0.0, grounded ? 0.0 : -0.45, 0.0);
        applyYaw(this.getYaw() + (transitionTicks % 4 < 2 ? 2.0f : -2.0f));
        if (transitionTicks % 5 == 0) {
            world.spawnParticles(ParticleTypes.EXPLOSION, this.getX() + (this.getRandom().nextDouble() - 0.5) * 12,
                    this.getY() + 3.0, this.getZ() + (this.getRandom().nextDouble() - 0.5) * 12, 2, 1.0, 1.0, 1.0, 0.0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 3.0, this.getZ(), 30, 6.0, 3.0, 6.0, 0.3);
            world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.HOSTILE, 4.0f, 0.6f);
        }
        if (transitionTicks >= 120) {
            BattleManager.spawnHeads(world, this.getPos());
            this.discard();
        }
    }
}
