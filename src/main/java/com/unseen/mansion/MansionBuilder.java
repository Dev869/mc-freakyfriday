package com.unseen.mansion;

import com.unseen.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

import java.util.Random;

/**
 * Turns a {@link MansionPlan} into blocks.
 * <p>
 * Design intent, since a mansion is a horror level and not just architecture:
 * <ul>
 *   <li><b>Doors, not archways.</b> Most openings get a real door, because the Stalker opens doors and
 *       leaves them standing open — the level has to give that mechanic something to act on.</li>
 *   <li><b>Deliberately underlit.</b> A few soul lanterns and candles, nowhere near enough. The mod's
 *       threat gate keys off darkness, so a dark interior is what makes the building dangerous.</li>
 *   <li><b>Hiding places scattered by design</b> — wardrobes and beds, so the hiding mechanic is
 *       available in a panic without the player having had to build anything.</li>
 * </ul>
 */
public final class MansionBuilder {
	private static final int WALL = 1;
	private static final int STRIDE = MansionPlan.ROOM + WALL;

	private MansionBuilder() {
	}

	/**
	 * Builds at {@code origin}, which becomes the north-west corner of the ground floor.
	 *
	 * @return number of blocks placed
	 */
	public static int build(WorldAccess world, BlockPos origin, long seed) {
		MansionPlan plan = new MansionPlan(seed);
		Random random = new Random(seed);
		Counter counter = new Counter();
		int size = MansionPlan.footprint();

		// Basement first, so upper floors sit on something.
		buildBasement(world, origin, size, counter, random);

		for (int floor = 0; floor < MansionPlan.FLOORS; floor++) {
			// Relative to origin, never absolute: every placement goes through BlockPos#add, which is
			// itself relative. Mixing the two silently doubles the Y and builds the house in the sky.
			int baseY = floor * (MansionPlan.FLOOR_HEIGHT + 1);
			buildFloorSlab(world, origin, baseY, size, floor, counter);
			buildWalls(world, origin, baseY, plan, floor, counter, random);
			buildStairwell(world, origin, baseY, plan, floor, counter);
			dressRooms(world, origin, baseY, plan, floor, counter, random);
		}

		int roofY = MansionPlan.FLOORS * (MansionPlan.FLOOR_HEIGHT + 1);
		buildRoof(world, origin, roofY, size, counter);
		return counter.placed;
	}

