package com.unseen;

/**
 * The Director's pacing decision, isolated from Minecraft so it can be reasoned about and checked.
 * <p>
 * Run the self-check with:
 * {@code java -ea -cp build/classes/java/main com.unseen.PhaseMachine}
 */
public final class PhaseMachine {

	/** What the Director decided, and what the caller must now do about it. */
	public record Decision(Phase phase, float stress, boolean resetPhaseClock,
	                       boolean despawnStalkers, boolean wantSpawn) {
	}

	private PhaseMachine() {
	}

	/**
	 * @param phase          current phase
	 * @param stress         stress after this tick's delta, before phase clamping
	 * @param phaseTicks     ticks elapsed in the current phase
	 * @param threatening    is the player somewhere the mod is allowed to escalate
	 * @param stalkerPresent is a Stalker already near the player
	 */
	public static Decision decide(Phase phase, float stress, int phaseTicks,
	                              boolean threatening, boolean stalkerPresent, Config cfg) {
		switch (phase) {
			case BUILD_UP -> {
				if (stress >= cfg.peakThreshold && threatening) {
					return new Decision(Phase.PEAK, stress, true, false, true);
				}
				return new Decision(Phase.BUILD_UP, stress, false, false, false);
			}
			case PEAK -> {
				// Forced release. The point of the entire class: a monster that is always present
				// stops being a monster, so attention is spent as a budget rather than drained.
				if (phaseTicks >= cfg.peakMaxTicks) {
					return new Decision(Phase.RELEASE, 0f, true, true, false);
				}
				if (!threatening) {
					// Player reached safety on their own; let them have it.
					return new Decision(Phase.RELEASE, Math.min(stress, cfg.peakThreshold * 0.5f),
							true, true, false);
				}
				return new Decision(Phase.PEAK, stress, false, false, !stalkerPresent);
			}
			case RELEASE -> {
				float capped = Math.min(stress, cfg.peakThreshold * 0.5f);
				if (phaseTicks >= cfg.releaseTicks) {
					return new Decision(Phase.BUILD_UP, capped, true, false, false);
				}
				return new Decision(Phase.RELEASE, capped, false, false, false);
			}
		}
		return new Decision(Phase.BUILD_UP, stress, false, false, false);
	}

	public static void main(String[] args) {
		boolean assertionsOn = false;
		assert assertionsOn = true;
		if (!assertionsOn) {
			throw new IllegalStateException("run with -ea or this check proves nothing");
		}
		Config cfg = new Config();

		// Below threshold: stays in build-up, spawns nothing.
		Decision d = decide(Phase.BUILD_UP, cfg.peakThreshold - 1, 0, true, false, cfg);
		assert d.phase() == Phase.BUILD_UP : "under threshold must not peak";
		assert !d.wantSpawn() : "under threshold must not spawn";

		// At threshold in a threatening place: escalate and ask for a spawn.
		d = decide(Phase.BUILD_UP, cfg.peakThreshold, 0, true, false, cfg);
		assert d.phase() == Phase.PEAK : "at threshold must peak";
		assert d.wantSpawn() && d.resetPhaseClock();

		// At threshold somewhere safe: the activation gate holds it back.
		d = decide(Phase.BUILD_UP, cfg.peakThreshold + 50, 0, false, false, cfg);
		assert d.phase() == Phase.BUILD_UP : "safe places must never escalate";

		// Peak, still fresh, stalker already out there: no duplicate spawn.
		d = decide(Phase.PEAK, 100f, 10, true, true, cfg);
		assert d.phase() == Phase.PEAK && !d.wantSpawn() : "must not double-spawn";

		// Peak, stalker died or despawned: ask for another.
		d = decide(Phase.PEAK, 100f, 10, true, false, cfg);
		assert d.wantSpawn() : "peak with no stalker must respawn one";

		// THE load-bearing rule: peak cannot outlast its ceiling, even at maximum stress.
		d = decide(Phase.PEAK, 100f, cfg.peakMaxTicks, true, true, cfg);
		assert d.phase() == Phase.RELEASE : "peak must be force-released at the ceiling";
		assert d.despawnStalkers() : "forced release must remove the stalker";
		assert d.stress() == 0f : "forced release must floor stress";

		// Reaching light/safety during peak also releases.
		d = decide(Phase.PEAK, 100f, 10, false, true, cfg);
		assert d.phase() == Phase.RELEASE && d.despawnStalkers();

		// Release caps stress so the next build-up starts from calm, and never spawns.
		d = decide(Phase.RELEASE, 100f, 0, true, false, cfg);
		assert d.phase() == Phase.RELEASE && !d.wantSpawn();
		assert d.stress() <= cfg.peakThreshold * 0.5f : "release must cap stress";

		// Release expires back into build-up.
		d = decide(Phase.RELEASE, 100f, cfg.releaseTicks, true, false, cfg);
		assert d.phase() == Phase.BUILD_UP : "release must expire";

		// A player who never leaves the dark still gets peaks and troughs, never a constant peak.
		Phase p = Phase.BUILD_UP;
		int clock = 0;
		int peaks = 0, releases = 0;
		float stress = 0f;
		for (int i = 0; i < 20000; i += TensionManager.TICK_INTERVAL) {
			stress = Math.min(100f, stress + cfg.stressPerTick);
			Decision step = decide(p, stress, clock, true, true, cfg);
			clock = step.resetPhaseClock() ? 0 : clock + TensionManager.TICK_INTERVAL;
			if (step.phase() != p) {
				if (step.phase() == Phase.PEAK) peaks++;
				if (step.phase() == Phase.RELEASE) releases++;
			}
			p = step.phase();
			stress = step.stress();
		}
		assert peaks >= 2 : "sustained darkness should produce repeated peaks, got " + peaks;
		assert releases >= 2 : "sustained darkness must still produce releases, got " + releases;

		System.out.println("PhaseMachine self-check passed ("
				+ peaks + " peaks, " + releases + " releases over 20k ticks in permanent darkness)");
	}
}
