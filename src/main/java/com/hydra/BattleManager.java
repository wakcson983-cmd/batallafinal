package com.hydra;

import com.hydra.entity.HydraEntity;
import com.hydra.entity.HydraHeadEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Estado global de la pelea contra HYDRA.
 *
 *  IDLE   -> nada pasa.
 *  ARMED  -> la ruleta-colores (Morado + HYDRA) ya invoco a HYDRA: cae del cielo y espera (invulnerable).
 *  ACTIVE -> /batalla final iniciar: musica, ataques, rugidos y eventos cada 2 minutos.
 */
public final class BattleManager {
    public enum State { IDLE, ARMED, ACTIVE }

    /** Ticks que se espera tras el giro de la ruleta antes de que caiga HYDRA (deja terminar la animacion). */
    public static final int SUMMON_DELAY_TICKS = 160;

    private static final Random RNG = new Random();

    private static State state = State.IDLE;
    private static ServerBossBar bar;
    private static ServerWorld world;
    private static Vec3d anchor = Vec3d.ZERO;
    private static UUID hydraId;
    private static final List<UUID> headIds = new ArrayList<>();
    private static boolean phase2;
    private static int battleTicks;
    private static int musicTimer;
    private static int summonDelay = -1;
    private static int emptyTicks;
    private static float damageMult = 1.0f;
    private static float fireRateMult = 1.0f;

    private BattleManager() {}

    // ------------------------------------------------------------ getters
    public static State state() { return state; }
    public static boolean isActive() { return state == State.ACTIVE; }
    public static Vec3d anchor() { return anchor; }
    public static int battleTicks() { return battleTicks; }
    public static float damageMult() { return damageMult; }
    public static float fireRateMult() { return fireRateMult; }
    public static void setDamageMult(float v) { damageMult = v; }
    public static void setFireRateMult(float v) { fireRateMult = v; }
    public static boolean isHeadTracked(UUID id) { return headIds.contains(id); }

