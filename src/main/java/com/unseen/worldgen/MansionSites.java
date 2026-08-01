package com.unseen.worldgen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;

/**
 * Where the creepy mansion is, worked out rather than looked up.
 *
 * <p>Worldgen features cannot ask the world where a structure is: the structure may not have been
 * placed yet, may be in a chunk that is not loaded, and asking would generate it. But a
 * {@code random_spread} placement is a pure function of the world seed and the region coordinates, so
 * anything that wants to know can simply recompute it. That is how {@code /locate} finds one without
 * generating the world in between.
 *
 * <p>The numbers below are Dungeons and Taverns' {@code minecraft:illager_manor} structure set. They
 * must match it exactly; if that mod changes its spacing, the cracks stop pointing at anything and
 * nothing will complain. {@code /unseen mansion-site} exists to check them against {@code /locate}.
 */
public final class MansionSites {

	private static final int SPACING = 80;
	private static final int SEPARATION = 20;
	private static final int SALT = 10387319;

	/** How far the mansion's influence reaches, in blocks. Beyond this the world is merely pretty. */
	public static final int REACH = 480;

	private MansionSites() {
	}

	/** The mansion candidate for one region of the grid, in block coordinates. */
	private static BlockPos siteInRegion(long worldSeed, int regionX, int regionZ) {
		ChunkRandom random = new ChunkRandom(new CheckedRandom(0L));
		random.setRegionSeed(worldSeed, regionX, regionZ, SALT);
		int spread = SPACING - SEPARATION;
		// Triangular spread: the average of two rolls, which clusters sites toward the region centre.
		// Order matters — x is drawn before z, and swapping them silently moves every mansion.
		int dx = (random.nextInt(spread + 1) + random.nextInt(spread + 1)) / 2;
		int dz = (random.nextInt(spread + 1) + random.nextInt(spread + 1)) / 2;
		ChunkPos chunk = new ChunkPos(regionX * SPACING + dx, regionZ * SPACING + dz);
		return new BlockPos(chunk.getStartX() + 8, 0, chunk.getStartZ() + 8);
	}

	/**
	 * The nearest mansion site to a position, or null if none is within {@link #REACH}.
	 * <p>
	 * Searches the neighbouring regions as well as the containing one, because a position near a
	 * region edge is often closer to the site next door than to its own.
	 */
	public static BlockPos nearest(long worldSeed, BlockPos from) {
		int regionX = Math.floorDiv(from.getX() >> 4, SPACING);
		int regionZ = Math.floorDiv(from.getZ() >> 4, SPACING);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos site = siteInRegion(worldSeed, regionX + dx, regionZ + dz);
				double distance = flatDistance(site, from);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = site;
				}
			}
		}
		return bestDistance <= REACH ? best : null;
	}

	/** Horizontal distance only. Depth is irrelevant to how far the rot has crept. */
	public static double flatDistance(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * How strongly the rot has taken hold here: 1 at the mansion's doorstep, 0 at the edge of its
	 * reach. Squared so the corruption stays visibly concentrated near the house instead of thinning
	 * into an even wash across half a kilometre.
	 */
	public static double intensity(long worldSeed, BlockPos at) {
		BlockPos site = nearest(worldSeed, at);
		if (site == null) {
			return 0;
		}
		double near = 1.0 - flatDistance(site, at) / REACH;
		return near * near;
	}
}
