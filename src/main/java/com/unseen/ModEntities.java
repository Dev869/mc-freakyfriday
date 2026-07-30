package com.unseen;

import com.unseen.entity.SeatEntity;
import com.unseen.entity.StalkerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<StalkerEntity> STALKER = Registry.register(
			Registries.ENTITY_TYPE,
			UnseenMod.id("stalker"),
			EntityType.Builder.create(StalkerEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 2.6f)
					.maxTrackingRange(64)
					.build("stalker"));

	public static final EntityType<SeatEntity> SEAT = Registry.register(
			Registries.ENTITY_TYPE,
			UnseenMod.id("seat"),
			EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC)
					.dimensions(0.01f, 0.01f)
					.maxTrackingRange(10)
					.build("seat"));

	private ModEntities() {
	}

	static void init() {
		FabricDefaultAttributeRegistry.register(STALKER, StalkerEntity.createStalkerAttributes());
	}
}
