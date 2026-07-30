package com.unseen.client;

import com.unseen.UnseenMod;
import com.unseen.entity.StalkerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * {@link DefaultedEntityGeoModel} resolves assets by convention from the id below:
 * {@code geo/entity/stalker.geo.json}, {@code animations/entity/stalker.animation.json},
 * {@code textures/entity/stalker.png}.
 */
public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {

	public StalkerRenderer(EntityRendererFactory.Context context) {
		super(context, new DefaultedEntityGeoModel<>(UnseenMod.id("stalker")));
		this.shadowRadius = 0.4f;
	}
}