    // ------------------------------------------------------------ init
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BattleManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> hardReset());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (state != State.IDLE && bar != null) bar.addPlayer(handler.player);
            if (state == State.ACTIVE) {
                handler.player.playSoundToPlayer(ModSounds.BATTLE_MUSIC, SoundCategory.MUSIC, 1.0f, 1.0f);
            }
        });
    }

    private static void hardReset() {
        state = State.IDLE;
        bar = null;
        world = null;
        hydraId = null;
        headIds.clear();
        phase2 = false;
        battleTicks = 0;
        summonDelay = -1;
        emptyTicks = 0;
        damageMult = 1.0f;
        fireRateMult = 1.0f;
        HydraEvents.forget();
        HydraFire.clear();
    }

    // ------------------------------------------------------------ tick
    private static void tick(MinecraftServer server) {
        if (RuletaHook.consume() && state == State.IDLE && summonDelay < 0) {
            summonDelay = SUMMON_DELAY_TICKS;
            broadcast(server, Text.literal("La ruleta ha hablado... algo enorme se acerca desde el cielo.")
                    .formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
        }
        if (summonDelay > 0) {
            summonDelay--;
            if (summonDelay == 0) {
                summonDelay = -1;
                summon(server);
            }
        }

        if (state == State.IDLE || world == null) return;

        if (bar != null && server.getTicks() % 20 == 0) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) bar.addPlayer(p);
        }

        boolean hydraAlive = false;
        float cur = 0.0f;
        float max = HydraEntity.MAX_HP;
        if (hydraId != null) {
            Entity e = world.getEntity(hydraId);
            if (e instanceof HydraEntity h && h.isAlive()) {
                hydraAlive = true;
                cur = h.getHealth();
                max = h.getMaxHealth();
            }
        }
        boolean anyHead = false;
        if (phase2) {
            cur = 0.0f;
            max = HydraHeadEntity.MAX_HP * 3.0f;
            for (UUID id : headIds) {
                Entity e = world.getEntity(id);
                if (e instanceof HydraHeadEntity hh && hh.isAlive()) {
                    anyHead = true;
                    cur += hh.getHealth();
                }
            }
        }
        if (bar != null) bar.setPercent(MathHelper.clamp(cur / max, 0.0f, 1.0f));

        if (!hydraAlive && !anyHead) {
            if (++emptyTicks > 100) {
                end(server, false);
                return;
            }
        } else {
            emptyTicks = 0;
        }

        if (state == State.ACTIVE) {
            battleTicks++;
            if (--musicTimer <= 0) {
                musicTimer = ModSounds.BATTLE_MUSIC_TICKS;
                playMusicAll(server);
            }
            HydraEvents.tick(server, battleTicks);
        }
    }

    // ------------------------------------------------------------ invocar
    /** Invoca a HYDRA: cae del cielo cerca de un jugador. Devuelve false si ya hay una pelea. */
    public static boolean summon(MinecraftServer server) {
        if (state != State.IDLE) return false;
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isAlive() && !p.isSpectator()) players.add(p);
        }
        if (players.isEmpty()) return false;
        ServerPlayerEntity p = players.get(RNG.nextInt(players.size()));
        ServerWorld w = p.getServerWorld();
        Vec3d c = p.getPos();

        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(c.x), (int) Math.floor(c.z));
        double sy = Math.min(Math.max(top, c.y) + 110.0, w.getTopY() - 12.0);

        HydraEntity h = ModEntities.HYDRA.create(w);
        if (h == null) return false;
        h.refreshPositionAndAngles(c.x + 24.0, sy, c.z + 24.0, 0.0f, 0.0f);
        w.spawnEntity(h);

        setup(w, h, c);
        lightning(w, c.add(14, 0, 14));
        lightning(w, c.add(-12, 0, 18));
        broadcastTitle(server,
                Text.literal("HYDRA").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
                Text.literal("Algo gigante cae del cielo...").formatted(Formatting.LIGHT_PURPLE));
        return true;
    }

    /** Un HYDRA creado con /summon (o que quedo de una partida anterior) se adopta como ARMED. */
    public static void adopt(HydraEntity h) {
        if (state != State.IDLE || summonDelay >= 0) return;
        if (!(h.getWorld() instanceof ServerWorld w)) return;
        setup(w, h, h.getPos());
    }

    private static void setup(ServerWorld w, HydraEntity h, Vec3d center) {
        state = State.ARMED;
        world = w;
        anchor = center;
        hydraId = h.getUuid();
        headIds.clear();
        phase2 = false;
        battleTicks = 0;
        emptyTicks = 0;
        damageMult = 1.0f;
        fireRateMult = 1.0f;
        if (bar != null) bar.clearPlayers();
        bar = new ServerBossBar(
                Text.literal("HYDRA").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
                BossBar.Color.PURPLE, BossBar.Style.NOTCHED_20);
        bar.setDarkenSky(true);
        bar.setPercent(1.0f);
        for (ServerPlayerEntity p : w.getServer().getPlayerManager().getPlayerList()) bar.addPlayer(p);
    }

    // ------------------------------------------------------------ batalla final
    /** @return null si todo bien, o el mensaje de error. */
    public static String startBattle(MinecraftServer server) {
        if (state == State.IDLE) {
            return "Primero gira la ruleta-colores en MORADO con el titulo HYDRA (o usa /hydra invocar).";
        }
        if (state == State.ACTIVE) return "La batalla final ya esta en curso.";
        state = State.ACTIVE;
        battleTicks = 0;
        musicTimer = ModSounds.BATTLE_MUSIC_TICKS;
        HydraEvents.forget();
        playMusicAll(server);
        broadcastTitle(server,
                Text.literal("BATALLA FINAL").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
                Text.literal("HYDRA ha despertado").formatted(Formatting.RED));
        if (world != null && hydraId != null) {
            Entity e = world.getEntity(hydraId);
            if (e instanceof HydraEntity h) h.roar(world);
        }
        return null;
    }

    public static void stopBattle(MinecraftServer server) {
        end(server, true);
    }

    /** Termina todo: quita eventos, musica, barra; opcionalmente borra las entidades. */
    public static void end(MinecraftServer server, boolean removeEntities) {
        HydraEvents.stopAll(server);
        stopMusic(server);
        if (bar != null) {
            bar.clearPlayers();
            bar.setVisible(false);
        }
        if (removeEntities && world != null) {
            if (hydraId != null) {
                Entity e = world.getEntity(hydraId);
                if (e != null) e.discard();
            }
            for (UUID id : headIds) {
                Entity e = world.getEntity(id);
                if (e != null) e.discard();
            }
        }
        hardReset();
    }

    // ------------------------------------------------------------ fases
    public static void onPhaseOneDown(ServerWorld w) {
        broadcastTitle(w.getServer(),
                Text.literal("HYDRA HA CAIDO...").formatted(Formatting.DARK_RED, Formatting.BOLD),
                Text.literal("pero algo se esta levantando").formatted(Formatting.GRAY));
    }

    public static void spawnHeads(ServerWorld w, Vec3d pos) {
        headIds.clear();
        for (int i = 0; i < 3; i++) {
            HydraHeadEntity hd = ModEntities.HYDRA_HEAD.create(w);
            if (hd == null) continue;
            hd.setHeadIndex(i);
            hd.refreshPositionAndAngles(pos.x + (i - 1) * 7.0, pos.y + 2.0, pos.z, 0.0f, 0.0f);
            headIds.add(hd.getUuid());
            w.spawnEntity(hd);
        }
        phase2 = true;
        if (bar != null) {
            bar.setName(Text.literal("HYDRA - Fase 2: las tres cabezas").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            bar.setPercent(1.0f);
        }
        lightning(w, pos);
        w.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 10.0f, 0.5f);
        broadcastTitle(w.getServer(),
                Text.literal("FASE 2").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
                Text.literal("Las tres cabezas se levantan").formatted(Formatting.LIGHT_PURPLE));
    }

    public static void onHeadDied(HydraHeadEntity dead) {
        if (state == State.IDLE || world == null) return;
        for (UUID id : headIds) {
            Entity e = world.getEntity(id);
            if (e instanceof HydraHeadEntity hh && hh != dead && hh.isAlive()) return;
        }
        victory(dead.getPos());
    }

    private static void victory(Vec3d at) {
        ServerWorld w = world;
        MinecraftServer server = w.getServer();
        broadcastTitle(server,
                Text.literal("HYDRA HA CAIDO").formatted(Formatting.GOLD, Formatting.BOLD),
                Text.literal("La batalla final ha terminado").formatted(Formatting.YELLOW));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        drop(w, at, new ItemStack(Items.NETHER_STAR, 3));
        drop(w, at, new ItemStack(Items.DRAGON_EGG, 1));
        drop(w, at, new ItemStack(Items.ELYTRA, 1));
        drop(w, at, new ItemStack(Items.NETHERITE_INGOT, 4));
        drop(w, at, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
        ExperienceOrbEntity.spawn(w, at, 12000);
        end(server, false);
    }

    private static void drop(ServerWorld w, Vec3d at, ItemStack stack) {
        w.spawnEntity(new ItemEntity(w, at.x, at.y + 1.0, at.z, stack));
    }

    // ------------------------------------------------------------ utilidades
    public static Entity bossEntity() {
        if (world == null) return null;
        if (hydraId != null) {
            Entity e = world.getEntity(hydraId);
            if (e != null && e.isAlive()) return e;
        }
        for (UUID id : headIds) {
            Entity e = world.getEntity(id);
            if (e != null && e.isAlive()) return e;
        }
        return null;
    }

    public static ServerPlayerEntity nearestPlayer(ServerWorld w, Vec3d from) {
        ServerPlayerEntity best = null;
        double bd = 220.0 * 220.0;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double d = p.squaredDistanceTo(from);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public static void lightning(ServerWorld w, Vec3d pos) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
        if (bolt == null) return;
        bolt.refreshPositionAfterTeleport(pos);
        bolt.setCosmetic(true);
        w.spawnEntity(bolt);
    }

    public static void playMusicAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.playSoundToPlayer(ModSounds.BATTLE_MUSIC, SoundCategory.MUSIC, 1.0f, 1.0f);
        }
    }

    public static void stopMusic(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(new StopSoundS2CPacket(ModSounds.BATTLE_ID, SoundCategory.MUSIC));
        }
    }

    public static void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().broadcast(text, false);
    }

    public static void broadcastTitle(MinecraftServer server, Text title, Text subtitle) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            p.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
    }
}
