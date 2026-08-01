package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Places a storybook cottage, and under one in three of them, a way into the Hollow.
 * <p>
 * All the geometry lives in {@link CottagePlan}, which is checked without a game running. This class
 * only decides what block each part is made of and whether the ground is worth building on.
 */
public class CottageFeature extends Feature<DefaultFeatureConfig> {

	/** How flat the ground has to be before a cottage will commit to it. */
	private static final int MAX_SLOPE = 2;
	/** One cottage in this many hides a portal. The rest are just a cellar and an empty room. */
	private static final int PORTAL_IN = 3;

	public CottageFeature(Codec<DefaultFeatureConfig> codec) {
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
		List<CottagePlan.Placed> plan = CottagePlan.build(random.nextLong(), withPortal);

		clearAbove(world, origin);
		for (CottagePlan.Placed p : plan) {
			// Relative, always. Absolute Y here is the bug that built the mansion in the sky.
			place(world, origin.add(p.x(), p.y(), p.z()), p, random);
		}
		return true;
	}

	/** Refuses steep or watery ground, so cottages do not end up half-buried or on stilts. */
	private boolean isBuildable(StructureWorldAccess world, BlockPos origin) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int half = CottagePlan.HALF;
		for (int x = -half; x <= half; x += half) {
			for (int z = -half; z <= half; z += half) {
				int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, origin.getX() + x, origin.getZ() + z);
				min = Math.min(min, y);
				max = Math.max(max, y);
			}
		}
		if (max - min > MAX_SLOPE) {
			return false;
		}
		// Nothing standing in water, and nothing floating over a cave that happens to break the surface.
		BlockPos below = origin.down();
		return world.getBlockState(below).isSolidBlock(world, below)
				&& world.getFluidState(origin).isEmpty();
	}

	/** Clears the column over the roof, so a meadow tree does not end up growing through the gable. */
	private void clearAbove(StructureWorldAccess world, BlockPos origin) {
		int half = CottagePlan.HALF;
		int top = CottagePlan.WALL_HEIGHT + half + 2;
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				for (int y = 1; y <= top; y++) {
					BlockPos at = origin.add(x, y, z);
					if (!world.getBlockState(at).isAir()) {
						world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
	}

	private void place(StructureWorldAccess world, BlockPos at, CottagePlan.Placed p, Random random) {
		switch (p.part()) {
			case FOUNDATION -> set(world, at, Blocks.COBBLESTONE.getDefaultState());
			case FLOOR -> set(world, at, Blocks.OAK_PLANKS.getDefaultState());
			case WALL -> set(world, at, wallBlock(p, random));
			case WINDOW -> set(world, at, Blocks.GLASS_PANE.getDefaultState());
			case DOOR_LOWER -> set(world, at, Blocks.OAK_DOOR.getDefaultState()
					.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			case DOOR_UPPER -> set(world, at, Blocks.OAK_DOOR.getDefaultState()
					.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			// Stairs ascend toward their facing. Both slopes climb inward to the ridge, so the row on
			// the north edge faces south and the row on the south edge faces north. Facing them
			// outward turns the gable into a groove.
			case ROOF_NORTH -> set(world, at, Blocks.DARK_OAK_STAIRS.getDefaultState()
					.with(StairsBlock.FACING, Direction.SOUTH));
			case ROOF_SOUTH -> set(world, at, Blocks.DARK_OAK_STAIRS.getDefaultState()
					.with(StairsBlock.FACING, Direction.NORTH));
			case RIDGE -> set(world, at, Blocks.DARK_OAK_PLANKS.getDefaultState());
			case LANTERN -> set(world, at, Blocks.LANTERN.getDefaultState()
					.with(net.minecraft.block.LanternBlock.HANGING, true));
			case RUG -> set(world, at, Blocks.RED_CARPET.getDefaultState());
			case TRAPDOOR -> set(world, at, Blocks.OAK_TRAPDOOR.getDefaultState()
					.with(TrapdoorBlock.OPEN, false));
			case SHAFT, CELLAR_AIR -> set(world, at, Blocks.CAVE_AIR.getDefaultState());
			case LADDER -> set(world, at, Blocks.LADDER.getDefaultState()
					.with(LadderBlock.FACING, Direction.WEST));
			case CELLAR_WALL -> set(world, at, random.nextInt(4) == 0
					? Blocks.MOSSY_STONE_BRICKS.getDefaultState()
					: Blocks.STONE_BRICKS.getDefaultState());
			case PATH -> set(world, at, Blocks.DIRT_PATH.getDefaultState());
			// The plot sits outside the footprint the slope check covered, so each piece checks its own
			// ground. Without this a garden on a slope ends up as fenceposts hanging in the air.
			case GARDEN_FENCE -> setGrounded(world, at, Blocks.OAK_FENCE.getDefaultState());
			case GARDEN_GATE -> setGrounded(world, at, Blocks.OAK_FENCE_GATE.getDefaultState());
			case GARDEN_SOIL -> {
				if (isGround(world, at.up())) {
					set(world, at, Blocks.FARMLAND.getDefaultState()
							.with(net.minecraft.block.FarmlandBlock.MOISTURE, 7));
				}
			}
			case GARDEN_WATER -> {
				if (isGround(world, at.up())) {
					set(world, at, Blocks.WATER.getDefaultState());
				}
			}
			case GARDEN_CROP -> placeCrop(world, at, random);
			case FLOWER -> placeFlower(world, at, random);
			// Blocks only, through the chunk region we were handed. Reaching for the ServerWorld here
			// deadlocks generation against the very chunk we are building.
			case PORTAL -> {
				com.unseen.portal.HollowPortal.buildFrameBlocks(world, at, Direction.Axis.X, true);
				// The rot is the tell. spreadTaint alone does almost nothing here because the room is
				// stone brick and deliberately not corruptible, so encrust the structure itself too.
				com.unseen.portal.HollowPortal.spreadTaint(world, at, Direction.Axis.X);
				com.unseen.portal.HollowPortal.encrustTaint(world, at, Direction.Axis.X);
			}
		}
	}

	/** Mostly birch, with the odd oak log for a timbered look rather than a flat slab of one plank. */
	private BlockState wallBlock(CottagePlan.Placed p, Random random) {
		boolean corner = Math.abs(p.x()) == CottagePlan.HALF && Math.abs(p.z()) == CottagePlan.HALF;
		if (corner) {
			return Blocks.OAK_LOG.getDefaultState();
		}
		return random.nextInt(7) == 0
				? Blocks.OAK_LOG.getDefaultState()
				: Blocks.BIRCH_PLANKS.getDefaultState();
	}

	/** True if this spot is open and standing on something solid — i.e. it is actually the surface. */
	private boolean isGround(StructureWorldAccess world, BlockPos at) {
		BlockPos below = at.down();
		return world.getBlockState(at).isReplaceable()
				&& world.getBlockState(below).isSolidBlock(world, below);
	}

	private void setGrounded(StructureWorldAccess world, BlockPos at, BlockState state) {
		if (isGround(world, at)) {
			set(world, at, state);
		}
	}

	/** Only where the farmland actually got placed, or crops end up floating over grass. */
	private void placeCrop(StructureWorldAccess world, BlockPos at, Random random) {
		if (!world.getBlockState(at.down()).isOf(Blocks.FARMLAND)) {
			return;
		}
		BlockState crop = switch (random.nextInt(4)) {
			case 0 -> Blocks.WHEAT.getDefaultState()
					.with(net.minecraft.block.CropBlock.AGE, random.nextInt(8));
			case 1 -> Blocks.CARROTS.getDefaultState()
					.with(net.minecraft.block.CropBlock.AGE, random.nextInt(8));
			case 2 -> Blocks.POTATOES.getDefaultState()
					.with(net.minecraft.block.CropBlock.AGE, random.nextInt(8));
			default -> Blocks.BEETROOTS.getDefaultState()
					.with(net.minecraft.block.BeetrootsBlock.AGE, random.nextInt(4));
		};
		set(world, at, crop);
	}

	private void placeFlower(StructureWorldAccess world, BlockPos at, Random random) {
		BlockPos ground = at.down();
		if (!world.getBlockState(at).isAir() || !world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK)) {
			return;
		}
		BlockState flower = switch (random.nextInt(6)) {
			case 0 -> Blocks.POPPY.getDefaultState();
			case 1 -> Blocks.CORNFLOWER.getDefaultState();
			case 2 -> Blocks.OXEYE_DAISY.getDefaultState();
			case 3 -> Blocks.AZURE_BLUET.getDefaultState();
			case 4 -> Blocks.LILY_OF_THE_VALLEY.getDefaultState();
			default -> Blocks.DANDELION.getDefaultState();
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
