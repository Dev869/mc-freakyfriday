package com.unseen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every number that decides how frightening this mod is lives here, because none of them are
 * knowable in advance. Tuning should be a file edit and a world reload, never a rebuild.
 */
public final class Config {
	private static Config INSTANCE = new Config();

	// Built on demand rather than in a static field, so simply constructing a Config (as the
	// PhaseMachine self-check does) needs nothing on the classpath but the JDK.
	private static Gson gson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}

	// --- Activation gate: the mod only escalates in the dark, underground, or at night. ---
	/** At or below this light level a position counts as threatening. */
	public int darkLightLevel = 6;
	/** Below this Y, count as underground regardless of light. */
	public int undergroundY = 50;

	// --- Director AI (TensionManager) ---
	/** Stress gained per tick-group while in a threatening place. */
	public float stressPerTick = 0.35f;
	/** Stress lost per tick-group while safe. */
	public float stressDecay = 0.8f;
	/** Stress at which BUILD_UP escalates to PEAK. */
	public float peakThreshold = 70f;
	/**
	 * Hard ceiling on a PEAK phase, in ticks. Exceeding it forces RELEASE regardless of stress.
	 * This is the anti-desensitisation mechanic and the single most important knob in the mod:
	 * too high and players acclimatise to the Stalker, too low and it never gets to be scary.
	 */
	public int peakMaxTicks = 1200;
	/** How long RELEASE lasts (false sense of security) before BUILD_UP may resume. */
	public int releaseTicks = 1800;

	// --- Stalker ---
	/** Vibration hearing radius, in blocks. Feeds the vanilla game-event graph. */
	public int hearingRadius = 30;
	/** Min/max distance from the player the Director may spawn a Stalker. */
	public int spawnMinDistance = 18;
	public int spawnMaxDistance = 34;
	/** Damage dealt when the Stalker reaches you. Survivable, but a real failure. */
	public float contactDamage = 12f;
	/** Ticks of blindness applied on contact. */
	public int contactBlindnessTicks = 200;
	/** Sanity lost on contact. */
	public float contactSanityLoss = 45f;
	/**
	 * How long the Stalker hauls you to its lair before it finishes the job. Long enough that the
	 * journey is the point and you spend it watching; short enough that it is not a punishment.
	 */
	public int dragTicks = 400;

	// --- Sanity ---
	/** Sanity drained per tick-group while the Stalker is inside your view cone. */
	public float sanityDrainLooking = 2.5f;
	/** Half-angle of the punished view cone, in degrees. */
	public float lookConeDegrees = 30f;
	/** How far away a Stalker can still cost you sanity by being looked at. */
	public int lookRange = 48;
	/** Sanity drained per tick-group in the dark. */
	public float sanityDrainDark = 0.08f;
	/** Sanity recovered per tick-group when lit, alone and undamaged. */
	public float sanityRecovery = 0.35f;
	/** Light level at or above which sanity recovers. */
	public int safeLightLevel = 8;
	/** No Stalker may be within this radius for sanity to recover. */
	public int safeRadius = 24;
	/** Below this sanity, nausea sets in. */
	public float nauseaThreshold = 30f;
	/** Below this sanity, the world starts going dark. */
	public float darknessThreshold = 15f;

	// --- Stare punishment: the Stalker refuses to be studied. ---
	/**
	 * Tick-groups of continuous staring before the Stalker simply is not there any more. This is what
	 * keeps it a glimpse rather than a model the player can stand and examine.
	 */
	public int stareTicksBeforeVanish = 12;
	/** Sanity torn out when it vanishes from under your gaze. */
	public float stareVanishSanityLoss = 20f;

	// --- Heartbeat (client) ---
	/** Distance at which the heartbeat becomes audible at all. */
	public double heartbeatRange = 24.0;

	// --- Scenery corruption (client, fully reversible) ---
	/** Below this sanity the world begins to go wrong. */
	public float sceneryThreshold = 40f;
	/** Radius around the player in which blocks may be corrupted. */
	public int sceneryRadius = 12;
	/** Tick-groups between individual corruptions. Sparse on purpose. */
	public int sceneryIntervalTicks = 20;
	/** Hard cap on simultaneously corrupted blocks. */
	public int sceneryMaxBlocks = 24;

	// --- Dread post-process shader (client) ---
	/** Below this sanity the screen effect fades in, reaching full strength at zero. */
	public float shaderThreshold = 55f;

	// --- Hallucinations ---
	/** Below this sanity the player starts seeing things that are not there. */
	public float phantomThreshold = 45f;
	/** Chance per tick-group of a phantom appearing while below the threshold. */
	public float phantomChance = 0.015f;
	/** Never more than this many phantoms around one player at once. */
	public int phantomMaxPerPlayer = 1;
	/** Ticks a phantom persists if the player never approaches it. */
	public int phantomLifetimeTicks = 400;
	/** Get this close and it is simply gone, leaving you unable to confirm anything. */
	public double phantomVanishDistance = 9.0;
	/** Spawn band for phantoms — closer than the real thing, at the edge of vision. */
	public int phantomMinDistance = 12;
	public int phantomMaxDistance = 26;

	// --- Portals into the Hollow ---
	/** Chance per tick-group that a way in opens near a player who is already under pressure. */
	public float portalChance = 0.004f;
	/** Stress at or above which portals may appear at all. */
	public float portalStressThreshold = 45f;

	// --- Spawn suppression (replaces In Control!, which has no Fabric build) ---
	public boolean suppressVanillaHostiles = true;
	/**
	 * Empties the dark. Bats, glow squid and axolotls stop spawning underground, and the Hollow stops
	 * spawning anything at all, so the first thing that moves once you leave the sunlight is ours.
	 */
	public boolean suppressUndergroundLife = true;

	public static Config get() {
		return INSTANCE;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("unseen.json");
	}

	/** Loads config, writing defaults if absent. Never throws; a broken config falls back to defaults. */
	public static void load() {
		Path p = path();
		try {
			if (Files.exists(p)) {
				Config loaded = gson().fromJson(Files.readString(p), Config.class);
				if (loaded != null) {
					INSTANCE = loaded;
					UnseenMod.LOGGER.info("Loaded config from {}", p);
					return;
				}
			}
			Files.createDirectories(p.getParent());
			Files.writeString(p, gson().toJson(INSTANCE));
			UnseenMod.LOGGER.info("Wrote default config to {}", p);
		} catch (IOException | RuntimeException e) {
			UnseenMod.LOGGER.warn("Config unreadable, using defaults: {}", e.toString());
			INSTANCE = new Config();
		}
	}
}
