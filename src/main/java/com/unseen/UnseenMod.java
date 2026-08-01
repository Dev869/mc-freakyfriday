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
		com.unseen.worldgen.ModFeatures.register();

		ServerTickEvents.END_SERVER_TICK.register(TensionManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.unseen.worldgen.LairSites::drain);

		// Sneak + use on a bed hides under it. Plain use still sleeps.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			// Flint and steel against a dark oak frame opens a way into the Hollow.
			if (player.getStackInHand(hand).isOf(net.minecraft.item.Items.FLINT_AND_STEEL)
					&& com.unseen.portal.HollowPortal.tryIgnite(world, hit.getBlockPos().offset(hit.getSide()))) {
				// Lighting a way into the Hollow costs what lighting anything else costs. Without this
				// the first flint and steel you ever craft opens every portal you will ever open.
				if (!world.isClient && !player.isCreative()) {
					player.getStackInHand(hand).damage(1, player,
							hand == net.minecraft.util.Hand.MAIN_HAND
									? net.minecraft.entity.EquipmentSlot.MAINHAND
									: net.minecraft.entity.EquipmentSlot.OFFHAND);
				}
				return net.minecraft.util.ActionResult.SUCCESS;
			}
			return Hiding.tryBed(world, hit.getBlockPos(), player);
		});

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
