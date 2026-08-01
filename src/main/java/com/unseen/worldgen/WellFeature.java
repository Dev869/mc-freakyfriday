package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A wishing well in the meadow, and under one in three of them a way into the Hollow at the bottom.
 * <p>
 * Geometry lives in {@link WellPlan}. This only picks blocks and decides whether the ground will take it.
 */
public class WellFeature extends Feature<DefaultFeatureConfig> {

	private static final int PORTAL_IN = 3;

	public WellFeature(Codec<DefaultFeatureConfig> codec) {
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
		boolean withPortal = random.nextInt(PORTAL_IN) == 0;
		for (WellPlan.Placed p : WellPlan.build(withPortal)) {
			place(world, origin.add(p.x(), p.y(), p.z()), p, random);
		}
		return true;
	}

	private boolean isBuildable(StructureWorldAccess world, BlockPos origin) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, origin.getX() + x, origin.getZ() + z);
				min = Math.min(min, y);
				max = Math.max(max, y);
			}
		}
		if (max - min > 1) {
			return false;
		}
		// The chamber has to fit under it without punching out of the bottom of the world.
		BlockPos floor = origin.down(-WellPlan.CHAMBER_FLOOR + 1);
		return !world.isOutOfHeightLimit(floor)
				&& world.getBlockState(origin.down()).isSolidBlock(world, origin.down())
				&& world.getFluidState(origin).isEmpty();
	}

	private void place(StructureWorldAccess world, BlockPos at, WellPlan.Placed p, Random random) {
		switch (p.part()) {
			case RIM -> set(world, at, random.nextInt(3) == 0
					? Blocks.MOSSY_COBBLESTONE.getDefaultState()
					: Blocks.COBBLESTONE.getDefaultState());
			case POST -> set(world, at, Blocks.OAK_FENCE.getDefaultState());
			case ROOF -> set(world, at, Blocks.OAK_SLAB.getDefaultState());
			case LANTERN -> set(world, at, Blocks.LANTERN.getDefaultState()
					.with(net.minecraft.block.LanternBlock.HANGING, true));
			case SHAFT, CHAMBER_AIR -> set(world, at, Blocks.CAVE_AIR.getDefaultState());
			// The shaft sits in the chamber's west corner, so the wall is always to the west and the
			// ladder faces east off it.
			case LADDER -> set(world, at, Blocks.LADDER.getDefaultState()
					.with(LadderBlock.FACING, Direction.EAST));
			case CHAMBER_WALL -> set(world, at, random.nextInt(3) == 0
					? Blocks.MOSSY_STONE_BRICKS.getDefaultState()
					: Blocks.STONE_BRICKS.getDefaultState());
			// Blocks only — reaching for the ServerWorld here deadlocks chunk generation.
			case PORTAL -> {
				com.unseen.portal.HollowPortal.buildFrameBlocks(world, at, Direction.Axis.X, true);
				// The rot is the tell. spreadTaint alone does almost nothing here because the room is
				// stone brick and deliberately not corruptible, so encrust the structure itself too.
				com.unseen.portal.HollowPortal.spreadTaint(world, at, Direction.Axis.X);
				com.unseen.portal.HollowPortal.encrustTaint(world, at, Direction.Axis.X);
			}
		}
	}

	private void set(StructureWorldAccess world, BlockPos at, net.minecraft.block.BlockState state) {
		if (world.isOutOfHeightLimit(at)) {
			return;
		}
		world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
	}
}
