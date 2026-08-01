package com.unseen.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A wishing well, and the room at the bottom of it.
 * <p>
 * Minecraft-free so the geometry can be checked without a game running, same as {@link CottagePlan}.
 * <p>
 * Deliberately dry. Water would look prettier from above, but the beat is climbing down a well in a
 * meadow full of flowers and finding a room that should not be there, and you cannot climb down a well
 * that is full of water.
 */
public final class WellPlan {

	/** Depth of the shaft below ground before the chamber opens out. */
	public static final int SHAFT_BOTTOM = -9;
	/** The chamber floor. Its void runs from here up to SHAFT_BOTTOM. */
	public static final int CHAMBER_FLOOR = -13;
	/** Chamber interior is (2 * CHAMBER_HALF + 1) square. */
	public static final int CHAMBER_HALF = 2;

	public enum Part {
		RIM,
		POST,
		ROOF,
		LANTERN,
		SHAFT,
		LADDER,
		CHAMBER_AIR,
		CHAMBER_WALL,
		PORTAL
	}

	public record Placed(int x, int y, int z, Part part) {
	}

	private WellPlan() {
	}

	public static List<Placed> build(boolean withPortal) {
		List<Placed> out = new ArrayList<>();

		// The kerb: a 3x3 ring of cobble at ground level with the mouth open in the middle.
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (x != 0 || z != 0) {
					out.add(new Placed(x, 0, z, Part.RIM));
				}
			}
		}
		// Four corner posts holding up a little roof.
		for (int x = -1; x <= 1; x += 2) {
			for (int z = -1; z <= 1; z += 2) {
				out.add(new Placed(x, 1, z, Part.POST));
				out.add(new Placed(x, 2, z, Part.POST));
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				out.add(new Placed(x, 3, z, Part.ROOF));
			}
		}
		out.add(new Placed(0, 2, 0, Part.LANTERN));

		// The shaft, with a ladder the whole way so the climb down is a decision and not a fall.
		for (int y = -1; y >= SHAFT_BOTTOM; y--) {
			out.add(new Placed(0, y, 0, Part.SHAFT));
			out.add(new Placed(0, y, 0, Part.LADDER));
		}

		chamber(out, withPortal);
		return out;
	}

	/**
	 * The chamber hangs off the shaft rather than being centred on it: the well mouth has to stay at
	 * the middle of the kerb, and a ladder needs a wall behind it. Putting the shaft in a corner of the
	 * room gives it one all the way down, instead of leaving the last stretch floating in open air.
	 */
	private static void chamber(List<Placed> out, boolean withPortal) {
		int far = CHAMBER_HALF * 2;
		for (int x = 0; x <= far; x++) {
			for (int z = 0; z <= far; z++) {
				for (int y = CHAMBER_FLOOR; y <= SHAFT_BOTTOM; y++) {
					out.add(new Placed(x, y, z, Part.CHAMBER_AIR));
				}
			}
		}
		for (int x = -1; x <= far + 1; x++) {
			for (int z = -1; z <= far + 1; z++) {
				out.add(new Placed(x, CHAMBER_FLOOR - 1, z, Part.CHAMBER_WALL));
				// Ceiling too, except the hole the shaft comes down through.
				if (x != 0 || z != 0) {
					out.add(new Placed(x, SHAFT_BOTTOM + 1, z, Part.CHAMBER_WALL));
				}
				for (int y = CHAMBER_FLOOR; y <= SHAFT_BOTTOM; y++) {
					if (x == -1 || z == -1 || x == far + 1 || z == far + 1) {
						out.add(new Placed(x, y, z, Part.CHAMBER_WALL));
					}
				}
			}
		}
		// The ladder keeps going to the floor, or the last stretch is a drop in the dark.
		for (int y = SHAFT_BOTTOM; y > CHAMBER_FLOOR; y--) {
			out.add(new Placed(0, y, 0, Part.LADDER));
		}
		if (withPortal) {
			// Against the far wall, so it faces you as you come off the ladder.
			out.add(new Placed(CHAMBER_HALF - 1, CHAMBER_FLOOR, far, Part.PORTAL));
		}
	}

	// --- self-check ---

	public static void main(String[] args) {
		for (boolean portal : new boolean[] {false, true}) {
			List<Placed> plan = build(portal);
			check(plan.equals(build(portal)), "plan must be deterministic");

			Set<String> at = new HashSet<>();
			for (Placed p : plan) {
				at.add(p.part() + "@" + p.x() + "," + p.y() + "," + p.z());
			}

			check(plan.stream().filter(p -> p.part() == Part.POST).count() == 8, "four posts, two tall");
			check(plan.stream().filter(p -> p.part() == Part.ROOF).count() == 9, "roof covers 3x3");
			check(plan.stream().noneMatch(p -> p.part() == Part.RIM && p.x() == 0 && p.z() == 0),
					"the well must be open in the middle");

			// The shaft has to be continuous from the mouth to the chamber, laddered the whole way.
			for (int y = -1; y > CHAMBER_FLOOR; y--) {
				check(at.contains(Part.LADDER + "@0," + y + ",0"), "ladder gap at y=" + y);
			}
			for (int y = -1; y >= SHAFT_BOTTOM; y--) {
				check(at.contains(Part.SHAFT + "@0," + y + ",0"), "shaft gap at y=" + y);
			}

			// Chamber void fully shelled, or it opens into raw stone.
			Set<String> air = new HashSet<>();
			Set<String> shell = new HashSet<>();
			for (Placed p : plan) {
				String key = p.x() + "," + p.y() + "," + p.z();
				if (p.part() == Part.CHAMBER_AIR || p.part() == Part.SHAFT) {
					air.add(key);
				} else if (p.part() == Part.CHAMBER_WALL) {
					shell.add(key);
				}
			}
			for (String cell : air) {
				String[] c = cell.split(",");
				int x = Integer.parseInt(c[0]);
				int y = Integer.parseInt(c[1]);
				int z = Integer.parseInt(c[2]);
				if (y > SHAFT_BOTTOM) {
					continue; // the shaft runs through undisturbed ground; only the chamber is shelled
				}
				for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}}) {
					String n = (x + d[0]) + "," + (y + d[1]) + "," + (z + d[2]);
					check(air.contains(n) || shell.contains(n), "chamber leaks at " + n);
				}
			}

			// Every ladder inside the chamber needs a wall behind it. Without this the last stretch
			// floats in the middle of the room and breaks the moment anything updates it.
			for (Placed p : plan) {
				if (p.part() != Part.LADDER || p.y() > SHAFT_BOTTOM) {
					continue;
				}
				boolean backed = shell.contains((p.x() - 1) + "," + p.y() + "," + p.z())
						|| shell.contains((p.x() + 1) + "," + p.y() + "," + p.z())
						|| shell.contains(p.x() + "," + p.y() + "," + (p.z() - 1))
						|| shell.contains(p.x() + "," + p.y() + "," + (p.z() + 1));
				check(backed, "ladder has nothing behind it at y=" + p.y());
			}

			check(plan.stream().filter(p -> p.part() == Part.PORTAL).count() == (portal ? 1 : 0),
					"portal count wrong");
		}
		System.out.println("WellPlan self-check passed: shaft " + (-SHAFT_BOTTOM) + " deep, chamber "
				+ (CHAMBER_HALF * 2 + 1) + "x" + (CHAMBER_HALF * 2 + 1));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
