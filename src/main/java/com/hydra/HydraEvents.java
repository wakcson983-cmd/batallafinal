package com.hydra;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Un evento nuevo cada 2 minutos (2400 ticks) que dura 1 minuto (1200 ticks).
 * Mezcla eventos propios con los de momentoshardcore y ruletaazul (si estan instalados).
 */
public final class HydraEvents {
    public static final int PERIOD = 2400;
    public static final int DURATION = 1200;

    private static final Random RNG = new Random();

    private static final class Ev {
        final String name;
        final String desc;
        final Consumer<MinecraftServer> start;
        final Consumer<MinecraftServer> stop;
        final BiConsumer<MinecraftServer, Integer> tick;

        Ev(String name, String desc, Consumer<MinecraftServer> start, Consumer<MinecraftServer> stop,
           BiConsumer<MinecraftServer, Integer> tick) {
            this.name = name;
            this.desc = desc;
            this.start = start;
            this.stop = stop;
            this.tick = tick;
        }
    }

    private static Ev current;
    private static int startedAt;
    private static String lastName = "";
    private static ServerBossBar evBar;

    private HydraEvents() {}

    /** Olvida todo sin ejecutar nada (nueva pelea / reinicio). */
    public static void forget() {
        current = null;
        lastName = "";
        if (evBar != null) {
            evBar.clearPlayers();
            evBar.setVisible(false);
            evBar = null;
        }
    }

    public static void stopAll(MinecraftServer server) {
        if (current != null) stop(server);
        forget();
    }

    public static boolean running() {
        return current != null;
    }

    public static void tick(MinecraftServer server, int bt) {
        if (current == null && bt > 0 && bt % PERIOD == 0) start(server, bt);
        if (current != null) {
            int el = bt - startedAt;
            if (current.tick != null) current.tick.accept(server, el);
            if (el % 20 == 0) updateBar(server, el);
            if (el >= DURATION) stop(server);
        }
    }

    /** Para probar sin esperar 2 minutos (/hydra evento). */
    public static boolean forceStart(MinecraftServer server) {
        if (current != null) return false;
        start(server, BattleManager.battleTicks());
        return true;
    }

    private static void start(MinecraftServer server, int bt) {
        List<Ev> pool = buildPool(server);
        List<Ev> options = new ArrayList<>();
        for (Ev e : pool) if (!e.name.equals(lastName)) options.add(e);
        if (options.isEmpty()) options = pool;
        Ev ev = options.get(RNG.nextInt(options.size()));
        current = ev;
        startedAt = bt;
        lastName = ev.name;
        try {
            ev.start.accept(server);
        } catch (Throwable t) {
            HydraMod.LOG.warn("Fallo al iniciar el evento {}: {}", ev.name, t.toString());
        }
        BattleManager.broadcastTitle(server,
                Text.literal("EVENTO: " + ev.name).formatted(Formatting.RED, Formatting.BOLD),
                Text.literal(ev.desc).formatted(Formatting.GOLD));
        BattleManager.broadcast(server, Text.literal("[HYDRA] Evento: " + ev.name + " - " + ev.desc + " (1 minuto)")
                .formatted(Formatting.DARK_PURPLE));
        if (evBar != null) evBar.clearPlayers();
        evBar = new ServerBossBar(Text.literal("EVENTO: " + ev.name), BossBar.Color.RED, BossBar.Style.PROGRESS);
        updateBar(server, 0);
    }

    private static void stop(MinecraftServer server) {
        Ev ev = current;
        current = null;
        if (ev != null) {
            try {
                ev.stop.accept(server);
            } catch (Throwable t) {
                HydraMod.LOG.warn("Fallo al terminar el evento {}: {}", ev.name, t.toString());
            }
            BattleManager.broadcast(server, Text.literal("[HYDRA] El evento \"" + ev.name + "\" ha terminado.")
                    .formatted(Formatting.GRAY));
        }
        if (evBar != null) {
            evBar.clearPlayers();
            evBar.setVisible(false);
            evBar = null;
        }
    }

