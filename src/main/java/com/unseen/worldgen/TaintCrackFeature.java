package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import com.unseen.ModBlocks;
import com.unseen.block.HollowTaintBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
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
 */
public class TaintCrackFeature extends Feature<DefaultFeatureConfig> {

	/** Longest a single crack runs. Long enough to have a direction you can read off the ground. */
	private static final int MAX_LENGTH = 14;

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
		double x = origin.getX();
		double z = origin.getZ();
		int placed = 0;
		for (int i = 0; i < steps; i++) {
			// A little wander per step, or every crack in the world is a straight line to the same spot.
			x += dx + (random.nextDouble() - 0.5) * 0.8;
			z += dz + (random.nextDouble() - 0.5) * 0.8;
			BlockPos at = surfaceAt(world, (int) Math.round(x), (int) Math.round(z));
			if (at == null) {
				continue;
			}
			// Vigour is what lets the rot keep spreading on its own once the world is running. Cracks
			// near the house are still alive; the far ones are spent and just sit there.
			int vigour = Math.max(0, Math.min(HollowTaintBlock.MAX_VIGOUR,
					(int) Math.round(intensity * HollowTaintBlock.MAX_VIGOUR)));
			world.setBlockState(at, ModBlocks.HOLLOW_TAINT.getDefaultState()
					.with(HollowTaintBlock.VIGOUR, vigour), Block.NOTIFY_LISTENERS);
			placed++;
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
		// Only ordinary ground. Cracking a lake, a tree trunk or somebody's cottage floor is not the
		// same picture at all.
		if (!state.isOf(Blocks.GRASS_BLOCK) && !state.isOf(Blocks.DIRT) && !state.isOf(Blocks.STONE)
				&& !state.isOf(Blocks.COARSE_DIRT) && !state.isOf(Blocks.PODZOL)) {
			return null;
		}
		return world.getBlockState(ground.up()).isAir() ? ground : null;
	}
}
