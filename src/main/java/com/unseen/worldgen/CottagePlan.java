package com.unseen.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The layout of a storybook cottage, and the cellar nobody was supposed to find.
 * <p>
 * Deliberately free of Minecraft types so the geometry can be checked without a game running — the same
 * split that caught the mansion's Y bug. {@link CottageFeature} does the block placement; everything
 * about <em>where</em> things go is decided here.
 * <p>
 * The cottage has to be genuinely pretty. It is the setup for the whole modpack: if the meadow and the
 * cottages are not somewhere a player would happily build a base, then finding a way into the Hollow
 * under the floorboards costs nothing.
 */
public final class CottagePlan {

	/** Outer footprint is (2 * HALF + 1) square. */
	public static final int HALF = 3;
	/** Height of the walls above the floor. */
	public static final int WALL_HEIGHT = 3;
	/** Floor sits at y = 0; the cellar void runs from CELLAR_FLOOR up to -2. */
	public static final int CELLAR_FLOOR = -5;

	public enum Part {
		FOUNDATION,
		FLOOR,
		WALL,
		WINDOW,
		DOOR_LOWER,
		DOOR_UPPER,
		ROOF_NORTH,
		ROOF_SOUTH,
		RIDGE,
		LANTERN,
		RUG,
		TRAPDOOR,
		SHAFT,
		LADDER,
		CELLAR_AIR,
		CELLAR_WALL,
		/** Marks where the way into the Hollow is cut, if this cottage has one. */
		PORTAL,
		FLOWER,
		PATH,
		GARDEN_FENCE,
		GARDEN_GATE,
		GARDEN_SOIL,
		GARDEN_WATER,
		GARDEN_CROP
	}

	/** Outer fence of the vegetable plot is (2 * GARDEN_HALF + 1) square. */
	public static final int GARDEN_HALF = 3;
	/** How far the plot's centre sits from the cottage centre, on the side away from the door. */
	public static final int GARDEN_OFFSET = 7;

	public record Placed(int x, int y, int z, Part part) {
	}

	private CottagePlan() {
	}

	public static int footprint() {
		return HALF * 2 + 1;
	}

	/**
	 * @param seed       decides window placement, garden scatter and door wall
	 * @param withPortal whether this cottage is one of the ones hiding a way through
	 */
	public static List<Placed> build(long seed, boolean withPortal) {
		Random random = new Random(seed);
		List<Placed> out = new ArrayList<>();

		// The door faces a randomly chosen wall, so a cluster of cottages does not read as stamped.
		int doorSide = random.nextInt(4);

		foundationAndFloor(out);
		walls(out, doorSide);
		roof(out);
		cellar(out, withPortal);
		garden(out, random, doorSide);
		vegetablePlot(out, random, doorSide);
		return out;
	}