	private static void buildBasement(WorldAccess world, BlockPos origin, int size, Counter counter, Random random) {
		int top = -1;
		int bottom = top - MansionPlan.FLOOR_HEIGHT;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				set(world, origin.add(x, bottom, z), Blocks.STONE_BRICKS.getDefaultState(), counter);
				boolean edge = x == 0 || z == 0 || x == size - 1 || z == size - 1;
				for (int y = bottom + 1; y <= top; y++) {
					BlockState state = edge
							? (random.nextInt(4) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS)
							.getDefaultState()
							: Blocks.AIR.getDefaultState();
					set(world, origin.add(x, y, z), state, counter);
				}
			}
		}
		// A handful of pillars, so it is not one empty box.
		for (int i = 0; i < 12; i++) {
			int px = 3 + random.nextInt(size - 6);
			int pz = 3 + random.nextInt(size - 6);
			for (int y = bottom + 1; y <= top; y++) {
				set(world, origin.add(px, y, pz), Blocks.STONE_BRICKS.getDefaultState(), counter);
			}
		}
	}

	private static void buildFloorSlab(WorldAccess world, BlockPos origin, int baseY, int size, int floor,
	                                   Counter counter) {
		Block material = floor == 0 ? Blocks.POLISHED_ANDESITE : Blocks.DARK_OAK_PLANKS;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				set(world, origin.add(x, baseY - 1, z), material.getDefaultState(), counter);
			}
		}
	}

	private static void buildWalls(WorldAccess world, BlockPos origin, int baseY, MansionPlan plan, int floor,
	                               Counter counter, Random random) {
		int size = MansionPlan.footprint();
		int height = MansionPlan.FLOOR_HEIGHT;

		// Clear the interior volume, then raise every wall line.
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				for (int y = baseY; y < baseY + height; y++) {
					set(world, origin.add(x, y, z), Blocks.AIR.getDefaultState(), counter);
				}
			}
		}

		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				boolean gridLine = x % STRIDE == 0 || z % STRIDE == 0;
				if (!gridLine) {
					continue;
				}
				boolean exterior = x == 0 || z == 0 || x == size - 1 || z == size - 1;
				boolean post = x % STRIDE == 0 && z % STRIDE == 0;
				for (int y = baseY; y < baseY + height; y++) {
					BlockState state = post
							? Blocks.DARK_OAK_LOG.getDefaultState()
							: Blocks.DARK_OAK_PLANKS.getDefaultState();
					// Windows on the outside, at head height.
					if (exterior && !post && (y == baseY + 2 || y == baseY + 3)
							&& ((x + z) % 4 == 1)) {
						state = Blocks.GLASS_PANE.getDefaultState();
					}
					set(world, origin.add(x, y, z), state, counter);
				}
			}
		}

		// Punch the doorways the plan calls for.
		for (int rx = 0; rx < MansionPlan.GRID; rx++) {
			for (int rz = 0; rz < MansionPlan.GRID; rz++) {
				if (rx + 1 < MansionPlan.GRID && plan.doorEast[floor][rx][rz]) {
					int wx = (rx + 1) * STRIDE;
					int wz = rz * STRIDE + 1 + MansionPlan.ROOM / 2;
					openDoorway(world, origin, baseY, wx, wz, Direction.EAST, counter, random);
				}
				if (rz + 1 < MansionPlan.GRID && plan.doorSouth[floor][rx][rz]) {
					int wx = rx * STRIDE + 1 + MansionPlan.ROOM / 2;
					int wz = (rz + 1) * STRIDE;
					openDoorway(world, origin, baseY, wx, wz, Direction.SOUTH, counter, random);
				}
			}
		}

		// Front entrance, so the building is enterable without breaking in.
		int mid = MansionPlan.footprint() / 2;
		openDoorway(world, origin, baseY, mid, 0, Direction.SOUTH, counter, random);
	}

	/** A two-high gap, usually with a real door in it. */
	private static void openDoorway(WorldAccess world, BlockPos origin, int baseY, int x, int z,
	                                Direction facing, Counter counter, Random random) {
		BlockPos lower = origin.add(x, baseY, z);
		set(world, lower, Blocks.AIR.getDefaultState(), counter);
		set(world, lower.up(), Blocks.AIR.getDefaultState(), counter);
		if (random.nextInt(10) < 7) {
			BlockState door = Blocks.DARK_OAK_DOOR.getDefaultState()
					.with(DoorBlock.FACING, facing)
					.with(DoorBlock.OPEN, random.nextInt(4) == 0);
			set(world, lower, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER), counter);
			set(world, lower.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), counter);
		}
	}

	/** A hole in the ceiling plus a staircase up to it. */
	private static void buildStairwell(WorldAccess world, BlockPos origin, int baseY, MansionPlan plan, int floor,
	                                   Counter counter) {
		int roomX = plan.stairX[floor] * STRIDE + 1;
		int roomZ = plan.stairZ[floor] * STRIDE + 1;
		int ceilingY = baseY + MansionPlan.FLOOR_HEIGHT;

		// Open the ceiling above the stairwell.
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				set(world, origin.add(roomX + dx, ceilingY, roomZ + dz), Blocks.AIR.getDefaultState(), counter);
			}
		}
		for (int step = 0; step < MansionPlan.FLOOR_HEIGHT; step++) {
			int y = baseY + step;
			int z = roomZ + Math.min(step, 2);
			for (int dx = 0; dx < 3; dx++) {
				set(world, origin.add(roomX + dx, y, z),
						Blocks.DARK_OAK_STAIRS.getDefaultState()
								.with(StairsBlock.FACING, Direction.SOUTH), counter);
				// Solid fill beneath, so the staircase is walkable rather than floating.
				for (int fill = baseY; fill < y; fill++) {
					set(world, origin.add(roomX + dx, fill, z), Blocks.DARK_OAK_PLANKS.getDefaultState(), counter);
				}
			}
		}
	}

	/** Furniture, hiding places, cobwebs, and not nearly enough light. */
	private static void dressRooms(WorldAccess world, BlockPos origin, int baseY, MansionPlan plan, int floor,
	                               Counter counter, Random random) {
		for (int rx = 0; rx < MansionPlan.GRID; rx++) {
			for (int rz = 0; rz < MansionPlan.GRID; rz++) {
				int x0 = rx * STRIDE + 1;
				int z0 = rz * STRIDE + 1;
				boolean isStairRoom = rx == plan.stairX[floor] && rz == plan.stairZ[floor];
				if (isStairRoom) {
					continue;
				}
				int roll = random.nextInt(10);
				if (roll < 3) {
					// Bedroom: a bed is also a hiding place.
					place(world, origin.add(x0 + 1, baseY, z0 + 1), Blocks.RED_BED.getDefaultState()
							.with(Properties.HORIZONTAL_FACING, Direction.SOUTH), counter);
					place(world, origin.add(x0 + 1, baseY, z0 + 2), Blocks.RED_BED.getDefaultState()
							.with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
							.with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD),
							counter);
					place(world, origin.add(x0 + 4, baseY, z0 + 1), ModBlocks.WARDROBE.getDefaultState(), counter);
				} else if (roll < 5) {
					// Study.
					for (int i = 0; i < MansionPlan.ROOM - 2; i++) {
						place(world, origin.add(x0 + i, baseY, z0), Blocks.BOOKSHELF.getDefaultState(), counter);
						place(world, origin.add(x0 + i, baseY + 1, z0), Blocks.BOOKSHELF.getDefaultState(), counter);
					}
					place(world, origin.add(x0 + 3, baseY, z0 + 3), ModBlocks.WARDROBE.getDefaultState(), counter);
				} else if (roll < 7) {
					// Something happened in this one.
					for (int i = 0; i < 6; i++) {
						BlockPos at = origin.add(x0 + random.nextInt(MansionPlan.ROOM),
								baseY, z0 + random.nextInt(MansionPlan.ROOM));
						place(world, at, ModBlocks.VISCERA.getDefaultState(), counter);
					}
					place(world, origin.add(x0 + 2, baseY, z0 + 2), ModBlocks.WARDROBE.getDefaultState(), counter);
				}

				// Cobwebs in the corners of every room.
				for (int i = 0; i < 3; i++) {
					if (random.nextBoolean()) {
						place(world, origin.add(x0 + random.nextInt(MansionPlan.ROOM),
										baseY + MansionPlan.FLOOR_HEIGHT - 2,
										z0 + random.nextInt(MansionPlan.ROOM)),
								Blocks.COBWEB.getDefaultState(), counter);
					}
				}
				// Sparse light. One room in three, and only a candle.
				if (random.nextInt(3) == 0) {
					place(world, origin.add(x0 + 3, baseY, z0 + 3), Blocks.CANDLE.getDefaultState()
							.with(Properties.LIT, true), counter);
				}
			}
		}
	}

	private static void buildRoof(WorldAccess world, BlockPos origin, int roofY, int size, Counter counter) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				set(world, origin.add(x, roofY - 1, z), Blocks.DARK_OAK_PLANKS.getDefaultState(), counter);
				boolean edge = x == 0 || z == 0 || x == size - 1 || z == size - 1;
				set(world, origin.add(x, roofY, z), edge
						? Blocks.DARK_OAK_FENCE.getDefaultState()
						: Blocks.AIR.getDefaultState(), counter);
			}
		}
	}

	/** Unconditional set, clamped to the world. The basement can otherwise dig below bedrock. */
	private static void set(WorldAccess world, BlockPos pos, BlockState state, Counter counter) {
		if (world.isOutOfHeightLimit(pos.getY())) {
			return;
		}
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		counter.placed++;
	}

	/** Set only into air, so furniture never eats a wall. */
	private static void place(WorldAccess world, BlockPos pos, BlockState state, Counter counter) {
		if (world.getBlockState(pos).isAir()) {
			set(world, pos, state, counter);
		}
	}

	private static final class Counter {
		private int placed;
	}
}
