package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import com.unseen.ModBlocks;
import com.unseen.block.HollowTaintBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Cracks of rot in the ground, running back toward the mansion they came from.
 *
 * <p>The portals used to be hidden and nothing pointed at them, so nobody found one. This is the
 * signpost: a thin split in the grass that gets wider and more frequent the closer you are to the
 * house, so walking uphill through the density leads somewhere. It reads as damage rather than
 * decoration, which is the tone — something is wrong here and it is spreading.
 *
 * <p>Each crack is aimed at the mansion rather than laid down at random. Following one is meant to
 * work.
 *
 * <p>Close to the house the seam is <em>open</em>: the crack blocks are portals lying flat in the
 * ground, and you look down through them into the Hollow before you ever find a doorway. Further out
 * the same seam is only rot. That gradient is the whole navigation cue — the ground stops being
 * scenery and starts being a hole.
 */
public class TaintCrackFeature extends Feature<DefaultFeatureConfig> {

	/** Longest a single crack runs. Long enough to have a direction you can read off the ground. */
	private static final int MAX_LENGTH = 14;

	/**
	 * Chance that a crack opens all the way through at all, at the mansion's doorstep.
	 * <p>
	 * Per crack, not per block. Scattering open blocks individually gave a dotted line of unrelated
	 * 1x1 holes, which reads as a bug rather than a split — a crack that goes through should go
	 * through for a stretch of its length, in one piece.
	 */
	private static final double OPEN_CHANCE = 0.35;

	/** Longest stretch of a crack that is open. Short, so it is a gash and not a trench. */
	private static final int MAX_OPEN_RUN = 5;

	public TaintCrackFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();

		BlockPos mansion = MansionSites.nearest(world.getSeed(), origin);
		if (mansion == null) {
			return false;
		}
		double intensity = MansionSites.intensity(world.getSeed(), origin);
		// Thins out with distance instead of stopping dead at the edge of reach, so there is no line
		// on the ground where the corruption obviously switches off.
		if (random.nextFloat() > intensity) {
			return false;
		}

		// Toward the house. A crack that wanders at random is scenery; one that points is a trail.
		double dx = mansion.getX() - origin.getX();
		double dz = mansion.getZ() - origin.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1) {
			return false;
		}
		dx /= length;
		dz /= length;

		int steps = 3 + (int) (MAX_LENGTH * intensity);

		// Decide up front whether this crack goes through, and if so which stretch of it does. One
		// contiguous run, so the hole is a single opening the whole gash widens into.
		boolean opens = random.nextFloat() < intensity * intensity * OPEN_CHANCE;
		int openRun = opens ? 2 + random.nextInt(MAX_OPEN_RUN - 1) : 0;
		int openFrom = opens ? random.nextInt(Math.max(1, steps - openRun)) : -1;

		double x = origin.getX();
		double z = origin.getZ();
		int placed = 0;
		for (int i = 0; i < steps; i++) {
			// A little wander per step, or every crack in the world is a straight line to the same spot.
			x += dx + (random.nextDouble() - 0.5) * 0.8;
			z += dz + (random.nextDouble() - 0.5) * 0.8;
			boolean open = opens && i >= openFrom && i < openFrom + openRun;
			// A crack has width. One block per step was a dotted line; a split is two or three across
			// at its middle and tapers at the ends, which is what makes it read as the ground giving
			// way rather than as blocks placed in a row.
			int halfWidth = open || random.nextInt(3) > 0 ? 1 : 0;
			for (int w = -halfWidth; w <= halfWidth; w++) {
				// Across the direction of travel, so the crack widens sideways rather than lengthening.
				int cx = (int) Math.round(x + (-dz) * w);
				int cz = (int) Math.round(z + dx * w);
				BlockPos at = surfaceAt(world, cx, cz);
				if (at == null) {
					continue;
				}
				if (open) {
					// Blocks only — a Feature runs on a worldgen thread, and the see-through view is
					// made the first time something stands in it, exactly as the cave portals do.
					world.setBlockState(at, ModBlocks.HOLLOW_PORTAL.getDefaultState()
							.with(com.unseen.block.HollowPortalBlock.AXIS, Direction.Axis.Y),
							Block.NOTIFY_LISTENERS);
				} else {
					// Closed seam: rot only. Vigour is what lets it keep spreading once the world is
					// running, so cracks near the house are still alive and the far ones are spent.
					int vigour = Math.max(0, Math.min(HollowTaintBlock.MAX_VIGOUR,
							(int) Math.round(intensity * HollowTaintBlock.MAX_VIGOUR)));
					world.setBlockState(at, ModBlocks.HOLLOW_TAINT.getDefaultState()
							.with(HollowTaintBlock.VIGOUR, vigour), Block.NOTIFY_LISTENERS);
				}
				placed++;
			}
		}
		return placed > 0;
	}

	/** The ground block at a column, or null if there is nothing there worth cracking. */
	private BlockPos surfaceAt(StructureWorldAccess world, int x, int z) {
		int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
		BlockPos ground = new BlockPos(x, y - 1, z);
		if (world.isOutOfHeightLimit(ground)) {
			return null;
		}
		BlockState state = world.getBlockState(ground);
		// Natural ground only. Cracking a lake, a tree trunk or somebody's cottage floor is not the
		// same picture at all.
		//
		// The first version listed grass, dirt and stone and nothing else, which quietly excluded every
		// hillside: Tectonic pushes the surface past y=120 in places and up there the ground is snow.
		// Cracks were being attempted and rejected by the thousand with nothing to show for it.
		boolean ground_ = state.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
				|| state.isIn(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD)
				|| state.isIn(net.minecraft.registry.tag.BlockTags.SAND)
				|| state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)
				|| state.isOf(Blocks.GRAVEL);
		if (!ground_) {
			return null;
		}
		// Air, or the grass and snow layers that decoration has already laid on top — those are things
		// a split in the earth would swallow, not obstacles to it.
		BlockState above = world.getBlockState(ground.up());
		return above.isAir() || above.isReplaceable() ? ground : null;
	}
}
