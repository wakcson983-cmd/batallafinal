package com.hydra;

import com.hydra.entity.HydraEntity;
import com.hydra.entity.HydraHeadEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static EntityType<HydraEntity> HYDRA;
    public static EntityType<HydraHeadEntity> HYDRA_HEAD;

    private ModEntities() {}

    public static void register() {
        HYDRA = Registry.register(Registries.ENTITY_TYPE, Identifier.of(HydraMod.MOD_ID, "hydra"),
                EntityType.Builder.create(HydraEntity::new, SpawnGroup.MONSTER)
                        .dimensions(14.0f, 7.0f)
                        .maxTrackingRange(32)
                        .makeFireImmune()
                        .build("hydra"));
        HYDRA_HEAD = Registry.register(Registries.ENTITY_TYPE, Identifier.of(HydraMod.MOD_ID, "hydra_head"),
                EntityType.Builder.create(HydraHeadEntity::new, SpawnGroup.MONSTER)
                        .dimensions(6.0f, 6.0f)
                        .maxTrackingRange(32)
                        .makeFireImmune()
                        .build("hydra_head"));
        FabricDefaultAttributeRegistry.register(HYDRA, HydraEntity.createHydraAttributes());
        FabricDefaultAttributeRegistry.register(HYDRA_HEAD, HydraHeadEntity.createHeadAttributes());
    }
}
