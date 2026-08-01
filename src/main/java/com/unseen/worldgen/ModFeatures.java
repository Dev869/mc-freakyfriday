package com.unseen.worldgen;

import com.unseen.UnseenMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

public final class ModFeatures {

	public static final Feature<DefaultFeatureConfig> COTTAGE = new CottageFeature(DefaultFeatureConfig.CODEC);
	public static final Feature<DefaultFeatureConfig> WELL = new WellFeature(DefaultFeatureConfig.CODEC);
	public static final Feature<DefaultFeatureConfig> LAIR = new LairFeature(DefaultFeatureConfig.CODEC);
	public static final Feature<DefaultFeatureConfig> MANSION = new MansionFeature(DefaultFeatureConfig.CODEC);
	public static final Feature<DefaultFeatureConfig> ARCH = new FlowerArchFeature(DefaultFeatureConfig.CODEC);
	public static final Feature<DefaultFeatureConfig> BRIDGE = new BridgeFeature(DefaultFeatureConfig.CODEC);

	private ModFeatures() {
	}

	public static void register() {
		Registry.register(Registries.FEATURE, UnseenMod.id("cottage"), COTTAGE);
		Registry.register(Registries.FEATURE, UnseenMod.id("well"), WELL);
		Registry.register(Registries.FEATURE, UnseenMod.id("lair"), LAIR);
		Registry.register(Registries.FEATURE, UnseenMod.id("mansion"), MANSION);
		Registry.register(Registries.FEATURE, UnseenMod.id("arch"), ARCH);
		Registry.register(Registries.FEATURE, UnseenMod.id("bridge"), BRIDGE);
	}
}
