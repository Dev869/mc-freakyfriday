package com.unseen.worldgen;

import com.mojang.serialization.Codec;
import com.unseen.portal.HollowPortal;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * A way into the Hollow, cut into a cave wall under the mansion's reach.
 *
 * <p>Portals used to be sealed inside cottage cellars and well shafts, one in three, with nothing
 * anywhere pointing at them — so in practice nobody found one. These are in open caves instead, near
 * the house the rot comes from, where {@link TaintCrackFeature}'s cracks on the surface are already
 * telling you something is wrong. Follow the cracks, go down, find the door.
 *
 * <p>Only ever in a wall you can walk up to. A frame floating in a cavern reads as a spawned object;
 * one cut into rock reads as something that opened.
 */
public class CavePortalFeature extends Feature<DefaultFeatureConfig> {

	/** Rock behind the frame, so it is a doorway into stone rather than a hole through a curtain. */
	private static final int WALL_DEPTH = 2;

	public CavePortalFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		Random random = context.getRandom();

		// Close to the house or not at all. The point is that the corruption has a source you can find.
		double intensity = MansionSites.intensity(world.getSeed(), origin);
		if (intensity <= 0 || random.nextFloat() > intensity) {
			return false;
		}

		BlockPos floor = findCaveFloor(world, origin);
		if (floor == null) {
			return false;
		}
		for (Direction facing : Direction.Type.HORIZONTAL) {
			// The frame stands against the wall, so its interior is the air column next to the rock.
			if (!isWall(world, floor, facing)) {
				continue;
			}
			Direction.Axis axis = facing.rotateYClockwise().getAxis();
			if (!roomFor(world, floor, axis)) {
				continue;
			}
			HollowPortal.buildFrameBlocks(world, floor, axis, true);
			// Blocks only — a Feature runs on a worldgen thread and must never touch the ServerWorld.
			// The see-through view is made later, the first time anyone stands in it.
			HollowPortal.spreadTaint(world, floor, axis);
			HollowPortal.encrustTaint(world, floor, axis);
			return true;
		}
		return false;
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
			BlockState below = world.getBlockState(at.down());
			if (here.isAir() && below.isSolidBlock(world, at.down())) {
				return at.toImmutable();
			}
		}
		return null;
	}

	/** True if there is real rock behind this face, not a thin shell into the next cave along. */
	private boolean isWall(StructureWorldAccess world, BlockPos floor, Direction facing) {
		for (int depth = 1; depth <= WALL_DEPTH; depth++) {
			for (int h = 0; h <= HollowPortal.HEIGHT; h++) {
				BlockPos at = floor.offset(facing, depth).up(h);
				if (!world.getBlockState(at).isSolidBlock(world, at)) {
					return false;
				}
			}
		}
		return true;
	}

	/** The frame needs its own air to stand in, or it gets buried in the wall it leans on. */
	private boolean roomFor(StructureWorldAccess world, BlockPos base, Direction.Axis axis) {
		Direction across = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		for (int w = 0; w < HollowPortal.WIDTH; w++) {
			for (int h = 0; h < HollowPortal.HEIGHT; h++) {
				BlockPos at = base.offset(across, w).up(h);
				if (world.isOutOfHeightLimit(at) || !world.getBlockState(at).isAir()) {
					return false;
				}
			}
		}
		return true;
	}
}
