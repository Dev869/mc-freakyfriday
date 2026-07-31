package com.unseen.client;

import com.unseen.entity.StalkerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * {@link StalkerModel} resolves assets by convention from the id it passes up:
 * {@code geo/entity/stalker.geo.json}, {@code animations/entity/stalker.animation.json},
 * {@code textures/entity/stalker.png}.
 */
public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {

	public StalkerRenderer(EntityRendererFactory.Context context) {
		super(context, new StalkerModel());
		this.shadowRadius = 0.4f;

		// Reads textures/entity/stalker_glowmask.png — the suffix is GeckoLib's, not ours — and draws
		// every non-transparent pixel of it at full brightness. Only the irises are opaque there, so in
		// an unlit corridor the eyes are the only part of this thing a player can see.
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}
}
