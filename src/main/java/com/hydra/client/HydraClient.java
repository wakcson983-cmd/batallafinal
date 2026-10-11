package com.hydra.client;

import com.hydra.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class HydraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.HYDRA, HydraModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.HYDRA_HEAD, HydraHeadModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.HYDRA, HydraRenderer::new);
        EntityRendererRegistry.register(ModEntities.HYDRA_HEAD, HydraHeadRenderer::new);
    }
}
