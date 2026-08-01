package com.unseen.worldgen;

/**
 * Where a footbridge goes, decided without a world.
 *
 * <p>Same split as {@link CottagePlan} and {@link WellPlan}: the choice lives here where it can be
 * checked, and {@link BridgeFeature} only turns the answer into blocks.
 */
public final class BridgePlan {

	/** Narrower than this is a puddle you step over. */
	private static final int MIN_WATER = 2;

	private BridgePlan() {
	}

	/**
	 * The stretch of deck to build, as indices into {@code water}, or null if there is nothing to bridge.
	 * <p>
	 * Both ends land one block onto dry ground, so the deck meets the bank instead of stopping short of
	 * it. A run that reaches either edge of the window has no bank inside our reach and is refused —
	 * that is the lake case, and half a bridge into open water is worse than none.
	 */
	public static int[] span(boolean[] water, int centre) {
		if (centre < 0 || centre >= water.length || !water[centre]) {
			return null;
		}
		int a = centre;
		while (a > 0 && water[a - 1]) {
			a--;
		}
		int b = centre;
		while (b < water.length - 1 && water[b + 1]) {
			b++;
		}
		if (a == 0 || b == water.length - 1 || b - a + 1 < MIN_WATER) {
			return null;
		}
		return new int[]{a - 1, b + 1};
	}


	/** Self-check for the span search. Needs assertions on: {@code java -ea BridgeFeature}. */
	public static void main(String[] args) {
		boolean assertions = false;
		assert assertions = true;
		if (!assertions) {
			throw new IllegalStateException("run with -ea or this checks nothing");
		}

		// A stream three wide, dead centre: deck reaches one block onto each bank.
		boolean[] stream = new boolean[9];
		stream[3] = stream[4] = stream[5] = true;
		int[] ends = span(stream, 4);
		assert ends != null && ends[0] == 2 && ends[1] == 6 : "deck must land on both banks";

		// Off-centre origin still finds the same crossing.
		assert java.util.Arrays.equals(span(stream, 3), ends) : "span must not depend on where in the water we start";

		// Dry land: nothing to bridge.
		assert span(new boolean[9], 4) == null : "no water, no bridge";

		// One block of water is a puddle.
		boolean[] puddle = new boolean[9];
		puddle[4] = true;
		assert span(puddle, 4) == null : "a single block of water needs no bridge";

		// Water running off the end of the window is a lake — refuse rather than build half a bridge.
		boolean[] lake = new boolean[9];
		java.util.Arrays.fill(lake, true);
		assert span(lake, 4) == null : "no bank in reach must be refused";
		boolean[] oneBank = new boolean[9];
		for (int i = 2; i < 9; i++) {
			oneBank[i] = true;
		}
		assert span(oneBank, 4) == null : "a bank on only one side is still half a bridge";

		System.out.println("BridgePlan.span: centred, off-centre, dry, puddle, lake and one-bank cases all hold");
	}
}
