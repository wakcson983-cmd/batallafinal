package com.hydra.client;

import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public final class ModModelLayers {
    public static final EntityModelLayer HYDRA = new EntityModelLayer(Identifier.of("hydra", "hydra"), "main");
    public static final EntityModelLayer HYDRA_HEAD = new EntityModelLayer(Identifier.of("hydra", "hydra_head"), "main");

    private ModModelLayers() {}
}
