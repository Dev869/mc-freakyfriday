package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import com.unseen.mansion.MansionBuilder;
import com.unseen.mansion.MansionPlan;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * The house in the Hollow.
 * <p>
 * {@link MansionBuilder} has existed since early on and was only ever reachable through a debug command,
 * which meant the one thing in this pack that was explicitly asked for as a "level" could never be found
 * by playing. This puts it in the world.
 * <p>
 * It generates only in the Hollow, and rarely. A derelict house standing in a world with the sun taken
 * out is a landmark; one on every hill is scenery.
 */
public class MansionFeature extends Feature<DefaultFeatureConfig> {

	/**
	 * How much height variation the ground under a 33-block footprint may have.
	 * <p>
	 * Four was the first guess and it rejected every chunk tried — natural overworld-style terrain is
	 * almost never that flat across thirty-three blocks. Eight still refuses cliffsides while accepting
	 * ordinary rolling ground.
	 */
	private static final int MAX_SLOPE = 8;

	public MansionFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();

		int half = MansionPlan.footprint() / 2;
		BlockPos centre = context.getOrigin();
		BlockPos corner = centre.add(-half, 0, -half);

		Integer ground = groundLevel(world, corner, half);
		if (ground == null) {
			return false;
		}
		// MansionBuilder treats its origin as the corner and works in relative offsets from there.
		MansionBuilder.build(world, new BlockPos(corner.getX(), ground, corner.getZ()), random.nextLong());
		return true;
	}

	/**
	 * The surface height under the footprint, or null if the ground is too broken to stand a house on.
	 * <p>
	 * Sampled at the corners and the middle rather than every column: cheap, and enough to reject the
	 * cliff edges and cave mouths that would leave the place half-buried or hanging in the air.
	 */
	private Integer groundLevel(StructureWorldAccess world, BlockPos corner, int half) {
		int span = half * 2;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int dx = 0; dx <= span; dx += half) {
			for (int dz = 0; dz <= span; dz += half) {
				int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG,
						corner.getX() + dx, corner.getZ() + dz);
				min = Math.min(min, y);
				max = Math.max(max, y);
			}
		}
		if (max - min > MAX_SLOPE) {
			return null;
		}
		// Build off the lowest corner, not the average: sitting the house into the hill leaves it
		// embedded at worst, whereas averaging leaves one corner hanging in the air.
		BlockPos under = new BlockPos(corner.getX() + half, min - 1, corner.getZ() + half);
		if (!world.getBlockState(under).isSolidBlock(world, under)) {
			return null;
		}
		return min;
	}
}
