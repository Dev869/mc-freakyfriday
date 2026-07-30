package com.unseen.client;

import com.unseen.Config;
import com.unseen.HorrorState;
import com.unseen.ModSounds;
import com.unseen.Phase;
import com.unseen.entity.StalkerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.Box;

/**
 * The player's heartbeat, rising as the Stalker closes.
 * <p>
 * This is the mechanic that lets the player feel the threat without seeing it, which matters because
 * looking at the Stalker is punished — most of the time the heartbeat is the <em>only</em> information
 * they get. It intentionally reports distance and nothing else: no direction, no confirmation, just the
 * knowledge that it is nearer than it was.
 * <p>
 * Deliberately non-positional ({@code master}), unlike every Stalker sound: this one is inside the
 * player's head, not in the world, so it must not be occluded by the wall they are hiding behind.
 */
public final class HeartbeatManager {
	private static int cooldown;
	private static int droneCooldown;

	private HeartbeatManager() {
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null || client.isPaused()) {
			return;
		}
		tickDrone(client);
		Config cfg = Config.get();
		HorrorState state = UnseenClient.state();

		double nearest = nearestStalkerDistance(client, cfg.heartbeatRange);
		// Sanity alone can drive it too, so the player's own deterioration is audible even when
		// nothing is nearby.
		float fear = Math.max(proximityFear(nearest, cfg), sanityFear(state, cfg));

		if (fear <= 0.01f) {
			cooldown = 0;
			return;
		}
		if (--cooldown > 0) {
			return;
		}

		// 30 ticks between beats when barely afraid, 7 when the thing is on top of you.
		cooldown = Math.round(30f - 23f * fear);
		float pitch = 0.85f + 0.35f * fear;
		float volume = 0.25f + 0.75f * fear;
		client.getSoundManager().play(
				PositionedSoundInstance.master(ModSounds.HEARTBEAT, pitch, volume));
	}

	/**
	 * A low drone while the Director holds a PEAK, so the phase is felt rather than announced.
	 * <p>
	 * Re-triggered on a long interval rather than held as a looping instance: the samples are long
	 * enough to read as continuous, and a one-shot cannot leak a stuck loop if the phase changes during
	 * a world transition.
	 */
	private static void tickDrone(MinecraftClient client) {
		if (UnseenClient.state().phase() != Phase.PEAK) {
			droneCooldown = 0;
			return;
		}
		if (--droneCooldown > 0) {
			return;
		}
		droneCooldown = 220;
		client.getSoundManager().play(
				PositionedSoundInstance.master(ModSounds.DREAD_DRONE, 1.0f, 0.7f));
	}

	private static float proximityFear(double distance, Config cfg) {
		if (distance >= cfg.heartbeatRange) {
			return 0f;
		}
		return (float) (1.0 - distance / cfg.heartbeatRange);
	}

	private static float sanityFear(HorrorState state, Config cfg) {
		if (state.sanity() >= cfg.nauseaThreshold) {
			return 0f;
		}
		return Math.min(1f, (cfg.nauseaThreshold - state.sanity()) / cfg.nauseaThreshold);
	}

	private static double nearestStalkerDistance(MinecraftClient client, double range) {
		double best = Double.MAX_VALUE;
		Box box = client.player.getBoundingBox().expand(range);
		for (StalkerEntity stalker : client.world.getEntitiesByClass(StalkerEntity.class, box, e -> true)) {
			best = Math.min(best, client.player.distanceTo(stalker));
		}
		return best;
	}
}
