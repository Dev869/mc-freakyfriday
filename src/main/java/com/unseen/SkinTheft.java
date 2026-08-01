package com.unseen;

import com.unseen.entity.ImpersonatorEntity;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

/**
 * What happens when the Stalker actually kills you.
 * <p>
 * The trophy is a real player head carrying your profile, so it renders with your face and can be
 * found, taken back, or left hanging. The impersonator that follows wears the same face and does not
 * despawn. Together they turn a death into a thing that persists in the world rather than a respawn
 * and a walk back.
 */
public final class SkinTheft {

	private SkinTheft() {
	}

	/** Hangs the victim's face in the killer's lair and sends something out wearing it. */
	public static void take(ServerWorld world, PlayerEntity victim, BlockPos lair) {
		if (!(victim instanceof ServerPlayerEntity server)) {
			return;
		}
		hangTrophy(world, server, lair);
		wearIt(world, server, lair);

		world.getServer().getPlayerManager().broadcast(
				Text.literal("Something is wearing " + server.getName().getString() + "'s face."), false);
	}

	/** A player head on the wall of the lair, wearing the victim's skin. */
	private static void hangTrophy(ServerWorld world, ServerPlayerEntity victim, BlockPos lair) {
		BlockPos at = findMount(world, lair);
		if (at == null) {
			return;
		}
		// Mounted on the wall where there is one, because a head on the floor reads as dropped loot
		// and a head at eye level on the wall reads as something that was put there on purpose.
		world.setBlockState(at, trophyState(wallBehind(world, at)),
				net.minecraft.block.Block.NOTIFY_LISTENERS);
		if (world.getBlockEntity(at) instanceof net.minecraft.block.entity.SkullBlockEntity skull) {
			// The head carries the victim's real profile, so it renders with their actual face.
			skull.setOwner(new ProfileComponent(victim.getGameProfile()));
			skull.markDirty();
		}
		UnseenMod.LOGGER.debug("Hung {}'s face at {}", victim.getName().getString(), at);
	}

	/**
	 * Debug: hangs an unclaimed face and reports where it went, using exactly the search the real theft
	 * uses. Without this the mount search can only be checked by dying, which needs a person at a
	 * keyboard.
	 */
	public static BlockPos hangBlank(ServerWorld world, BlockPos lair) {
		BlockPos at = findMount(world, lair);
		if (at == null) {
			return null;
		}
		Direction wall = wallBehind(world, at);
		world.setBlockState(at, trophyState(wall), net.minecraft.block.Block.NOTIFY_LISTENERS);
		return at;
	}

	/** A head on the wall where there is one, on the floor where there is not. */
	private static net.minecraft.block.BlockState trophyState(Direction wall) {
		return wall == null
				? Blocks.PLAYER_HEAD.getDefaultState()
				: Blocks.PLAYER_WALL_HEAD.getDefaultState()
						.with(net.minecraft.block.WallSkullBlock.FACING, wall.getOpposite());
	}

	/**
	 * How far out to look for a wall. A generated lair is nine blocks across, so anything less than
	 * this finds nothing but open air when the drag ends in the middle of one and the face gets left
	 * hovering over the nest instead of hung up with the others.
	 */
	private static final int MOUNT_SEARCH_RADIUS = 6;

	/**
	 * Somewhere to put the face.
	 * <p>
	 * Eye level first and working down, then outward ring by ring, always preferring a spot with
	 * something solid behind it. In a lair that lands the head on the trophy wall in a gap between the
	 * skulls already hanging there, which is the shot the whole sequence is building toward. In a bare
	 * cave it falls back to whatever flat spot it can find, which is the best that can be done.
	 */
	private static BlockPos findMount(ServerWorld world, BlockPos lair) {
		BlockPos fallback = null;
		for (int dy = 2; dy >= 0; dy--) {
			for (int radius = 1; radius <= MOUNT_SEARCH_RADIUS; radius++) {
				for (BlockPos at : ring(lair.up(dy), radius)) {
					if (!world.getBlockState(at).isAir()) {
						continue;
					}
					if (wallBehind(world, at) != null) {
						return at;
					}
					if (fallback == null
							&& world.getBlockState(at.down()).isSolidBlock(world, at.down())) {
						fallback = at;
					}
				}
			}
		}
		// No last resort that skips the floor check. The lair centre used to be accepted on the
		// strength of being air alone, which in an open cave hangs the face in mid-air — and both
		// callers already handle null by simply not placing a trophy.
		return fallback;
	}

