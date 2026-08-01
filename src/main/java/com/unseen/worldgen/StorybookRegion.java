package com.unseen.worldgen;

import com.mojang.datafixers.util.Pair;
import com.unseen.UnseenMod;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import terrablender.api.ModifiedVanillaOverworldBuilder;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.List;
import java.util.function.Consumer;

/**
 * Makes the overworld one endless Storybook Meadow.
 * <p>
 * The beauty is the setup, not decoration. A horror mod that starts in a dark cave has nowhere to fall
 * from; one that starts in a sunlit meadow full of flowers and rabbits has everything to lose.
 * <p>
 * It used to claim only the soft biomes and leave deserts and mountains generating normally, so the
 * meadow was somewhere you arrived at. That is now exactly backwards. If you can walk to a badlands
 * then the world has an outside, and the horror is just over there somewhere; if the meadow never ends,
 * the only way out is <em>down</em>, and the portal under a cottage floor is the only door there is.
 * <p>
 * Water and caves are deliberately left alone. An ocean rendered as grass looks broken rather than
 * dreamlike, the footbridges need streams to cross, and a portal cut into a lush-cave wall is worth
 * more than one in plain stone.
 */
public class StorybookRegion extends Region {

	/**
	 * Every land biome vanilla can put on the surface. All of them become the meadow.
	 * <p>
	 * Listed rather than derived by subtracting the water and cave biomes: a new biome in some future
	 * version should show up here as an obvious omission, not get silently swallowed by a filter.
	 */
	private static final List<RegistryKey<Biome>> LAND = List.of(
			BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.SNOWY_PLAINS, BiomeKeys.ICE_SPIKES,
			BiomeKeys.MEADOW, BiomeKeys.CHERRY_GROVE, BiomeKeys.GROVE,
			BiomeKeys.FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.BIRCH_FOREST,
			BiomeKeys.OLD_GROWTH_BIRCH_FOREST, BiomeKeys.DARK_FOREST,
			BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA,
			BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA,
			BiomeKeys.JUNGLE, BiomeKeys.SPARSE_JUNGLE, BiomeKeys.BAMBOO_JUNGLE,
			BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.WINDSWEPT_SAVANNA,
			BiomeKeys.DESERT, BiomeKeys.BADLANDS, BiomeKeys.WOODED_BADLANDS, BiomeKeys.ERODED_BADLANDS,
			BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP, BiomeKeys.MUSHROOM_FIELDS,
			BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.WINDSWEPT_GRAVELLY_HILLS, BiomeKeys.WINDSWEPT_FOREST,
			BiomeKeys.SNOWY_SLOPES, BiomeKeys.JAGGED_PEAKS, BiomeKeys.FROZEN_PEAKS, BiomeKeys.STONY_PEAKS);

	public StorybookRegion(int weight) {
		super(UnseenMod.id("storybook"), RegionType.OVERWORLD, weight);
	}

	@Override
	public void addBiomes(Registry<Biome> registry,
	                      Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> mapper) {
		// Takes vanilla's whole overworld parameter list and swaps biomes inside it, so the climate
		// space stays complete — every point still resolves to something, it just resolves to us.
		// Claiming the parameter space directly would have swallowed the oceans along with the land.
		this.addModifiedVanillaOverworldBiomes(mapper, this::replaceLand);
	}

	private void replaceLand(ModifiedVanillaOverworldBuilder builder) {
		for (RegistryKey<Biome> biome : LAND) {
			builder.replaceBiome(biome, ModBiomes.STORYBOOK_MEADOW);
		}
	}
}
