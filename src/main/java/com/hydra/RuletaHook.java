package com.hydra;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detecta cuando el mod ruleta-colores anuncia un giro: color MORADO + titulo HYDRA.
 * Lee el paquete "ruleta_colores:spin_play" (record color/title/text) por reflexion.
 */
public final class RuletaHook {
    private static final AtomicBoolean TRIGGER = new AtomicBoolean(false);
    private static final AtomicLong LAST = new AtomicLong(0);
    private static final int MORADO_FALLBACK = 2;

    private RuletaHook() {}

    /** Se llama desde el mixin cada vez que el servidor manda un paquete custom. */
    public static void inspect(CustomPayload payload) {
        try {
            Identifier id = payload.getId().id();
            if (!"ruleta_colores".equals(id.getNamespace()) || !"spin_play".equals(id.getPath())) return;

            Class<?> c = payload.getClass();
            Method mColor = c.getMethod("color");
            Method mTitle = c.getMethod("title");
            mColor.setAccessible(true);
            mTitle.setAccessible(true);
            int color = (Integer) mColor.invoke(payload);
            String title = String.valueOf(mTitle.invoke(payload));

            if (!isPurple(color)) return;
            if (title == null || !title.trim().equalsIgnoreCase("HYDRA")) return;

            // el servidor manda el mismo paquete a cada jugador: solo cuenta una vez por giro
            long now = System.currentTimeMillis();
            long prev = LAST.get();
            if (now - prev < 6000) return;
            if (LAST.compareAndSet(prev, now)) TRIGGER.set(true);
        } catch (Throwable t) {
            // nunca romper el envio de paquetes
        }
    }

    private static boolean isPurple(int color) {
        try {
            Class<?> e = Class.forName("com.ruletacolores.RuletaColor");
            Object[] all = e.getEnumConstants();
            if (color >= 0 && color < all.length) {
                return ((Enum<?>) all[color]).name().equals("MORADO");
            }
        } catch (Throwable ignored) {
        }
        return color == MORADO_FALLBACK;
    }

    public static boolean consume() {
        return TRIGGER.getAndSet(false);
    }
}