    private static void updateBar(MinecraftServer server, int el) {
        if (evBar == null || current == null) return;
        int left = Math.max(0, (DURATION - el) / 20);
        evBar.setName(Text.literal("EVENTO: " + current.name + "  -  " + (left / 60) + ":" + String.format("%02d", left % 60))
                .formatted(Formatting.RED, Formatting.BOLD));
        evBar.setPercent(Math.max(0f, Math.min(1f, (DURATION - el) / (float) DURATION)));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) evBar.addPlayer(p);
    }

    // ------------------------------------------------------------ catalogo de eventos
    private static List<Ev> buildPool(MinecraftServer server) {
        List<Ev> pool = new ArrayList<>();

        // --- cambiar la dificultad del juego
        final Difficulty[] saved1 = new Difficulty[1];
        pool.add(new Ev("Dificultad Pesadilla", "La dificultad sube a DIFICIL y HYDRA pega 50% mas fuerte.",
                s -> {
                    saved1[0] = s.getOverworld().getDifficulty();
                    s.setDifficulty(Difficulty.HARD, true);
                    BattleManager.setDamageMult(1.5f);
                },
                s -> {
                    s.setDifficulty(saved1[0] == null ? Difficulty.NORMAL : saved1[0], true);
                    BattleManager.setDamageMult(1.0f);
                }, null));

        final Difficulty[] saved2 = new Difficulty[1];
        pool.add(new Ev("Tregua", "La dificultad baja a FACIL y HYDRA dispara la mitad de rapido.",
                s -> {
                    saved2[0] = s.getOverworld().getDifficulty();
                    s.setDifficulty(Difficulty.EASY, true);
                    BattleManager.setFireRateMult(0.5f);
                },
                s -> {
                    s.setDifficulty(saved2[0] == null ? Difficulty.NORMAL : saved2[0], true);
                    BattleManager.setFireRateMult(1.0f);
                }, null));

        // --- PvP
        final boolean[] pvp = new boolean[1];
        pool.add(new Ev("PvP forzado", "Los jugadores pueden pegarse entre ellos.",
                s -> {
                    pvp[0] = s.isPvpEnabled();
                    s.setPvpEnabled(true);
                },
                s -> s.setPvpEnabled(pvp[0]), null));

        // --- teletransporte
        pool.add(new Ev("Teletransporte", "Todos los jugadores son lanzados a lugares al azar.",
                s -> {
                    for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                        if (!p.isAlive() || p.isSpectator()) continue;
                        ServerWorld w = p.getServerWorld();
                        Vec3d c = BattleManager.anchor();
                        double a = RNG.nextDouble() * Math.PI * 2;
                        double r = 15 + RNG.nextDouble() * 30;
                        double x = c.x + Math.cos(a) * r;
                        double z = c.z + Math.sin(a) * r;
                        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                        p.teleport(w, x, y + 1.0, z, p.getYaw(), p.getPitch());
                    }
                },
                s -> { }, null));

        // --- tormenta electrica
        pool.add(new Ev("Tormenta electrica", "Caen rayos alrededor de todos los jugadores.",
                s -> s.getOverworld().setWeather(0, DURATION + 100, true, true),
                s -> s.getOverworld().setWeather(6000, 0, false, false),
                (s, el) -> {
                    if (el % 30 != 0) return;
                    for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                        if (!p.isAlive() || p.isSpectator()) continue;
                        ServerWorld w = p.getServerWorld();
                        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
                        if (bolt == null) continue;
                        bolt.refreshPositionAfterTeleport(p.getX() + (RNG.nextDouble() - 0.5) * 12,
                                p.getY(), p.getZ() + (RNG.nextDouble() - 0.5) * 12);
                        w.spawnEntity(bolt);
                    }
                }));

        // --- oscuridad
        pool.add(new Ev("Oscuridad", "La visibilidad desaparece.",
                s -> { },
                s -> { },
                (s, el) -> {
                    if (el % 20 != 0) return;
                    for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false));
                    }
                }));

        // --- lluvia de fuego morado
        pool.add(new Ev("Lluvia de fuego morado", "Bolas de fuego morado caen del cielo sobre todos.",
                s -> { },
                s -> { },
                (s, el) -> {
                    if (el % 15 != 0) return;
                    Entity owner = BattleManager.bossEntity();
                    if (owner == null) return;
                    for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                        if (!p.isAlive() || p.isSpectator()) continue;
                        ServerWorld w = p.getServerWorld();
                        if (w != owner.getWorld()) continue;
                        Vec3d from = new Vec3d(p.getX() + (RNG.nextDouble() - 0.5) * 16, p.getY() + 45,
                                p.getZ() + (RNG.nextDouble() - 0.5) * 16);
                        Vec3d to = new Vec3d(p.getX() + (RNG.nextDouble() - 0.5) * 6, p.getY(),
                                p.getZ() + (RNG.nextDouble() - 0.5) * 6);
                        HydraFire.shoot(w, owner, from, to.subtract(from), 1.4, 7.0f);
                    }
                }));

        // --- momentoshardcore (si esta instalado)
        if (ExternalMods.has("momentoshardcore")) {
            pool.add(mh("acid", "Lluvia acida (momentosHARDCORE)", "Llueve acido: busca techo."));
            pool.add(mh("magma", "Magma (momentosHARDCORE)", "Aparece magma por todas partes."));
            pool.add(mh("creepers", "Creepers (momentosHARDCORE)", "Caen creepers sobre los jugadores."));
            pool.add(mh("hideHealth", "Vida oculta (momentosHARDCORE)", "Nadie puede ver su vida."));
        }

        // --- ruletaazul (si esta instalado)
        if (ExternalMods.has("ruletaazul")) {
            String[] names = {"Terremoto nivel 10", "Huracan polar", "Lluvia electrica", "Incendios forestales",
                    "Lluvia de meteoritos", "Niebla mortal", "Horda zombi", "Glaciacion"};
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                pool.add(new Ev(names[i] + " (ruletaazul)", "Evento de la Ruleta Azul.",
                        s -> ExternalMods.azulActivate(s, idx, DURATION / 20),
                        s -> ExternalMods.azulDeactivate(s, idx), null));
            }
        }
        return pool;
    }

    private static Ev mh(String field, String name, String desc) {
        return new Ev(name, desc,
                s -> ExternalMods.mhSet(s, field, true),
                s -> ExternalMods.mhSet(s, field, false), null);
    }
}
