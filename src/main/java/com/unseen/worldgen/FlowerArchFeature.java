package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A flowering arch over a short stretch of path.
 *
 * <p>Small on purpose. The meadow already has cottages and wells to look at; what it does not have is
 * anything that reads as <em>tended</em> — something a person put up because it was pretty. An arch you
 * walk under does that in five blocks, and it makes the same promise the whole first act makes: someone
 * lived here and liked it.
 */
public class FlowerArchFeature extends Feature<DefaultFeatureConfig> {

	/** Half the span. Posts stand at ±HALF, the lintel runs between them. */
	private static final int HALF = 2;
	/** Height of the posts. The lintel sits one above, so you walk under it with room to spare. */
	private static final int POST_HEIGHT = 3;

	public FlowerArchFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();

		// Along X or Z — an arch always crosses the way you are walking, and there is no path here to
		// align to, so the coin flip is the whole decision.
		Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;

		if (!isFlat(world, origin, axis)) {
			return false;
		}

		for (int d = -HALF; d <= HALF; d++) {
			BlockPos foot = along(origin, axis, d);
			boolean post = Math.abs(d) == HALF;
			// Path under the arch sits *in* the ground, one below the walking surface — the same
			// convention the cottage path uses. Getting this wrong is how the last path ended up
			// never placing at all.
			//
			// Not under the posts, though: a dirt path with anything solid on top of it reverts to
			// plain dirt the moment it is placed, so those two blocks were being written and lost.
			// Plain ground at the foot of a post is what you would expect to see anyway.
			if (!post) {
				set(world, foot.down(), Blocks.DIRT_PATH.getDefaultState());
			}

			if (post) {
				for (int y = 0; y < POST_HEIGHT; y++) {
					set(world, foot.up(y), Blocks.OAK_FENCE.getDefaultState());
				}
			}
			// Lintel across the top, posts included, so the fences knit into one piece.
			set(world, foot.up(POST_HEIGHT), Blocks.OAK_FENCE.getDefaultState());
			// Canopy. Persistent, or it decays the moment a player loads the chunk — there is no log
			// anywhere near it to keep it alive.
			set(world, foot.up(POST_HEIGHT + 1), Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState()
					.with(LeavesBlock.PERSISTENT, true));
		}

		// Flowers crowding the feet of the posts, on whichever side has room.
		for (int d = -HALF; d <= HALF; d++) {
			for (int side = -1; side <= 1; side += 2) {
				if (random.nextInt(3) == 0) {
					continue;
				}
				plantFlower(world, beside(along(origin, axis, d), axis, side), random);
			}
		}
		return true;
	}

	private static BlockPos along(BlockPos origin, Direction.Axis axis, int d) {
		return axis == Direction.Axis.X ? origin.add(d, 0, 0) : origin.add(0, 0, d);
	}

	/** One step perpendicular to the arch — the verge either side of the path. */
	private static BlockPos beside(BlockPos at, Direction.Axis axis, int side) {
		return axis == Direction.Axis.X ? at.add(0, 0, side) : at.add(side, 0, 0);
	}

	/**
	 * The arch is rigid, so it needs level ground under all five columns; one step of slope leaves a
	 * post buried or hanging.
	 */
	private boolean isFlat(StructureWorldAccess world, BlockPos origin, Direction.Axis axis) {
		int y = origin.getY();
		for (int d = -HALF; d <= HALF; d++) {
			BlockPos foot = along(origin, axis, d);
			int here = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, foot.getX(), foot.getZ());
			if (here != y) {
				return false;
			}
			BlockPos below = foot.down();
			if (!world.getBlockState(below).isSolidBlock(world, below)
					|| !world.getFluidState(foot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void plantFlower(StructureWorldAccess world, BlockPos at, Random random) {
		BlockPos ground = at.down();
		if (!world.getBlockState(at).isAir() || !world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK)) {
			return;
		}
		BlockState flower = switch (random.nextInt(5)) {
			case 0 -> Blocks.CORNFLOWER.getDefaultState();
			case 1 -> Blocks.OXEYE_DAISY.getDefaultState();
			case 2 -> Blocks.LILY_OF_THE_VALLEY.getDefaultState();
			case 3 -> Blocks.ALLIUM.getDefaultState();
			default -> Blocks.POPPY.getDefaultState();
		};
		set(world, at, flower);
	}

	private void set(StructureWorldAccess world, BlockPos at, BlockState state) {
		if (world.isOutOfHeightLimit(at)) {
			return;
		}
		world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
	}
}
