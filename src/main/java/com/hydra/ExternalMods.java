package com.hydra;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Puente con los otros mods (momentoshardcore y ruletaazul) por reflexion:
 * HYDRA no necesita tenerlos para compilar ni para funcionar. Si un mod no esta
 * o su codigo es distinto, simplemente ese evento no se ofrece / no hace nada.
 */
public final class ExternalMods {
    private ExternalMods() {}

    public static boolean has(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    // ---------- momentoshardcore ----------
    public static boolean mhSet(MinecraftServer server, String field, boolean value) {
        try {
            Class<?> c = Class.forName("com.momentoshardcore.EventManager");
            Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            f.setBoolean(null, value);
            try {
                Method sync = c.getDeclaredMethod("syncAll", MinecraftServer.class);
                sync.setAccessible(true);
                sync.invoke(null, server);
            } catch (Throwable ignored) {
                // la sincronizacion con los clientes es opcional
            }
            return true;
        } catch (Throwable t) {
            HydraMod.LOG.warn("No se pudo cambiar '{}' de momentoshardcore: {}", field, t.toString());
            return false;
        }
    }

    // ---------- ruletaazul ----------
    public static boolean azulActivate(MinecraftServer server, int eventIndex, int seconds) {
        try {
            Class<?> c = Class.forName("com.ruletaazul.EventManager");
            Method m = c.getMethod("activate", MinecraftServer.class, int.class, int.class);
            m.invoke(null, server, eventIndex, seconds);
            return true;
        } catch (Throwable t) {
            HydraMod.LOG.warn("No se pudo activar el evento {} de ruletaazul: {}", eventIndex, t.toString());
            return false;
        }
    }

    public static void azulDeactivate(MinecraftServer server, int eventIndex) {
        try {
            Class<?> c = Class.forName("com.ruletaazul.EventManager");
            Method m = c.getMethod("deactivate", MinecraftServer.class, int.class);
            m.invoke(null, server, eventIndex);
        } catch (Throwable t) {
            HydraMod.LOG.warn("No se pudo desactivar el evento {} de ruletaazul: {}", eventIndex, t.toString());
        }
    }
}
