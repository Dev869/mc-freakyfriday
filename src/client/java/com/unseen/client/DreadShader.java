package com.unseen.client;

import com.unseen.Config;
import com.unseen.UnseenMod;
import com.unseen.client.mixin.GameRendererInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;

/**
 * Drives the low-sanity post-process effect.
 * <p>
 * Two constraints shape this. Loading a post processor is expensive and reallocates framebuffers, so
 * it is applied and cleared only on a state change, never per frame — only the uniforms move per
 * frame. And Minecraft 1.21.1 resolves shader <em>programs</em> with {@code Identifier.ofVanilla},
 * so while the chain lives in {@code assets/unseen/shaders/post/}, the program and GLSL must ship
 * under {@code assets/minecraft/shaders/program/} with a prefixed name to avoid colliding with other
 * mods.
 */
public final class DreadShader {
	private static final net.minecraft.util.Identifier CHAIN = UnseenMod.id("shaders/post/dread.json");

	private static boolean active;
	private static float time;

	private DreadShader() {
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			clear(client);
			return;
		}
		Config cfg = Config.get();
		float sanity = UnseenClient.state().sanity();
		float amount = intensity(sanity, cfg);

		if (amount <= 0.001f) {
			clear(client);
			return;
		}
		if (!active) {
			apply(client);
		}
		if (!active) {
			return; // load failed; do not spam attempts every tick
		}

		time += 0.05f;
		PostEffectProcessor processor = client.gameRenderer.getPostProcessor();
		if (processor != null) {
			processor.setUniforms("DreadAmount", amount);
			processor.setUniforms("DreadTime", time);
		}
	}

	/** Ramps from nothing at the shader threshold to full at zero sanity. */
	static float intensity(float sanity, Config cfg) {
		if (sanity >= cfg.shaderThreshold) {
			return 0f;
		}
		return Math.min(1f, (cfg.shaderThreshold - sanity) / cfg.shaderThreshold);
	}

	private static void apply(MinecraftClient client) {
		try {
			((GameRendererInvoker) client.gameRenderer).unseen$loadPostProcessor(CHAIN);
			active = true;
		} catch (RuntimeException e) {
			UnseenMod.LOGGER.error("Could not load the dread post effect, disabling it: {}", e.toString());
			active = false;
		}
	}

	private static void clear(MinecraftClient client) {
		if (!active) {
			return;
		}
		active = false;
		time = 0f;
		client.gameRenderer.disablePostProcessor();
	}

	/** Called on disconnect so a new world never inherits a stale effect. */
	public static void forget() {
		active = false;
		time = 0f;
	}
}
