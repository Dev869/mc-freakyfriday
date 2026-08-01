package com.unseen.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The room it takes people back to.
 * <p>
 * Minecraft-free so the geometry can be checked without a game running, same as the cottage and the well.
 * <p>
 * The design brief is one sentence: you should know what this room is before you have finished walking
 * into it. Faces on every wall at eye height, bones underfoot, one way in and no other way out.
 */
public final class LairPlan {

	/** Interior is (2 * HALF + 1) square. */
	public static final int HALF = 4;
	/** Interior height above the floor. */
	public static final int HEIGHT = 4;
	/** Trophies hang at eye level, not scattered — a row of faces reads as deliberate. */
	public static final int TROPHY_Y = 2;

	public enum Part {
		/** The open volume of the room. Placed first; everything else overwrites it. */
		AIR,
		FLOOR,
		BONES,
		WALL,
		CEILING,
		/** A face on the wall. Some are old, some are recent. */
		TROPHY,
		/** The gap it comes and goes through. */
		DOORWAY,
		/** The heap in the middle it sleeps on. */
		NEST,
		TAINT
	}

	public record Placed(int x, int y, int z, Part part) {
	}

	private LairPlan() {
	}

	public static int footprint() {
		return HALF * 2 + 3;
	}

	public static List<Placed> build(long seed) {
		java.util.Random random = new java.util.Random(seed);
		List<Placed> out = new ArrayList<>();

		// The doorway is centred on one of the four walls.
		int doorSide = random.nextInt(4);
		int[] door = doorPosition(doorSide);

		// Hollow it out first. Without this the whole thing generates as a solid block of stone.
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				for (int y = 0; y < HEIGHT; y++) {
					out.add(new Placed(x, y, z, Part.AIR));
				}
			}
		}

		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				// Bone underfoot, with dirt showing through in patches.
				out.add(new Placed(x, -1, z, random.nextInt(3) == 0 ? Part.BONES : Part.FLOOR));
				out.add(new Placed(x, HEIGHT, z, Part.CEILING));
			}
		}

		for (int y = 0; y < HEIGHT; y++) {
			for (int x = -HALF - 1; x <= HALF + 1; x++) {
				for (int z = -HALF - 1; z <= HALF + 1; z++) {
					if (Math.abs(x) != HALF + 1 && Math.abs(z) != HALF + 1) {
						continue;
					}
					if (x == door[0] && z == door[1] && y <= 1) {
						out.add(new Placed(x, y, z, Part.DOORWAY));
						continue;
					}
					out.add(new Placed(x, y, z, Part.WALL));
				}
			}
		}

		trophies(out, door);

		// The nest: a low heap in the middle, and the rot that comes with it.
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				out.add(new Placed(x, 0, z, Part.NEST));
			}
		}
		out.add(new Placed(0, 1, 0, Part.NEST));
		for (int i = 0; i < 8; i++) {
			int x = random.nextInt(HALF * 2 + 1) - HALF;
			int z = random.nextInt(HALF * 2 + 1) - HALF;
			out.add(new Placed(x, 0, z, Part.TAINT));
		}
		return out;
	}

	private static int[] doorPosition(int doorSide) {
		return switch (doorSide) {
			case 0 -> new int[] {0, -HALF - 1};
			case 1 -> new int[] {0, HALF + 1};
			case 2 -> new int[] {-HALF - 1, 0};
			default -> new int[] {HALF + 1, 0};
		};
	}

	/**
	 * Faces along the inside of every wall at eye height, evenly spaced and never in the doorway. The
	 * spacing is the point: a random scatter reads as decoration, a row reads as a collection.
	 */
	private static void trophies(List<Placed> out, int[] door) {
		for (int i = -HALF + 1; i <= HALF - 1; i += 2) {
			addTrophy(out, i, -HALF, door);
			addTrophy(out, i, HALF, door);
			addTrophy(out, -HALF, i, door);
			addTrophy(out, HALF, i, door);
		}
	}

	private static void addTrophy(List<Placed> out, int x, int z, int[] door) {
		// Never directly in front of the way in, or it gets destroyed by whatever walks through.
		if (Math.abs(x - door[0]) <= 1 && Math.abs(z - door[1]) <= 1) {
			return;
		}
		out.add(new Placed(x, TROPHY_Y, z, Part.TROPHY));
	}

	// --- self-check ---

	public static void main(String[] args) {
		for (long seed = 0; seed < 200; seed++) {
			List<Placed> plan = build(seed);
			check(plan.equals(build(seed)), "plan must be deterministic, seed " + seed);

			Set<String> wall = new HashSet<>();
			Set<String> door = new HashSet<>();
			for (Placed p : plan) {
				String key = p.x() + "," + p.y() + "," + p.z();
				if (p.part() == Part.WALL) {
					wall.add(key);
				} else if (p.part() == Part.DOORWAY) {
					door.add(key);
				}
			}

			// Exactly one way in: two blocks tall, in one wall.
			check(door.size() == 2, "expected one 2-high doorway, got " + door.size() + " seed " + seed);

			// Sealed otherwise — every perimeter column is wall or the doorway.
			for (int y = 0; y < HEIGHT; y++) {
				for (int x = -HALF - 1; x <= HALF + 1; x++) {
					for (int z = -HALF - 1; z <= HALF + 1; z++) {
						if (Math.abs(x) != HALF + 1 && Math.abs(z) != HALF + 1) {
							continue;
						}
						String key = x + "," + y + "," + z;
						check(wall.contains(key) || door.contains(key),
								"hole in the lair at " + key + " seed " + seed);
					}
				}
			}

			// Trophies must be on the inside face of a wall, at eye height, and never block the door.
			long trophies = 0;
			for (Placed p : plan) {
				if (p.part() != Part.TROPHY) {
					continue;
				}
				trophies++;
				check(p.y() == TROPHY_Y, "trophy off eye level, seed " + seed);
				check(Math.abs(p.x()) == HALF || Math.abs(p.z()) == HALF,
						"trophy not against a wall, seed " + seed);
				for (String d : door) {
					String[] c = d.split(",");
					check(!(Math.abs(p.x() - Integer.parseInt(c[0])) <= 1
									&& Math.abs(p.z() - Integer.parseInt(c[2])) <= 1),
							"trophy in the doorway, seed " + seed);
				}
			}
			check(trophies >= 8, "a lair with " + trophies + " faces is not a collection, seed " + seed);

			// The room has to actually be a room. This is not hypothetical: the first version of this
			// plan never hollowed the interior and would have generated a solid block of stone.
			Set<String> open = new HashSet<>();
			for (Placed p : plan) {
				if (p.part() == Part.AIR) {
					open.add(p.x() + "," + p.y() + "," + p.z());
				}
			}
			for (int y = 0; y < HEIGHT; y++) {
				for (int x = -HALF; x <= HALF; x++) {
					for (int z = -HALF; z <= HALF; z++) {
						check(open.contains(x + "," + y + "," + z),
								"lair interior not carved at " + x + "," + y + "," + z);
					}
				}
			}

			// Floor and ceiling must be complete, or it opens into raw stone.
			Set<String> floor = new HashSet<>();
			Set<String> ceiling = new HashSet<>();
			for (Placed p : plan) {
				if (p.part() == Part.FLOOR || p.part() == Part.BONES) {
					floor.add(p.x() + "," + p.z());
				} else if (p.part() == Part.CEILING) {
					ceiling.add(p.x() + "," + p.z());
				}
			}
			for (int x = -HALF; x <= HALF; x++) {
				for (int z = -HALF; z <= HALF; z++) {
					check(floor.contains(x + "," + z), "floor hole at " + x + "," + z + " seed " + seed);
					check(ceiling.contains(x + "," + z), "ceiling hole at " + x + "," + z);
				}
			}
		}
		System.out.println("LairPlan self-check passed: 200 seeds, footprint " + footprint()
				+ "x" + footprint());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
