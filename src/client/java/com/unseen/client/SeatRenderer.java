package com.unseen.client;

import com.unseen.entity.SeatEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** The seat is an anchor, not a thing. It must never draw. */
public class SeatRenderer extends EntityRenderer<SeatEntity> {

	public SeatRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(SeatEntity entity) {
		return Identifier.ofVanilla("textures/misc/white.png");
	}

	@Override
	public boolean shouldRender(SeatEntity entity, net.minecraft.client.render.Frustum frustum,
	                            double x, double y, double z) {
		return false;
	}
}
