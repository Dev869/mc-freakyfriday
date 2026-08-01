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

	/** Runs a worldgen feature directly, bypassing the rarity filter that makes it untestable. */
	private static int placeFeature(ServerCommandSource source, net.minecraft.server.world.ServerWorld world,
	                                net.minecraft.util.math.BlockPos origin, String name,
	                                net.minecraft.world.gen.feature.Feature<
			                                net.minecraft.world.gen.feature.DefaultFeatureConfig> feature) {
		boolean placed = feature.generate(
				new net.minecraft.world.gen.feature.util.FeatureContext<>(
						java.util.Optional.empty(), world,
						world.getChunkManager().getChunkGenerator(),
						net.minecraft.util.math.random.Random.create(world.getRandom().nextLong()),
						origin,
						net.minecraft.world.gen.feature.DefaultFeatureConfig.INSTANCE));
		source.sendFeedback(() -> Text.literal(placed
				? "a " + name + " at " + origin.toShortString()
				: "the ground there is too rough for a " + name), false);
		return placed ? 1 : 0;
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
				.then(CommandManager.literal("portal")
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							net.minecraft.util.math.BlockPos at = com.unseen.portal.HollowPortal.maybePlaceNear(
									player.getServerWorld(), player, Config.get());
							ctx.getSource().sendFeedback(() -> Text.literal(at == null
									? "nowhere dark and clear enough nearby for a way in"
									: "a way into the Hollow opened at " + at.toShortString()), false);
							return at == null ? 0 : 1;
						})
						// Explicit position, so a portal can be built from a console with no player.
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> {
									net.minecraft.util.math.BlockPos at =
											BlockPosArgumentType.getBlockPos(ctx, "at");
									com.unseen.portal.HollowPortal.buildFrame(
											ctx.getSource().getWorld(), at,
											net.minecraft.util.math.Direction.Axis.X, true);
									com.unseen.portal.HollowPortal.spreadTaint(
											ctx.getSource().getWorld(), at,
											net.minecraft.util.math.Direction.Axis.X);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"portal built at " + at.toShortString()), false);
									return 1;
								})))
				.then(CommandManager.literal("cottage")
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							return placeFeature(ctx.getSource(), player.getServerWorld(),
									player.getBlockPos(), "cottage",
									com.unseen.worldgen.ModFeatures.COTTAGE);
						})
						// Explicit position, so it also works from a console with no player attached.
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> placeFeature(ctx.getSource(),
										ctx.getSource().getWorld(),
										BlockPosArgumentType.getBlockPos(ctx, "at"), "cottage",
										com.unseen.worldgen.ModFeatures.COTTAGE))))
				.then(CommandManager.literal("well")
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							return placeFeature(ctx.getSource(), player.getServerWorld(),
									player.getBlockPos(), "well", com.unseen.worldgen.ModFeatures.WELL);
						})
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> placeFeature(ctx.getSource(),
										ctx.getSource().getWorld(),
										BlockPosArgumentType.getBlockPos(ctx, "at"), "well",
										com.unseen.worldgen.ModFeatures.WELL))))
				.then(CommandManager.literal("arch")
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							return placeFeature(ctx.getSource(), player.getServerWorld(),
									player.getBlockPos(), "flower arch",
									com.unseen.worldgen.ModFeatures.ARCH);
						})
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> placeFeature(ctx.getSource(),
										ctx.getSource().getWorld(),
										BlockPosArgumentType.getBlockPos(ctx, "at"), "flower arch",
										com.unseen.worldgen.ModFeatures.ARCH))))
				.then(CommandManager.literal("bridge")
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							return placeFeature(ctx.getSource(), player.getServerWorld(),
									player.getBlockPos(), "bridge",
									com.unseen.worldgen.ModFeatures.BRIDGE);
						})
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> placeFeature(ctx.getSource(),
										ctx.getSource().getWorld(),
										BlockPosArgumentType.getBlockPos(ctx, "at"), "bridge",
										com.unseen.worldgen.ModFeatures.BRIDGE))))
				.then(CommandManager.literal("impersonate")
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> {
									net.minecraft.util.math.BlockPos from =
											BlockPosArgumentType.getBlockPos(ctx, "at");
									net.minecraft.server.world.ServerWorld here = ctx.getSource().getWorld();
									net.minecraft.util.math.BlockPos out = com.unseen.SkinTheft.sendOutWearer(
											here, from, java.util.UUID.randomUUID(), "Someone");
									ctx.getSource().sendFeedback(() -> Text.literal(out == null
											? "nothing came out"
											: "it came out in "
													+ com.unseen.SkinTheft.emergenceWorld(here)
															.getRegistryKey().getValue()
													+ " at " + out.toShortString()), false);
									return out == null ? 0 : 1;
								})))
				.then(CommandManager.literal("trophy")
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> {
									net.minecraft.util.math.BlockPos from =
											BlockPosArgumentType.getBlockPos(ctx, "at");
									net.minecraft.util.math.BlockPos hung =
											com.unseen.SkinTheft.hangBlank(ctx.getSource().getWorld(), from);
									ctx.getSource().sendFeedback(() -> Text.literal(hung == null
											? "nowhere to hang a face near " + from.toShortString()
											: "hung a face at " + hung.toShortString()), false);
									return hung == null ? 0 : 1;
								})))
				.then(CommandManager.literal("lairs")
						.then(CommandManager.argument("near", BlockPosArgumentType.blockPos())
								.executes(ctx -> {
									net.minecraft.server.world.ServerWorld world = ctx.getSource().getWorld();
									com.unseen.worldgen.LairSites sites =
											com.unseen.worldgen.LairSites.get(world);
									net.minecraft.util.math.BlockPos from =
											BlockPosArgumentType.getBlockPos(ctx, "near");
									net.minecraft.util.math.BlockPos best = sites.nearest(from, 512);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"lairs known here: " + sites.count() + " | nearest to "
													+ from.toShortString() + ": "
													+ (best == null ? "none" : best.toShortString())), false);
									return sites.count();
								})))
				.then(CommandManager.literal("lair")
						.executes(ctx -> {
							ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
							return placeFeature(ctx.getSource(), player.getServerWorld(),
									player.getBlockPos(), "lair", com.unseen.worldgen.ModFeatures.LAIR);
						})
						.then(CommandManager.argument("at", BlockPosArgumentType.blockPos())
								.executes(ctx -> placeFeature(ctx.getSource(),
										ctx.getSource().getWorld(),
										BlockPosArgumentType.getBlockPos(ctx, "at"), "lair",
										com.unseen.worldgen.ModFeatures.LAIR))))
				.then(CommandManager.literal("drag").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					StalkerEntity stalker = TensionManager.trySpawnStalker(
							player, player.getServerWorld(), Config.get());
					if (stalker == null) {
						ctx.getSource().sendError(Text.literal("no valid dark spawn point nearby"));
						return 0;
					}
					// Its lair is wherever it just spawned, so this drags you off to a real distance
					// instead of finishing on the spot.
					stalker.grab(player);
					ctx.getSource().sendFeedback(() -> Text.literal("it has you"), false);
					return 1;
				}))
				.then(CommandManager.literal("skin").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					com.unseen.SkinTheft.take(player.getServerWorld(), player, player.getBlockPos());
					ctx.getSource().sendFeedback(() -> Text.literal("your face has been taken"), false);
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
