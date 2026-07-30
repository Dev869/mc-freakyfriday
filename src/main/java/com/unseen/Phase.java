package com.unseen;

/**
 * Director pacing phase. Deliberately free of any Minecraft import so the decision logic in
 * {@link PhaseMachine} can be exercised without a game running.
 */
public enum Phase {
	/** Pressure accumulating. Nothing is hunting you yet. */
	BUILD_UP,
	/** Something is out there. Fog closes in. */
	PEAK,
	/** Forced calm. The Stalker is gone and you are allowed to believe you are safe. */
	RELEASE;

	public static Phase byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return BUILD_UP;
		}
	}
}