	/** The hollow square of positions exactly {@code radius} out from a centre, at its own height. */
	private static java.util.List<BlockPos> ring(BlockPos centre, int radius) {
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
					out.add(centre.add(dx, 0, dz));
				}
			}
		}
		return out;
	}

	/** The direction of the first solid horizontal neighbour, or null if the spot is free-standing. */
	private static Direction wallBehind(ServerWorld world, BlockPos at) {
		for (Direction d : Direction.Type.HORIZONTAL) {
			BlockPos side = at.offset(d);
			if (world.getBlockState(side).isSolidBlock(world, side)) {
				return d;
			}
		}
		return null;
	}

	/** Sends out the thing that will be mistaken for them. */
	private static void wearIt(ServerWorld world, ServerPlayerEntity victim, BlockPos lair) {
		sendOutWearer(world, lair, victim.getUuid(), victim.getName().getString());
	}

	/**
	 * Puts something wearing the face where it can be used.
	 * <p>
	 * The trophy stays in the lair; the wearer does not. A face taken in the Hollow is worth nothing
	 * down there — there is nobody left to fool — and the story is explicit that it walks back out to
	 * find your friends. So it surfaces in the overworld at the same coordinates it took you from. The
	 * Hollow is this world with the sun taken out, so coming back up at the spot where you were killed
	 * is the shortest honest reading of that.
	 *
	 * @return where it came out, or null if nothing could be spawned
	 */
	public static BlockPos sendOutWearer(ServerWorld world, BlockPos lair,
	                                     java.util.UUID victimId, String victimName) {
		ServerWorld out = emergenceWorld(world);
		BlockPos at = emergencePos(out, lair);
		ImpersonatorEntity wearer = ModEntities.IMPERSONATOR.create(out);
		if (wearer == null) {
			return null;
		}
		wearer.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
				out.random.nextFloat() * 360f, 0f);
		wearer.setVictim(victimId, victimName);
		wearer.setPersistent();
		if (!out.spawnEntity(wearer)) {
			return null;
		}
		UnseenMod.LOGGER.debug("{}'s face walked out in {} at {}",
				victimName, out.getRegistryKey().getValue(), at);
		return at;
	}

	/** The Hollow is where it takes you; it is not where it hunts. */
	public static ServerWorld emergenceWorld(ServerWorld where) {
		if (!HollowDimension.isHollow(where)) {
			return where;
		}
		ServerWorld overworld = where.getServer().getOverworld();
		return overworld == null ? where : overworld;
	}

	/**
	 * Same column, standing on the surface rather than buried at the lair's depth.
	 * <p>
	 * The chunk is loaded first on purpose. {@code getTopY} quietly answers with the bottom of the world
	 * for a chunk that is not loaded rather than loading it, and the overworld column above a Hollow
	 * lair is exactly the kind of place nobody has been — so without this the wearer is spawned at
	 * y=-64 into an ungenerated chunk and is simply gone. One synchronous chunk load, once, on a death.
	 */
	public static BlockPos emergencePos(ServerWorld target, BlockPos from) {
		target.getChunk(from.getX() >> 4, from.getZ() >> 4);
		int y = target.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				from.getX(), from.getZ());
		// Still nothing there: better to come out at the lair than at the bottom of the world.
		return y <= target.getBottomY() ? from : new BlockPos(from.getX(), y, from.getZ());
	}

	/** True if this impersonator is wearing the given player's face. */
	public static boolean isWearing(ImpersonatorEntity wearer, PlayerEntity player) {
		Optional<java.util.UUID> id = wearer.getVictimId();
		return id.isPresent() && id.get().equals(player.getUuid());
	}
}
