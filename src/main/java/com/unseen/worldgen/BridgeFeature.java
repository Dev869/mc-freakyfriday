package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A plank footbridge across a stream, with fence rails and a lantern at each corner.
 *
 * <p>Rivers cut the meadow up, and swimming across one is the least storybook thing in the game. A
 * bridge says the same thing the cottages say — somebody lives here, and they got tired of getting wet.
 *
 * <p>The search for where to put it is the only real logic, and it is pure: {@link BridgePlan#span}
 * works on a boolean array, so it is checked without a game running.
 */
public class BridgeFeature extends Feature<DefaultFeatureConfig> {

	/** How far either side of the origin to look for a bank. Beyond this it is a lake, not a stream. */
	private static final int REACH = 10;

	public BridgeFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();

		// The origin is the first air above the surface, so the water — if there is any — is one below.
		// The deck goes where the origin is, which is also the walking height of a bank whose ground
		// tops out at the same level. That is what makes the two ends meet without a step.
		int deck = origin.getY();

		Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
		int[] ends = findSpan(world, origin, axis, deck);
		if (ends == null) {
			// One stream, one crossing direction. If the coin flip picked the wrong one, try the other
			// rather than throwing away a perfectly good river.
			axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
			ends = findSpan(world, origin, axis, deck);
		}
		if (ends == null) {
			return false;
		}

		for (int d = ends[0]; d <= ends[1]; d++) {
			for (int w = -1; w <= 1; w++) {
				BlockPos at = offset(origin, axis, d, w).withY(deck);
				set(world, at, Blocks.OAK_PLANKS.getDefaultState());
				// Keep the walkway clear — a bridge with a tree growing through it is not charming.
				for (int y = 1; y <= 3; y++) {
					clear(world, at.up(y));
				}
			}
			for (int w = -1; w <= 1; w += 2) {
				BlockPos rail = offset(origin, axis, d, w).withY(deck + 1);
				set(world, rail, Blocks.OAK_FENCE.getDefaultState());
				// Lanterns only where the rail starts and finishes, so the crossing is marked at night
				// without turning into a runway.
				if (d == ends[0] || d == ends[1]) {
					set(world, rail.up(), Blocks.LANTERN.getDefaultState());
				}
			}
		}
		return true;
	}

	/** Walks the window either side of the origin and hands the water pattern to {@link BridgePlan#span}. */
	private int[] findSpan(StructureWorldAccess world, BlockPos origin, Direction.Axis axis, int deck) {
		boolean[] water = new boolean[REACH * 2 + 1];
		for (int i = 0; i < water.length; i++) {
			BlockPos surface = offset(origin, axis, i - REACH, 0).withY(deck - 1);
			water[i] = world.getBlockState(surface).isOf(Blocks.WATER);
		}
		int[] ends = BridgePlan.span(water, REACH);
		if (ends == null) {
			return null;
		}
		// span() only knows where the water stops. Whether what it stops against is ground level with
		// the deck — rather than a cliff, or a lily pad — is a question about the world.
		for (int end : ends) {
			for (int w = -1; w <= 1; w++) {
				BlockPos foot = offset(origin, axis, end - REACH, w).withY(deck - 1);
				if (!world.getBlockState(foot).isSolidBlock(world, foot)
						|| !world.getBlockState(foot.up()).isReplaceable()) {
					return null;
				}
			}
		}
		return new int[]{ends[0] - REACH, ends[1] - REACH};
	}

	/** {@code d} runs along the bridge, {@code w} across it. */
	private static BlockPos offset(BlockPos origin, Direction.Axis axis, int d, int w) {
		return axis == Direction.Axis.X ? origin.add(d, 0, w) : origin.add(w, 0, d);
	}

	private void clear(StructureWorldAccess world, BlockPos at) {
		if (!world.isOutOfHeightLimit(at) && !world.getBlockState(at).isAir()) {
			world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
	}

	private void set(StructureWorldAccess world, BlockPos at, BlockState state) {
		if (world.isOutOfHeightLimit(at)) {
			return;
		}
		world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
	}
}
