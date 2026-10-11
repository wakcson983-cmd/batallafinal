package com.hydra;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HydraMod implements ModInitializer {
    public static final String MOD_ID = "hydra";
    public static final Logger LOG = LoggerFactory.getLogger("HYDRA");

    @Override
    public void onInitialize() {
        ModSounds.register();
        ModEntities.register();
        HydraFire.init();
        BattleManager.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                HydraCommands.register(dispatcher));
        LOG.info("HYDRA cargado. Gira la ruleta-colores en MORADO con el titulo HYDRA para invocarla.");
    }
}
