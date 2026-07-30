package com.unseen.client;

import com.unseen.HorrorState;
import com.unseen.ModAttachments;
import com.unseen.ModEntities;
import com.unseen.UnseenMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;

public class UnseenClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.STALKER, StalkerRenderer::new);
		EntityRendererRegistry.register(ModEntities.SEAT, SeatRenderer::new);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			HeartbeatManager.tick(client);
			SceneryManipulator.tick(client);
			DreadShader.tick(client);
		});
		// Never leave a corrupted world behind: drop tracking when the world goes away.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SceneryManipulator.forget();
			DreadShader.forget();
		});
		UnseenMod.LOGGER.info("Client effects armed.");
	}

	/**
	 * Reads the state the server synced onto the client player. No packet handling needed — the Data
	 * Attachment API delivers it.
	 */
	public static HorrorState state() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return HorrorState.INITIAL;
		}
		return client.player.getAttachedOrElse(ModAttachments.HORROR, HorrorState.INITIAL);
	}
}
