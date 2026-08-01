package com.unseen.worldgen;

import com.unseen.UnseenMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;

public final class ModBiomes {
	public static final RegistryKey<Biome> STORYBOOK_MEADOW =
			RegistryKey.of(RegistryKeys.BIOME, UnseenMod.id("storybook_meadow"));
	public static final RegistryKey<Biome> HOLLOW =
			RegistryKey.of(RegistryKeys.BIOME, UnseenMod.id("hollow"));

	private ModBiomes() {
	}
}
