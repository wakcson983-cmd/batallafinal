package com.hydra;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    public static final Identifier BATTLE_ID = Identifier.of(HydraMod.MOD_ID, "battle_music");
    public static SoundEvent BATTLE_MUSIC;

    /** Duracion exacta de battle.ogg en ticks (106.67 s). */
    public static final int BATTLE_MUSIC_TICKS = 2133;

    private ModSounds() {}

    public static void register() {
        BATTLE_MUSIC = Registry.register(Registries.SOUND_EVENT, BATTLE_ID, SoundEvent.of(BATTLE_ID));
    }
}
