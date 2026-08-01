package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallSkullBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * The Stalker's trophy room, buried in the Hollow.
 * <p>
 * Geometry lives in {@link LairPlan}. This picks blocks and, more importantly, refuses to build anywhere
 * you could not walk into — a sealed room in the middle of the stone is worth nothing.
 */
public class LairFeature extends Feature<DefaultFeatureConfig> {

	/** How far down from the origin to look for a cave to hang the doorway off. */
	private static final int SEARCH_DOWN = 40;

	public LairFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();

		BlockPos floor = findCaveFloor(world, context.getOrigin());
		if (floor == null) {
			return false;
		}
		for (LairPlan.Placed p : LairPlan.build(random.nextLong())) {
			place(world, floor.add(p.x(), p.y(), p.z()), p, random);
		}
		// Only the registry key and a position — no world access, so this stays safe on a worldgen thread.
		LairSites.record(world.toServerWorld().getRegistryKey(), floor);
		return true;
	}

	/**
	 * Looks for open air standing on solid ground. Building off a cave means the doorway opens onto
	 * something a player can actually arrive through, instead of into undisturbed rock.
	 */
	private BlockPos findCaveFloor(StructureWorldAccess world, BlockPos origin) {
		BlockPos.Mutable cursor = origin.mutableCopy();
		for (int i = 0; i < SEARCH_DOWN; i++) {
			cursor.set(origin.getX(), origin.getY() - i, origin.getZ());
			if (world.isOutOfHeightLimit(cursor) || cursor.getY() - 2 < world.getBottomY()) {
				return null;
			}
			boolean headroom = world.getBlockState(cursor).isAir()
					&& world.getBlockState(cursor.up()).isAir();
			BlockPos below = cursor.down();
			if (headroom && world.getBlockState(below).isSolidBlock(world, below)) {
				return cursor.toImmutable();
			}
		}
		return null;
	}

	private void place(StructureWorldAccess world, BlockPos at, LairPlan.Placed p, Random random) {
		switch (p.part()) {
			case AIR, DOORWAY -> set(world, at, Blocks.CAVE_AIR.getDefaultState());
			case FLOOR -> set(world, at, random.nextInt(3) == 0
					? Blocks.SOUL_SOIL.getDefaultState()
					: Blocks.DEEPSLATE.getDefaultState());
			case BONES, NEST -> set(world, at, Blocks.BONE_BLOCK.getDefaultState());
			case WALL -> set(world, at, random.nextInt(4) == 0
					? Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState()
					: Blocks.DEEPSLATE_BRICKS.getDefaultState());
			case CEILING -> set(world, at, Blocks.DEEPSLATE.getDefaultState());
			case TAINT -> set(world, at, com.unseen.ModBlocks.HOLLOW_TAINT.getDefaultState());
			case TROPHY -> placeTrophy(world, at, p, random);
		}
	}

	/**
	 * A face on the wall, turned to look into the room. Mostly skulls — old ones — with the occasional
	 * player head, which renders as a person and is the one that makes the row land.
	 */
	private void placeTrophy(StructureWorldAccess world, BlockPos at, LairPlan.Placed p, Random random) {
		Direction facing = facingFromWall(p);
		if (facing == null) {
			return;
		}
		BlockState head = random.nextInt(3) == 0
				? Blocks.PLAYER_WALL_HEAD.getDefaultState()
				: Blocks.SKELETON_WALL_SKULL.getDefaultState();
		set(world, at, head.with(WallSkullBlock.FACING, facing));
	}

	/** Wall skulls face away from the wall they hang on, which here means into the room. */
	private Direction facingFromWall(LairPlan.Placed p) {
		if (p.x() == -LairPlan.HALF) {
			return Direction.EAST;
		}
		if (p.x() == LairPlan.HALF) {
			return Direction.WEST;
		}
		if (p.z() == -LairPlan.HALF) {
			return Direction.SOUTH;
		}
		if (p.z() == LairPlan.HALF) {
			return Direction.NORTH;
		}
		return null;
	}

	private void set(StructureWorldAccess world, BlockPos at, BlockState state) {
		if (world.isOutOfHeightLimit(at)) {
			return;
		}
		world.setBlockState(at, state, Block.NOTIFY_LISTENERS);
	}
}
