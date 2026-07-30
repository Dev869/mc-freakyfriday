package com.unseen;

import com.unseen.entity.StalkerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sanity, and the rule that makes the Stalker frightening: looking at it costs you.
 * <p>
 * The punishment is applied as ordinary server-side status effects rather than client render hacks,
 * so it syncs itself and works identically in multiplayer.
 */
public final class SanityManager {
	/** Consecutive tick-groups each player has held the Stalker in view. Transient. */
	private static final Map<UUID, Integer> STARE_TICKS = new HashMap<>();


	private SanityManager() {
	}

	public static void onPlayerDisconnect(UUID uuid) {
		STARE_TICKS.remove(uuid);
	}

	static void tick(ServerPlayerEntity player) {
		Config cfg = Config.get();
		ServerWorld world = player.getServerWorld();

		float delta = sanityDelta(player, world, cfg);

		// Re-read rather than reusing a pre-delta snapshot: the stare-vanish path calls
		// TensionManager.forceRelease, which writes a new phase. Writing back a stale copy here would
		// silently clobber that release.
		HorrorState state = ModAttachments.get(player);
		float sanity = HorrorState.clamp(state.sanity() + delta);

		applyEffects(player, sanity, cfg);
		ModAttachments.set(player, state.withSanity(sanity));
	}

	static float sanityDelta(ServerPlayerEntity player, ServerWorld world, Config cfg) {
		float delta = 0f;
		boolean stalkerNearby = false;

		boolean staringAtSomething = false;

		// Scanned out to the look range, not the safe radius: a glimpse at 35 blocks is the most
		// likely way you will ever see this thing, and it has to cost you.
		for (StalkerEntity stalker : TensionManager.findStalkerNear(player, world, cfg.lookRange)) {
			double distSq = player.squaredDistanceTo(stalker);
			if (distSq <= (double) cfg.safeRadius * cfg.safeRadius) {
				stalkerNearby = true;
			}
			if (isLookingAt(player, stalker, cfg.lookConeDegrees) && player.canSee(stalker)) {
				delta -= cfg.sanityDrainLooking;
				staringAtSomething = true;

				// Refuses to be studied. Hold your gaze too long and it is simply gone — which costs
				// you badly, and leaves you with no idea where it went.
				int stare = STARE_TICKS.merge(player.getUuid(), 1, Integer::sum);
				if (stare >= cfg.stareTicksBeforeVanish) {
					boolean wasReal = !stalker.isPhantom();
					stalker.discard();
					delta -= cfg.stareVanishSanityLoss;
					STARE_TICKS.remove(player.getUuid());
					// Only a real hunt earns the mercy of a release. Staring down a hallucination buys
					// you nothing, and the player has no way to know which one they just lost.
					if (wasReal) {
						TensionManager.forceRelease(player);
					}
				}
			}
		}
		if (!staringAtSomething) {
			STARE_TICKS.remove(player.getUuid());
		}

		if (world.getLightLevel(player.getBlockPos()) <= cfg.darkLightLevel) {
			delta -= cfg.sanityDrainDark;
		}

		boolean safe = !stalkerNearby
				&& world.getLightLevel(player.getBlockPos()) >= cfg.safeLightLevel
				&& player.hurtTime == 0;
		if (safe) {
			delta += cfg.sanityRecovery;
		}
		return delta;
	}

	/**
	 * True when {@code target} sits inside the player's forward cone. Combined with a line-of-sight
	 * check by the caller, so a wall between you and it protects you.
	 */
	public static boolean isLookingAt(ServerPlayerEntity player, StalkerEntity target, float coneDegrees) {
		Vec3d look = player.getRotationVec(1f).normalize();
		Vec3d toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePos());
		if (toTarget.lengthSquared() < 1.0e-4) {
			return true;
		}
		double dot = look.dotProduct(toTarget.normalize());
		return dot > Math.cos(Math.toRadians(coneDegrees));
	}

	private static void applyEffects(ServerPlayerEntity player, float sanity, Config cfg) {
		// Duration slightly exceeds the tick interval so the effect is continuous but stops promptly
		// once sanity recovers.
		int duration = TensionManager.TICK_INTERVAL * 4;
		if (sanity < cfg.nauseaThreshold) {
			player.addStatusEffect(new StatusEffectInstance(
					StatusEffects.NAUSEA, duration, 0, true, false, false));
		}
		if (sanity < cfg.darknessThreshold) {
			player.addStatusEffect(new StatusEffectInstance(
					StatusEffects.DARKNESS, duration, 0, true, false, false));
		}
	}
}
