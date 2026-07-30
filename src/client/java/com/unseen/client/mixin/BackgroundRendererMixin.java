package com.unseen.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.unseen.HorrorState;
import com.unseen.Phase;
import com.unseen.client.UnseenClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes the world in during a PEAK phase, and tightens it further as sanity falls. Applied at TAIL
 * so vanilla has already set its own fog and we are only ever pulling it closer, never pushing it out.
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

	@Inject(method = "applyFog", at = @At("TAIL"))
	private static void unseen$dread(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
	                                 boolean thickFog, float tickDelta, CallbackInfo ci) {
		if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN) {
			return;
		}
		HorrorState state = UnseenClient.state();

		// 0 = no dread, 1 = maximum. Peak phase and low sanity both pull it in.
		float phaseFactor = state.phase() == Phase.PEAK ? 1f : 0f;
		float sanityFactor = Math.max(0f, (50f - state.sanity()) / 50f);
		float dread = Math.min(1f, Math.max(phaseFactor * 0.7f, sanityFactor));
		if (dread <= 0.01f) {
			return;
		}

		float targetEnd = 12f + (1f - dread) * (viewDistance - 12f);
		if (targetEnd < viewDistance) {
			RenderSystem.setShaderFogStart(targetEnd * 0.15f);
			RenderSystem.setShaderFogEnd(targetEnd);
		}
	}
}
