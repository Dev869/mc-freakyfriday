package com.unseen;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.unseen.entity.StalkerEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Debug controls. A system whose entire job is to be unpredictable is otherwise close to untestable,
 * so this is the difference between "it compiles" and "we know it works".
 */
public final class UnseenCommand {

	private UnseenCommand() {
	}

	private static int buildMansion(ServerCommandSource source, net.minecraft.server.world.ServerWorld world,
	                               net.minecraft.util.math.BlockPos origin) {
		long seed = world.getRandom().nextLong();
		int placed = com.unseen.mansion.MansionBuilder.build(world, origin, seed);
		source.sendFeedback(() -> Text.literal(
				"built a mansion at " + origin.toShortString() + " (" + placed + " blocks, seed " + seed + ")"), false);
		return 1;
	}

	public static void register(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("unseen")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("status").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					HorrorState state = ModAttachments.get(player);
					boolean threatening = TensionManager.isThreatening(
							player, player.getServerWorld(), Config.get());
					int stalkers = TensionManager.findStalkerNear(
							player, player.getServerWorld(), 96).size();
					ctx.getSource().sendFeedback(() -> Text.literal(String.format(
							"sanity %.1f | stress %.1f | phase %s | threatening %s | stalkers %d",
							state.sanity(), state.stress(), state.phase(), threatening, stalkers)), false);
					return 1;
				}))
				.then(CommandManager.literal("sanity")
						.then(CommandManager.argument("value", FloatArgumentType.floatArg(0, 100))
								.executes(ctx -> {
									ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
									float v = FloatArgumentType.getFloat(ctx, "value");
									ModAttachments.set(player, ModAttachments.get(player).withSanity(v));
									ctx.getSource().sendFeedback(() -> Text.literal("sanity = " + v), false);
									return 1;
								})))
				.then(CommandManager.literal("stress")
						.then(CommandManager.argument("value", FloatArgumentType.floatArg(0, 100))
								.executes(ctx -> {
									ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
									float v = FloatArgumentType.getFloat(ctx, "value");
									ModAttachments.set(player, ModAttachments.get(player).withStress(v));
									ctx.getSource().sendFeedback(() -> Text.literal("stress = " + v), false);
									return 1;
								})))
				.then(CommandManager.literal("phase")
						.then(CommandManager.argument("name", StringArgumentType.word())
								.executes(ctx -> {
									ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
									Phase phase = Phase.byName(
											StringArgumentType.getString(ctx, "name").toUpperCase());
									TensionManager.setPhase(player, phase);
									ctx.getSource().sendFeedback(() -> Text.literal("phase = " + phase), false);
									return 1;
								})))
				.then(CommandManager.literal("spawn").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					StalkerEntity stalker = TensionManager.trySpawnStalker(
							player, player.getServerWorld(), Config.get());
					ctx.getSource().sendFeedback(() -> Text.literal(stalker == null
							? "no valid dark spawn point nearby"
							: "spawned a Stalker at " + stalker.getBlockPos()), false);
					return stalker == null ? 0 : 1;
				}))
				.then(CommandManager.literal("phantom").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					StalkerEntity phantom = TensionManager.trySpawnPhantom(
							player, player.getServerWorld(), Config.get());
					ctx.getSource().sendFeedback(() -> Text.literal(phantom == null
							? "nowhere out of sight to put one"
							: "hallucination at " + phantom.getBlockPos()), false);
					return phantom == null ? 0 : 1;
				}))
				.then(CommandManager.literal("mansion")
						// Centred on the player: placing it deliberately is what authoring a horror world
						// actually needs, rather than hoping worldgen puts one somewhere useful.
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							int half = com.unseen.mansion.MansionPlan.footprint() / 2;
							return buildMansion(ctx.getSource(), player.getServerWorld(),
									player.getBlockPos().add(-half, 0, -half));
						})
						// Explicit corner, so it also works from a server console with no player attached.
						.then(CommandManager.argument("corner", BlockPosArgumentType.blockPos())
								.executes(ctx -> buildMansion(ctx.getSource(),
										ctx.getSource().getWorld(),
										BlockPosArgumentType.getBlockPos(ctx, "corner")))))
				.then(CommandManager.literal("hollow").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					net.minecraft.server.world.ServerWorld target =
							ctx.getSource().getServer().getWorld(HollowDimension.WORLD);
					if (target == null) {
						ctx.getSource().sendError(Text.literal(
								"the Hollow is not loaded — the dimension datapack is missing"));
						return 0;
					}
					boolean leaving = HollowDimension.isHollow(player.getServerWorld());
					net.minecraft.server.world.ServerWorld destination = leaving
							? ctx.getSource().getServer().getOverworld() : target;
					player.teleport(destination, player.getX(), Math.max(player.getY(), 70),
							player.getZ(), java.util.Set.of(), player.getYaw(), player.getPitch());
					ctx.getSource().sendFeedback(() -> Text.literal(
							leaving ? "back to the overworld" : "into the Hollow"), false);
					return 1;
				}))
				.then(CommandManager.literal("clear").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					int n = 0;
					for (StalkerEntity s : TensionManager.findStalkerNear(
							player, player.getServerWorld(), 128)) {
						s.discard();
						n++;
					}
					final int removed = n;
					ctx.getSource().sendFeedback(() -> Text.literal("removed " + removed), false);
					return 1;
				})));
	}
}
