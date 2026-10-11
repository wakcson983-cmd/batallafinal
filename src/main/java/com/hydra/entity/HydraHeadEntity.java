package com.hydra.entity;

import com.hydra.BattleManager;
import com.hydra.HydraFire;
import net.minecraft.entity.EntityType;
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

/** HYDRA fase 2: una de las tres cabezas flotantes (cada una es una entidad). */
public class HydraHeadEntity extends MobEntity implements Monster {
    public static final float MAX_HP = 200.0f;
    public static final int ROAR_TICKS = 40;

    private static final TrackedData<Integer> ROAR = DataTracker.registerData(HydraHeadEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> BREATHING = DataTracker.registerData(HydraHeadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private enum Mode { RISE, HOLD, DESCEND, FIGHT }

    private Mode mode = Mode.RISE;
    private int modeTicks;
    private int headIndex;
    private double angle;
    private int lookSwap;
    private ServerPlayerEntity lookTarget;
    private int shotCooldown = 60;
    private int burstLeft;
    private int burstTimer;
    private int roarCooldown = 200;

    public HydraHeadEntity(EntityType<? extends HydraHeadEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPersistent();
        this.experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createHeadAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HP)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 160.0);
    }

    public void setHeadIndex(int i) {
        this.headIndex = i;
        this.angle = i * (Math.PI * 2.0 / 3.0);
        this.shotCooldown = 60 + i * 15;
        this.roarCooldown = 120 + i * 80;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ROAR, 0);
        builder.add(BREATHING, false);
    }

    public float getRoarProgress() {
        return this.dataTracker.get(ROAR) / (float) ROAR_TICKS;
    }

    public boolean isBreathing() {
        return this.dataTracker.get(BREATHING);
    }

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
        return 3.0f;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) return false;
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
            return super.damage(source, amount);
        }
        if (mode == Mode.RISE || mode == Mode.HOLD || !BattleManager.isActive()) return false;
        if (source.getAttacker() == null && source.getSource() == null) return false;
        if (source.getAttacker() instanceof HydraEntity || source.getAttacker() instanceof HydraHeadEntity) return false;
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!this.getWorld().isClient) BattleManager.onHeadDied(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        serverTick();
    }

    private void serverTick() {
        if (!(this.getWorld() instanceof ServerWorld world)) return;

        if (this.isDead()) {
            this.setVelocity(this.getVelocity().multiply(0.9, 1.0, 0.9).add(0.0, -0.05, 0.0));
            return;
        }
        if (!BattleManager.isHeadTracked(this.getUuid())) {
            this.discard();
            return;
        }

        int roar = this.dataTracker.get(ROAR);
        if (roar > 0) this.dataTracker.set(ROAR, roar - 1);

        modeTicks++;
        Vec3d anchor = BattleManager.anchor();
        ServerPlayerEntity near = BattleManager.nearestPlayer(world, this.getPos());
        double holdY = anchor.y + 75.0;

        switch (mode) {
            case RISE -> {
                Vec3d form = formation(anchor, 22.0);
                steerTo(new Vec3d(form.x, holdY, form.z), 1.1);
                lookAtPlayers(world, near, anchor);
                if (this.getY() >= holdY - 2.0) {
                    mode = Mode.HOLD;
                    modeTicks = 0;
                    roar(world);
                }
            }
            case HOLD -> {
                angle += 0.01;
                Vec3d form = formation(anchor, 22.0);
                steerTo(new Vec3d(form.x, holdY + Math.sin(this.age * 0.06) * 2.0, form.z), 0.3);
                lookAtPlayers(world, near, anchor);
                if (modeTicks >= 120) {
                    mode = Mode.DESCEND;
                    modeTicks = 0;
                }
            }
            case DESCEND -> {
                angle += 0.01;
                Vec3d c = near != null ? near.getPos() : anchor;
                Vec3d form = formation(c, 20.0);
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(c.x), (int) Math.floor(c.z));
                double goal = Math.max(c.y, top) + 24.0;
                steerTo(new Vec3d(form.x, goal, form.z), 0.22);
                lookAtPlayers(world, near, anchor);
                if (Math.abs(this.getY() - goal) < 2.5) {
                    mode = Mode.FIGHT;
                    modeTicks = 0;
                }
            }
            case FIGHT -> fight(world, near, anchor);
        }

        if (mode != Mode.RISE && --roarCooldown <= 0) {
            roarCooldown = 400 + this.getRandom().nextInt(300);
            roar(world);
        }
    }

    private void fight(ServerWorld world, ServerPlayerEntity near, Vec3d anchor) {
        angle += 0.012;
        Vec3d c = near != null ? near.getPos() : anchor;
        Vec3d form = formation(c, 20.0);
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(c.x), (int) Math.floor(c.z));
        double y = Math.max(c.y, top) + 22.0 + Math.sin(this.age * 0.05 + headIndex) * 3.0;
        steerTo(new Vec3d(form.x, y, form.z), 0.55);
        lookAtPlayers(world, near, anchor);

        if (near == null) return;
        if (burstLeft > 0) {
            if (--burstTimer <= 0) {
                burstTimer = 8;
                burstLeft--;
                Vec3d from = mouth();
                Vec3d dir = near.getEyePos().subtract(from).add(
                        (this.getRandom().nextDouble() - 0.5) * 3.0,
                        (this.getRandom().nextDouble() - 0.5) * 2.0,
                        (this.getRandom().nextDouble() - 0.5) * 3.0);
                HydraFire.shoot(world, this, from, dir, 1.2, 9.0f);
                if (burstLeft == 0) this.dataTracker.set(BREATHING, false);
            }
        } else if (--shotCooldown <= 0) {
            shotCooldown = (int) ((55 + headIndex * 12 + this.getRandom().nextInt(40)) / BattleManager.fireRateMult());
            burstLeft = 3;
            burstTimer = 6;
            this.dataTracker.set(BREATHING, true);
        }
    }

    private Vec3d formation(Vec3d center, double radius) {
        return new Vec3d(center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius);
    }

    private Vec3d mouth() {
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        float pitch = this.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d dir = new Vec3d(-MathHelper.sin(yaw) * MathHelper.cos(pitch), -MathHelper.sin(pitch),
                MathHelper.cos(yaw) * MathHelper.cos(pitch));
        return this.getPos().add(0.0, 4.4, 0.0).add(dir.multiply(6.0));
    }

    private void lookAtPlayers(ServerWorld world, ServerPlayerEntity near, Vec3d anchor) {
        // de vez en cuando mira a otro jugador distinto: "miran a todos"
        if (--lookSwap <= 0) {
            lookSwap = 40 + this.getRandom().nextInt(40);
            java.util.List<ServerPlayerEntity> list = world.getPlayers();
            lookTarget = list.isEmpty() ? null : list.get(this.getRandom().nextInt(list.size()));
        }
        Vec3d p;
        if (mode == Mode.FIGHT && near != null) p = near.getEyePos();
        else if (lookTarget != null && lookTarget.isAlive()) p = lookTarget.getEyePos();
        else p = anchor;
        lookAt(p);
    }

    private void lookAt(Vec3d p) {
        Vec3d from = this.getEyePos();
        double dx = p.x - from.x;
        double dy = p.y - from.y;
        double dz = p.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float ty = (float) (MathHelper.atan2(-dx, dz) * (180.0 / Math.PI));
        float tp = (float) (-(MathHelper.atan2(dy, h) * (180.0 / Math.PI)));
        float yaw = this.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(ty - this.getYaw()), -6.0f, 6.0f);
        float pitch = MathHelper.clamp(this.getPitch() + MathHelper.clamp(tp - this.getPitch(), -5.0f, 5.0f), -60.0f, 80.0f);
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
        this.setPitch(pitch);
    }

    private void steerTo(Vec3d desired, double maxSpeed) {
        Vec3d d = desired.subtract(this.getPos());
        double dist = d.length();
        Vec3d want = dist < 1.0E-3 ? Vec3d.ZERO : d.multiply(1.0 / dist).multiply(Math.min(maxSpeed, dist * 0.12 + 0.05));
        this.setVelocity(this.getVelocity().multiply(0.82).add(want.multiply(0.18)));
    }

    private void roar(ServerWorld world) {
        this.dataTracker.set(ROAR, ROAR_TICKS);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                SoundCategory.HOSTILE, 9.0f, 0.65f + headIndex * 0.1f);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 4.0, this.getZ(), 25, 2.5, 2.0, 2.5, 0.04);
    }
}
