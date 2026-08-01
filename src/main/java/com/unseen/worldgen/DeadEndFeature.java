package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import com.unseen.portal.HollowPortal;
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
 * A passage off a cave that goes nowhere, and has a way into the Hollow at the end of it.
 *
 * <p>A portal standing in an open cavern is scenery you walk past. A side passage is a decision: it is
 * obviously cut rather than eroded, it is too straight, and it only goes one way. You follow it because
 * dead ends in Minecraft normally mean someone mined here, and at the end of it the wall is open.
 *
 * <p>Bored out of the rock from an existing cave, never floating in stone on its own — a chamber nobody
 * can reach is the same as no chamber at all.
 */
public class DeadEndFeature extends Feature<DefaultFeatureConfig> {

	private static final int MIN_LENGTH = 5;
	private static final int MAX_LENGTH = 13;
	/** Tunnel bore. Two wide so the portal at the end fits without cutting into the walls. */
	private static final int WIDTH = 2;
	private static final int HEIGHT = 3;

	public DeadEndFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();

		BlockPos mouth = findCaveFloor(world, context.getOrigin());
		if (mouth == null) {
			return false;
		}
		int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);

		for (Direction heading : shuffledHorizontals(random)) {
			if (!isSolidRun(world, mouth, heading, length + 2)) {
				continue;
			}
			carve(world, mouth, heading, length);

			// The portal faces back down the passage, so it is the first thing you see at the end
			// rather than something you turn around and find behind you.
			BlockPos end = mouth.offset(heading, length);
			Direction.Axis axis = heading.rotateYClockwise().getAxis();
			HollowPortal.buildFrameBlocks(world, end, axis, true);
			// Blocks only. A Feature runs on a worldgen thread; the see-through view gets made the
			// first time something stands in it.
			HollowPortal.spreadTaint(world, end, axis);
			HollowPortal.encrustTaint(world, end, axis);
			return true;
		}
		return false;
	}

	/** The passage has to be cut through rock, not opened into the next cave along. */
	private boolean isSolidRun(StructureWorldAccess world, BlockPos from, Direction heading, int length) {
		Direction across = heading.rotateYClockwise();
		for (int step = 1; step <= length; step++) {
			for (int w = 0; w < WIDTH; w++) {
				for (int h = 0; h < HEIGHT; h++) {
					BlockPos at = from.offset(heading, step).offset(across, w).up(h);
					if (world.isOutOfHeightLimit(at) || !world.getBlockState(at).isSolidBlock(world, at)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private void carve(StructureWorldAccess world, BlockPos from, Direction heading, int length) {
		Direction across = heading.rotateYClockwise();
		for (int step = 1; step <= length; step++) {
			for (int w = 0; w < WIDTH; w++) {
				for (int h = 0; h < HEIGHT; h++) {
					set(world, from.offset(heading, step).offset(across, w).up(h),
							Blocks.CAVE_AIR.getDefaultState());
				}
			}
		}
	}

	/** Walks down from the origin looking for open air standing on solid rock. */
	private BlockPos findCaveFloor(StructureWorldAccess world, BlockPos origin) {
		BlockPos.Mutable at = origin.mutableCopy();
		for (int i = 0; i < 48; i++) {
			at.move(Direction.DOWN);
			if (world.isOutOfHeightLimit(at)) {
				return null;
			}
			BlockState here = world.getBlockState(at);
			if (here.isAir() && world.getBlockState(at.down()).isSolidBlock(world, at.down())) {
				return at.toImmutable();
			}
		}
		return null;
	}

	private Direction[] shuffledHorizontals(Random random) {
		Direction[] out = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		for (int i = out.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			Direction swap = out[i];
			out[i] = out[j];
			out[j] = swap;
		}
		return out;
	}

	private void set(StructureWorldAccess world, BlockPos at, BlockState state) {
		if (!world.isOutOfHeightLimit(at)) {
			world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
		}
	}
}