	private static void foundationAndFloor(List<Placed> out) {
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				out.add(new Placed(x, -1, z, Part.FOUNDATION));
				out.add(new Placed(x, 0, z, Part.FLOOR));
			}
		}
	}

	private static void walls(List<Placed> out, int doorSide) {
		int[] doorPos = doorPosition(doorSide);
		for (int y = 1; y <= WALL_HEIGHT; y++) {
			for (int x = -HALF; x <= HALF; x++) {
				for (int z = -HALF; z <= HALF; z++) {
					if (Math.abs(x) != HALF && Math.abs(z) != HALF) {
						continue;
					}
					boolean corner = Math.abs(x) == HALF && Math.abs(z) == HALF;
					if (x == doorPos[0] && z == doorPos[1] && y <= 2) {
						out.add(new Placed(x, y, z, y == 1 ? Part.DOOR_LOWER : Part.DOOR_UPPER));
						continue;
					}
					// Windows at head height on the middle of every wall that is not the door's.
					if (y == 2 && !corner && isWallMidpoint(x, z) && !(x == doorPos[0] && z == doorPos[1])) {
						out.add(new Placed(x, y, z, Part.WINDOW));
						continue;
					}
					out.add(new Placed(x, y, z, Part.WALL));
				}
			}
		}
		out.add(new Placed(0, WALL_HEIGHT, 0, Part.LANTERN));
	}

	private static boolean isWallMidpoint(int x, int z) {
		return (Math.abs(x) == HALF && z == 0) || (Math.abs(z) == HALF && x == 0);
	}

	/** Door sits at the middle of one of the four walls. */
	private static int[] doorPosition(int doorSide) {
		return switch (doorSide) {
			case 0 -> new int[] {0, -HALF};
			case 1 -> new int[] {0, HALF};
			case 2 -> new int[] {-HALF, 0};
			default -> new int[] {HALF, 0};
		};
	}

	/**
	 * A gable running east-west: each course steps in from both sides until they meet at the ridge, so
	 * every column of the footprint ends up covered.
	 */
	private static void roof(List<Placed> out) {
		int y = WALL_HEIGHT + 1;
		for (int inset = 0; inset <= HALF; inset++) {
			int north = -HALF + inset;
			int south = HALF - inset;
			for (int x = -HALF; x <= HALF; x++) {
				if (north == south) {
					out.add(new Placed(x, y, north, Part.RIDGE));
				} else {
					out.add(new Placed(x, y, north, Part.ROOF_NORTH));
					out.add(new Placed(x, y, south, Part.ROOF_SOUTH));
					// Fill the gap under the eaves so the roof is solid rather than a shell.
					for (int z = north + 1; z < south; z++) {
						if (inset > 0) {
							out.add(new Placed(x, y - 1, z, Part.RIDGE));
						}
					}
				}
			}
			y++;
		}
	}

	/**
	 * The cellar is always there, whether or not it has a way through. An empty stone room under a
	 * flower cottage is already wrong; the ones with a portal are worse.
	 */
	private static void cellar(List<Placed> out, boolean withPortal) {
		int inner = HALF - 1;
		for (int x = -inner; x <= inner; x++) {
			for (int z = -inner; z <= inner; z++) {
				for (int y = CELLAR_FLOOR; y <= -2; y++) {
					out.add(new Placed(x, y, z, Part.CELLAR_AIR));
				}
			}
		}
		// Shell it: floor, ceiling and four walls one block outside the void.
		for (int x = -inner - 1; x <= inner + 1; x++) {
			for (int z = -inner - 1; z <= inner + 1; z++) {
				out.add(new Placed(x, CELLAR_FLOOR - 1, z, Part.CELLAR_WALL));
				for (int y = CELLAR_FLOOR; y <= -2; y++) {
					if (Math.abs(x) == inner + 1 || Math.abs(z) == inner + 1) {
						out.add(new Placed(x, y, z, Part.CELLAR_WALL));
					}
				}
			}
		}

		// The way down, hidden under a rug in the corner of the room.
		int tx = inner;
		int tz = inner;
		out.add(new Placed(tx, 0, tz, Part.TRAPDOOR));
		out.add(new Placed(tx, 1, tz, Part.RUG));
		out.add(new Placed(tx, -1, tz, Part.SHAFT));
		for (int y = -1; y >= CELLAR_FLOOR + 1; y--) {
			out.add(new Placed(tx, y, tz, Part.LADDER));
		}

		if (withPortal) {
			// Cut into the far cellar wall, opposite the ladder, so it is the first thing you see
			// when you turn around at the bottom.
			out.add(new Placed(-inner, CELLAR_FLOOR, -inner, Part.PORTAL));
		}
	}

	/**
	 * Flowers and a short path to the door. Cheap, and it is most of why the place reads as lovely.
	 * <p>
	 * Ground level outside the house is y = -1, not y = 0. The cottage's floor sits at y = 0 because
	 * that is the first <em>air</em> block above the terrain — the foundation at y = -1 is the block
	 * that replaces the grass. Anything meant to sit on the ground outside therefore goes at y = -1,
	 * and anything standing on the ground goes at y = 0.
	 */
	private static void garden(List<Placed> out, Random random, int doorSide) {
		int[] door = doorPosition(doorSide);
		int stepX = Integer.signum(door[0]);
		int stepZ = Integer.signum(door[1]);
		for (int i = 1; i <= 3; i++) {
			out.add(new Placed(door[0] + stepX * i, -1, door[1] + stepZ * i, Part.PATH));
		}
		int flowers = 10 + random.nextInt(10);
		Set<Long> taken = new HashSet<>();
		for (int i = 0; i < flowers; i++) {
			int x = random.nextInt(HALF * 2 + 5) - (HALF + 2);
			int z = random.nextInt(HALF * 2 + 5) - (HALF + 2);
			// Outside the walls only, and never on the path.
			if (Math.abs(x) <= HALF && Math.abs(z) <= HALF) {
				continue;
			}
			if (taken.add(((long) x << 32) ^ (z & 0xffffffffL))) {
				out.add(new Placed(x, 0, z, Part.FLOWER));
			}
		}
	}

	/**
	 * A fenced vegetable plot on the far side of the house from the door.
	 * <p>
	 * This is the single cheapest thing that makes the meadow read as lived in rather than decorated.
	 * A cottage on its own is scenery; a cottage with somebody's carrots behind it is a home, and the
	 * cellar underneath is worse for it.
	 */
	private static void vegetablePlot(List<Placed> out, Random random, int doorSide) {
		int[] door = doorPosition(doorSide);
		// Directly opposite the door, so the path to the front stays clear.
		int cx = -Integer.signum(door[0]) * GARDEN_OFFSET;
		int cz = -Integer.signum(door[1]) * GARDEN_OFFSET;

		// The gate faces back toward the house.
		int gateX = cx + Integer.signum(door[0]) * GARDEN_HALF;
		int gateZ = cz + Integer.signum(door[1]) * GARDEN_HALF;

		for (int dx = -GARDEN_HALF; dx <= GARDEN_HALF; dx++) {
			for (int dz = -GARDEN_HALF; dz <= GARDEN_HALF; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				boolean edge = Math.abs(dx) == GARDEN_HALF || Math.abs(dz) == GARDEN_HALF;
				if (edge) {
					out.add(new Placed(x, 0, z,
							x == gateX && z == gateZ ? Part.GARDEN_GATE : Part.GARDEN_FENCE));
					continue;
				}
				if (dx == 0 && dz == 0) {
					// One water source in the middle. Everything inside the fence is within four
					// blocks of it, which is exactly what farmland needs to stay wet.
					out.add(new Placed(x, -1, z, Part.GARDEN_WATER));
					continue;
				}
				out.add(new Placed(x, -1, z, Part.GARDEN_SOIL));
				out.add(new Placed(x, 0, z, Part.GARDEN_CROP));
			}
		}
	}

	// --- self-check ---

	public static void main(String[] args) {
		for (long seed = 0; seed < 500; seed++) {
			boolean portal = seed % 3 == 0;
			List<Placed> plan = build(seed, portal);

			check(plan.equals(build(seed, portal)), "plan must be deterministic for a seed");

			long doorLower = plan.stream().filter(p -> p.part() == Part.DOOR_LOWER).count();
			long doorUpper = plan.stream().filter(p -> p.part() == Part.DOOR_UPPER).count();
			long trapdoors = plan.stream().filter(p -> p.part() == Part.TRAPDOOR).count();
			check(doorLower == 1 && doorUpper == 1, "exactly one door, seed " + seed);
			check(trapdoors == 1, "exactly one way down, seed " + seed);

			// Enclosure: every perimeter column at wall height must be filled by something.
			Set<String> solid = new HashSet<>();
			for (Placed p : plan) {
				if (p.part() == Part.WALL || p.part() == Part.WINDOW
						|| p.part() == Part.DOOR_LOWER || p.part() == Part.DOOR_UPPER) {
					solid.add(p.x() + "," + p.y() + "," + p.z());
				}
			}
			for (int y = 1; y <= WALL_HEIGHT; y++) {
				for (int x = -HALF; x <= HALF; x++) {
					for (int z = -HALF; z <= HALF; z++) {
						if (Math.abs(x) != HALF && Math.abs(z) != HALF) {
							continue;
						}
						check(solid.contains(x + "," + y + "," + z),
								"wall gap at " + x + "," + y + "," + z + " seed " + seed);
					}
				}
			}

			// Roof: every column of the footprint is covered by something above the walls.
			Set<String> roofed = new HashSet<>();
			for (Placed p : plan) {
				if (p.part() == Part.ROOF_NORTH || p.part() == Part.ROOF_SOUTH || p.part() == Part.RIDGE) {
					roofed.add(p.x() + "," + p.z());
				}
			}
			for (int x = -HALF; x <= HALF; x++) {
				for (int z = -HALF; z <= HALF; z++) {
					check(roofed.contains(x + "," + z), "roof hole at " + x + "," + z + " seed " + seed);
				}
			}

			// The trapdoor has to open onto the shaft, and the shaft has to reach the cellar.
			Placed trap = plan.stream().filter(p -> p.part() == Part.TRAPDOOR).findFirst().orElseThrow();
			check(Math.abs(trap.x()) < HALF && Math.abs(trap.z()) < HALF,
					"trapdoor must be inside the room, seed " + seed);
			boolean shaft = plan.stream().anyMatch(p -> p.part() == Part.SHAFT
					&& p.x() == trap.x() && p.z() == trap.z() && p.y() == -1);
			check(shaft, "trapdoor opens onto nothing, seed " + seed);
			long ladder = plan.stream().filter(p -> p.part() == Part.LADDER
					&& p.x() == trap.x() && p.z() == trap.z()).count();
			check(ladder >= Math.abs(CELLAR_FLOOR) - 1, "ladder does not reach the floor, seed " + seed);

			// Cellar void must be fully shelled, or it opens into raw stone.
			Set<String> air = new HashSet<>();
			Set<String> shell = new HashSet<>();
			for (Placed p : plan) {
				if (p.part() == Part.CELLAR_AIR) {
					air.add(p.x() + "," + p.y() + "," + p.z());
				} else if (p.part() == Part.CELLAR_WALL) {
					shell.add(p.x() + "," + p.y() + "," + p.z());
				}
			}
			for (String cell : air) {
				String[] c = cell.split(",");
				int x = Integer.parseInt(c[0]);
				int y = Integer.parseInt(c[1]);
				int z = Integer.parseInt(c[2]);
				for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
					String n = (x + d[0]) + "," + (y + d[1]) + "," + (z + d[2]);
					check(air.contains(n) || shell.contains(n),
							"cellar leaks at " + n + " seed " + seed);
				}
			}

			// The vegetable plot must not clip the house, must be fenced with exactly one way in, and
			// every bed of soil must be close enough to the water to stay wet.
			long gates = plan.stream().filter(p -> p.part() == Part.GARDEN_GATE).count();
			check(gates == 1, "garden needs exactly one gate, seed " + seed);

			List<Placed> water = plan.stream().filter(p -> p.part() == Part.GARDEN_WATER).toList();
			check(water.size() == 1, "garden needs exactly one water source, seed " + seed);

			for (Placed p : plan) {
				boolean garden = p.part() == Part.GARDEN_FENCE || p.part() == Part.GARDEN_GATE
						|| p.part() == Part.GARDEN_SOIL || p.part() == Part.GARDEN_CROP
						|| p.part() == Part.GARDEN_WATER;
				if (!garden) {
					continue;
				}
				check(Math.abs(p.x()) > HALF || Math.abs(p.z()) > HALF,
						"garden overlaps the house at " + p.x() + "," + p.z() + " seed " + seed);
				if (p.part() == Part.GARDEN_SOIL) {
					int dist = Math.max(Math.abs(p.x() - water.get(0).x()),
							Math.abs(p.z() - water.get(0).z()));
					check(dist <= 4, "farmland at " + p.x() + "," + p.z() + " will dry out, seed " + seed);
				}
			}

			// Ground-plane convention: anything replacing terrain sits at y = -1, anything standing on
			// it at y = 0. Getting this wrong silently places nothing at all, which is how the flowers
			// and the path went unnoticed for several iterations.
			for (Placed p : plan) {
				switch (p.part()) {
					case PATH, GARDEN_SOIL, GARDEN_WATER ->
							check(p.y() == -1, p.part() + " must sit in the ground, seed " + seed);
					case FLOWER, GARDEN_FENCE, GARDEN_GATE, GARDEN_CROP ->
							check(p.y() == 0, p.part() + " must stand on the ground, seed " + seed);
					default -> {
					}
				}
			}

			long portals = plan.stream().filter(p -> p.part() == Part.PORTAL).count();
			check(portals == (portal ? 1 : 0), "portal count wrong, seed " + seed);
		}
		System.out.println("CottagePlan self-check passed: 500 seeds, footprint " + footprint()
				+ "x" + footprint());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
