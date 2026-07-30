package com.unseen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnseenMod implements ModInitializer {
	public static final String MOD_ID = "unseen";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Config.load();
		ModAttachments.init();
		ModEntities.init();
		ModSounds.init();
		ModBlocks.init();

		ServerTickEvents.END_SERVER_TICK.register(TensionManager::tick);

		// Sneak + use on a bed hides under it. Plain use still sleeps.
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				Hiding.tryBed(world, hit.getBlockPos(), player));

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> UnseenCommand.register(dispatcher));

		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> {
					java.util.UUID uuid = handler.getPlayer().getUuid();
					TensionManager.onPlayerDisconnect(uuid);
					SanityManager.onPlayerDisconnect(uuid);
				});

		LOGGER.info("The Unseen Architecture is listening.");
	}
}
