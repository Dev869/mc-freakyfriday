package com.unseen.worldgen;

import com.unseen.UnseenMod;
import terrablender.api.Regions;

/**
 * TerraBlender entrypoint. Registered under the {@code terrablender} entrypoint key in
 * fabric.mod.json, which TerraBlender calls once it is ready to accept regions.
 */
public class UnseenTerraBlender implements terrablender.api.TerraBlenderApi {

	@Override
	public void onTerraBlenderInitialized() {
		// Weight decides how much of the overworld this region claims against other mods'.
		Regions.register(new StorybookRegion(6));
		UnseenMod.LOGGER.info("Storybook region registered with TerraBlender.");
	}
}
