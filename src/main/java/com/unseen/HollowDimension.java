package com.unseen;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * The Hollow: a dead, sunless forest.
 * <p>
 * Defined entirely as datapack JSON under {@code data/unseen/} — a dimension needs no code and no
 * library, whereas injecting a biome into the <em>overworld</em> would have meant a hard dependency on
 * TerraBlender. A separate dimension is also the better horror: it has no daylight to run to.
 * <p>
 * {@code has_skylight: false} with {@code ambient_light: 0.0} means the surface is as dark as a cave, so
 * the Director treats the whole dimension as threatening and the Stalker is never off duty there.
 */
public final class HollowDimension {
	public static final RegistryKey<World> WORLD =
			RegistryKey.of(RegistryKeys.WORLD, UnseenMod.id("hollow"));

	private HollowDimension() {
	}

	public static boolean isHollow(ServerWorld world) {
		return world.getRegistryKey().equals(WORLD);
	}
}
