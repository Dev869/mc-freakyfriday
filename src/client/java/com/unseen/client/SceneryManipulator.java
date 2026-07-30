package com.unseen.client;

import com.unseen.Config;
import com.unseen.UnseenMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;

/**
 * The world goes wrong when your sanity does — stone cracks, torches go out, walls grow cobwebs.
 * <p>
 * Entirely client-side and entirely reversible. Every change records the state it replaced, and the
 * originals are restored as sanity recovers, on disconnect, or if the player walks away. The server
 * never hears about any of it, so nothing here can corrupt a save.
 * <p>
 * The point is not spectacle, it is doubt: the effect is deliberately sparse and applied out of the
 * player's view, so corruption is something they turn around and notice rather than watch happen. A
 * change they see occur is a special effect. A change they discover is a horror beat.
 */
public final class SceneryManipulator {
	/** Original states, so every swap can be undone. */
	private static final Map<BlockPos, BlockState> ORIGINALS = new HashMap<>();
	private static final Random RANDOM = Random.create();

	private static int cooldown;

	private SceneryManipulator() {
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null || client.isPaused()) {
			return;
		}
		Config cfg = Config.get();
		float sanity = UnseenClient.state().sanity();

		if (sanity >= cfg.sceneryThreshold) {
			revertAll(client);
			return;
		}
		if (--cooldown > 0) {
			return;
		}
		cooldown = cfg.sceneryIntervalTicks;

		// Drop anything the player has wandered away from, so the effect follows them.
		revertDistant(client, cfg.sceneryRadius * 2);

		if (ORIGINALS.size() >= cfg.sceneryMaxBlocks) {
			return;
		}
		corruptOnce(client, cfg);
	}

	private static void corruptOnce(MinecraftClient client, Config cfg) {
		BlockPos origin = client.player.getBlockPos();
		for (int attempt = 0; attempt < 24; attempt++) {
			BlockPos pos = origin.add(
					RANDOM.nextInt(cfg.sceneryRadius * 2) - cfg.sceneryRadius,
					RANDOM.nextInt(8) - 4,
					RANDOM.nextInt(cfg.sceneryRadius * 2) - cfg.sceneryRadius);
			if (ORIGINALS.containsKey(pos)) {
				continue;
			}
			BlockState current = client.world.getBlockState(pos);
			BlockState corrupted = corruptionFor(current);
			if (corrupted == null) {
				continue;
			}
			// Only ever change what they are not looking at. Corruption must be discovered.
			if (isInView(client, pos)) {
				continue;
			}
			ORIGINALS.put(pos.toImmutable(), current);
			client.world.setBlockState(pos, corrupted, 0);
			return;
		}
	}

	/** The swap table. Returns null for blocks that should be left alone. */
	private static BlockState corruptionFor(BlockState state) {
		if (state.isOf(Blocks.STONE)) {
			return Blocks.COBBLESTONE.getDefaultState();
		}
		if (state.isOf(Blocks.STONE_BRICKS)) {
			return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
		}
		if (state.isOf(Blocks.DEEPSLATE_BRICKS)) {
			return Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
		}
		if (state.isOf(Blocks.DEEPSLATE_TILES)) {
			return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
		}
		if (state.isOf(Blocks.TORCH)) {
			return Blocks.SOUL_TORCH.getDefaultState();
		}
		if (state.isOf(Blocks.WALL_TORCH)) {
			return Blocks.SOUL_WALL_TORCH.getDefaultState()
					.withIfExists(net.minecraft.state.property.Properties.HORIZONTAL_FACING,
							state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING));
		}
		if (state.isOf(Blocks.OAK_PLANKS)) {
			return Blocks.DARK_OAK_PLANKS.getDefaultState();
		}
		return null;
	}

	private static boolean isInView(MinecraftClient client, BlockPos pos) {
		var look = client.player.getRotationVec(1f).normalize();
		var toBlock = net.minecraft.util.math.Vec3d.ofCenter(pos)
				.subtract(client.player.getEyePos());
		if (toBlock.lengthSquared() < 1.0e-4) {
			return true;
		}
		return look.dotProduct(toBlock.normalize()) > 0.35; // generous cone, ~70 degrees
	}

	private static void revertDistant(MinecraftClient client, double maxDistance) {
		double maxSq = maxDistance * maxDistance;
		BlockPos player = client.player.getBlockPos();
		ORIGINALS.entrySet().removeIf(entry -> {
			if (entry.getKey().getSquaredDistance(player) <= maxSq) {
				return false;
			}
			restore(client, entry.getKey(), entry.getValue());
			return true;
		});
	}

	/** Restores everything. Safe to call repeatedly and when nothing is corrupted. */
	public static void revertAll(MinecraftClient client) {
		if (ORIGINALS.isEmpty()) {
			return;
		}
		if (client.world != null) {
			ORIGINALS.forEach((pos, state) -> restore(client, pos, state));
		}
		ORIGINALS.clear();
	}

	/** Drops tracking without touching the world — for when the world itself is going away. */
	public static void forget() {
		ORIGINALS.clear();
		cooldown = 0;
	}

	private static void restore(MinecraftClient client, BlockPos pos, BlockState original) {
		try {
			client.world.setBlockState(pos, original, 0);
		} catch (RuntimeException e) {
			UnseenMod.LOGGER.debug("Could not restore corrupted block at {}: {}", pos, e.toString());
		}
	}
}
