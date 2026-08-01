package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import com.unseen.portal.HollowPortal;
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
 * A ruin in the meadow: four broken walls, no roof, and often a way through standing in the middle.
 *
 * <p>These are scattered across the whole world rather than tied to the mansion's reach, and that is
 * deliberate. Mansions are roughly 1280 blocks apart while the cracks only carry 480, so a new world
 * could put you 800 blocks from any hint that anything is wrong. Ruins close that gap: wherever you
 * spawn, something old and broken is within a walk of you, and one in three of them is a door.
 *
 * <p>Deliberately not pretty. The cottages are the fairytale; this is what the fairytale is built on
 * top of, and it was here first.
 */
public class RuinFeature extends Feature<DefaultFeatureConfig> {

	/** Half the footprint. Small — a ruin should read as one room that lost its roof. */
	private static final int HALF = 3;
	private static final int WALL_HEIGHT = 3;
	/** How flat the ground has to be. Ruins tolerate more than cottages; they are already broken. */
	private static final int MAX_SLOPE = 3;
	/** One ruin in this many has a portal standing in it. */
	private static final int PORTAL_IN = 3;

	public RuinFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();

		if (!isBuildable(world, origin)) {
			return false;
		}

		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
				// Floor slabs everywhere, sunk into the ground so grass has grown up to them.
				set(world, origin.add(x, -1, z), stone(random));
				if (!edge) {
					continue;
				}
				// Walls that have fallen down rather than walls with holes punched in them: height
				// drops off toward the corners and every course has bites taken out of it.
				int height = WALL_HEIGHT - random.nextInt(3);
				for (int y = 0; y < height; y++) {
					if (random.nextInt(6) == 0) {
						continue;
					}
					set(world, origin.add(x, y, z), stone(random));
				}
			}
		}

		// Rubble where the roof went.
		for (int i = 0; i < 8; i++) {
			int x = random.nextInt(HALF * 2 + 1) - HALF;
			int z = random.nextInt(HALF * 2 + 1) - HALF;
			if (random.nextBoolean()) {
				set(world, origin.add(x, 0, z), Blocks.MOSSY_COBBLESTONE_SLAB.getDefaultState());
			}
		}

		if (random.nextInt(PORTAL_IN) == 0) {
			Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
			// Standing in the open, in the middle of the room. Nothing subtle: the point of a ruin is
			// that you can see what it holds from outside it.
			BlockPos base = origin.add(axis == Direction.Axis.X ? -1 : 0, 0,
					axis == Direction.Axis.Z ? -1 : 0);
			HollowPortal.buildFrameBlocks(world, base, axis, true);
			HollowPortal.spreadTaint(world, base, axis);
			HollowPortal.encrustTaint(world, base, axis);
		}
		return true;
	}

	/** Cracked, mossy or plain, so no two courses look like the same block. */
	private BlockState stone(Random random) {
		return switch (random.nextInt(4)) {
			case 0 -> Blocks.MOSSY_STONE_BRICKS.getDefaultState();
			case 1 -> Blocks.CRACKED_STONE_BRICKS.getDefaultState();
			case 2 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
			default -> Blocks.STONE_BRICKS.getDefaultState();
		};
	}

	private boolean isBuildable(StructureWorldAccess world, BlockPos origin) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int x = -HALF; x <= HALF; x += HALF) {
			for (int z = -HALF; z <= HALF; z += HALF) {
				int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, origin.getX() + x, origin.getZ() + z);
				min = Math.min(min, y);
				max = Math.max(max, y);
			}
		}
		if (max - min > MAX_SLOPE) {
			return false;
		}
		BlockPos below = origin.down();
		return world.getBlockState(below).isSolidBlock(world, below)
				&& world.getFluidState(origin).isEmpty();
	}

	private void set(StructureWorldAccess world, BlockPos at, BlockState state) {
		if (!world.isOutOfHeightLimit(at)) {
			world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
		}
	}
}
