package com.unseen.mansion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The mansion's layout, as pure data. No Minecraft types, so it can be generated and checked without a
 * game running — see {@link #main}.
 * <p>
 * Doors are carved along a randomised spanning tree, which makes every room reachable <em>by
 * construction</em> rather than by luck. A sealed room is the classic procedural-generation bug and the
 * one most likely to survive playtesting unnoticed, because you cannot see the room you cannot get to.
 * Extra doors are then punched to create loops: a layout that is a pure tree has exactly one route
 * between any two points, and a player who can only ever backtrack the way they came feels safe.
 */
public final class MansionPlan {
	/** Rooms per side, per floor. */
	public static final int GRID = 5;
	/** Interior size of one room, in blocks. */
	public static final int ROOM = 7;
	/** Floors above ground. A basement is generated below these. */
	public static final int FLOORS = 2;
	/** Interior height of a floor. */
	public static final int FLOOR_HEIGHT = 5;

	/** doorEast[floor][x][z] — a doorway between room (x,z) and (x+1,z). */
	public final boolean[][][] doorEast = new boolean[FLOORS][GRID][GRID];
	/** doorSouth[floor][x][z] — a doorway between room (x,z) and (x,z+1). */
	public final boolean[][][] doorSouth = new boolean[FLOORS][GRID][GRID];
	/** Room hosting the stairwell on each floor. */
	public final int[] stairX = new int[FLOORS];
	public final int[] stairZ = new int[FLOORS];

	private final Random random;

	public MansionPlan(long seed) {
		this.random = new Random(seed);
		for (int floor = 0; floor < FLOORS; floor++) {
			carveSpanningTree(floor);
			addLoops(floor);
			this.stairX[floor] = this.random.nextInt(GRID);
			this.stairZ[floor] = this.random.nextInt(GRID);
		}
	}

	/** Randomised depth-first carve. Guarantees connectivity. */
	private void carveSpanningTree(int floor) {
		boolean[][] visited = new boolean[GRID][GRID];
		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[]{0, 0});
		visited[0][0] = true;

		while (!stack.isEmpty()) {
			int[] at = stack.peek();
			List<int[]> options = new ArrayList<>();
			for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = at[0] + step[0];
				int nz = at[1] + step[1];
				if (nx >= 0 && nx < GRID && nz >= 0 && nz < GRID && !visited[nx][nz]) {
					options.add(new int[]{nx, nz});
				}
			}
			if (options.isEmpty()) {
				stack.pop();
				continue;
			}
			int[] next = options.get(this.random.nextInt(options.size()));
			openBetween(floor, at[0], at[1], next[0], next[1]);
			visited[next[0]][next[1]] = true;
			stack.push(next);
		}
	}

	/** A handful of extra doorways, so the place has more than one route through it. */
	private void addLoops(int floor) {
		int extra = GRID;
		for (int i = 0; i < extra; i++) {
			int x = this.random.nextInt(GRID);
			int z = this.random.nextInt(GRID);
			if (this.random.nextBoolean()) {
				if (x + 1 < GRID) {
					this.doorEast[floor][x][z] = true;
				}
			} else if (z + 1 < GRID) {
				this.doorSouth[floor][x][z] = true;
			}
		}
	}

	private void openBetween(int floor, int ax, int az, int bx, int bz) {
		if (ax == bx) {
			this.doorSouth[floor][ax][Math.min(az, bz)] = true;
		} else {
			this.doorEast[floor][Math.min(ax, bx)][az] = true;
		}
	}

	public boolean connected(int floor, int x, int z, int nx, int nz) {
		if (x == nx) {
			return this.doorSouth[floor][x][Math.min(z, nz)];
		}
		if (z == nz) {
			return this.doorEast[floor][Math.min(x, nx)][z];
		}
		return false;
	}

	/** Number of rooms reachable from (0,0) on a floor. */
	public int reachableRooms(int floor) {
		boolean[][] seen = new boolean[GRID][GRID];
		Deque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[]{0, 0});
		seen[0][0] = true;
		int count = 0;
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			count++;
			for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = at[0] + step[0];
				int nz = at[1] + step[1];
				if (nx < 0 || nx >= GRID || nz < 0 || nz >= GRID || seen[nx][nz]) {
					continue;
				}
				if (this.connected(floor, at[0], at[1], nx, nz)) {
					seen[nx][nz] = true;
					queue.add(new int[]{nx, nz});
				}
			}
		}
		return count;
	}

	/** Total doorway count on a floor — used to confirm the layout is not a bare corridor. */
	public int doorCount(int floor) {
		int n = 0;
		for (int x = 0; x < GRID; x++) {
			for (int z = 0; z < GRID; z++) {
				if (this.doorEast[floor][x][z]) {
					n++;
				}
				if (this.doorSouth[floor][x][z]) {
					n++;
				}
			}
		}
		return n;
	}

	/** Footprint of the whole building, in blocks, including exterior walls. */
	public static int footprint() {
		return GRID * (ROOM + 1) + 1;
	}

	public static void main(String[] args) {
		boolean assertionsOn = false;
		assert assertionsOn = true;
		if (!assertionsOn) {
			throw new IllegalStateException("run with -ea or this check proves nothing");
		}
		int rooms = GRID * GRID;
		// Many seeds, because a connectivity bug can easily hide behind a lucky one.
		for (long seed = 0; seed < 500; seed++) {
			MansionPlan plan = new MansionPlan(seed);
			for (int floor = 0; floor < FLOORS; floor++) {
				int reachable = plan.reachableRooms(floor);
				assert reachable == rooms
						: "seed " + seed + " floor " + floor + ": only " + reachable + "/" + rooms
						+ " rooms reachable — a sealed room is unplayable";
				assert plan.doorCount(floor) >= rooms - 1
						: "seed " + seed + " floor " + floor + ": too few doorways to be connected";
				assert plan.stairX[floor] >= 0 && plan.stairX[floor] < GRID;
			}
		}
		List<Integer> doorCounts = new ArrayList<>();
		for (long seed = 0; seed < 20; seed++) {
			doorCounts.add(new MansionPlan(seed).doorCount(0));
		}
		Collections.sort(doorCounts);
		System.out.println("MansionPlan self-check passed: 500 seeds x " + FLOORS + " floors, all "
				+ rooms + " rooms reachable; footprint " + footprint() + "x" + footprint()
				+ "; doorways per floor " + doorCounts.get(0) + ".." + doorCounts.get(doorCounts.size() - 1));
	}
}
