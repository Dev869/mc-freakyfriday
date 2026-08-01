package com.unseen;

import com.unseen.entity.StalkerEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Director. Decides when something is out there, and — more importantly — when to take it away.
 * <p>
 * The load-bearing idea is the forced RELEASE: if a PEAK runs longer than
 * {@link Config#peakMaxTicks}, the Stalker is despawned whether or not the player ever saw it. A
 * monster that is always present stops being a monster and becomes scenery, so the Director spends
 * player attention like a budget rather than letting it drain to zero.
 */
public final class TensionManager {
	/** Run at 4 Hz. Nothing here needs to think 20 times a second. */
	public static final int TICK_INTERVAL = 5;

	/** Transient per-player runtime state; deliberately not persisted. */
	private static final Map<UUID, Integer> PHASE_TICKS = new HashMap<>();

	private static int tickCounter;

	private TensionManager() {
	}

	public static void tick(MinecraftServer server) {
		if (++tickCounter % TICK_INTERVAL != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			tickPlayer(player);
			SanityManager.tick(player);
		}
	}

	private static void tickPlayer(ServerPlayerEntity player) {
		Config cfg = Config.get();
		HorrorState state = ModAttachments.get(player);
		ServerWorld world = player.getServerWorld();

		float stress = state.stress() + stressDelta(player, world, cfg);
		int phaseTicks = PHASE_TICKS.merge(player.getUuid(), TICK_INTERVAL, Integer::sum);

		// The pacing decision itself lives in PhaseMachine, free of Minecraft, so it can be checked
		// without launching the game. Everything below is just carrying it out.
		PhaseMachine.Decision decision = PhaseMachine.decide(
				state.phase(),
				stress,
				phaseTicks,
				isThreatening(player, world, cfg),
				!findRealStalkerNear(player, world, 64).isEmpty(),
				cfg);

		if (decision.resetPhaseClock()) {
			resetPhase(player);
		}
		if (decision.despawnStalkers()) {
			despawnStalkersNear(player, world);
		}
		if (decision.wantSpawn()) {
			trySpawnStalker(player, world, cfg);
		}

		ModAttachments.set(player, new HorrorState(
				state.sanity(), HorrorState.clamp(decision.stress()), decision.phase()));

		maybeHallucinate(player, world, cfg, state.sanity());
		maybeOpenPortal(player, world, cfg, decision.stress());
	}

	/** A way in appears when the player is already frightened, never when they are calm. */
	private static void maybeOpenPortal(ServerPlayerEntity player, ServerWorld world, Config cfg, float stress) {
		if (HollowDimension.isHollow(world)
				|| stress < cfg.portalStressThreshold
				|| world.random.nextFloat() >= cfg.portalChance) {
			return;
		}
		com.unseen.portal.HollowPortal.maybePlaceNear(world, player, cfg);
	}

	private static void maybeHallucinate(ServerPlayerEntity player, ServerWorld world, Config cfg, float sanity) {
		if (sanity >= cfg.phantomThreshold || world.random.nextFloat() >= cfg.phantomChance) {
			return;
		}
		long existing = findStalkerNear(player, world, 64).stream()
				.filter(StalkerEntity::isPhantom).count();
		if (existing >= cfg.phantomMaxPerPlayer) {
			return;
		}
		trySpawnPhantom(player, world, cfg);
	}

	/** Stress rises in the dark and near the Stalker, and bleeds away anywhere safe. */
	private static float stressDelta(ServerPlayerEntity player, ServerWorld world, Config cfg) {
		if (!isThreatening(player, world, cfg)) {
			return -cfg.stressDecay;
		}
		float delta = cfg.stressPerTick;
		if (player.hurtTime > 0) {
			delta += cfg.stressPerTick * 2f;
		}
		if (!findStalkerNear(player, world, 32).isEmpty()) {
			delta += cfg.stressPerTick * 1.5f;
		}
		return delta;
	}

	/**
	 * The activation gate. A lit base in daylight is genuinely safe, which is what makes choosing to
	 * go underground mean something.
	 */
	public static boolean isThreatening(ServerPlayerEntity player, ServerWorld world, Config cfg) {
		// The Hollow is never safe. There is no daylight there to escape into, so the light-and-shelter
		// bargain that governs the overworld simply does not apply.
		if (HollowDimension.isHollow(world)) {
			return true;
		}
		BlockPos pos = player.getBlockPos();
		if (pos.getY() < cfg.undergroundY) {
			return true;
		}
		if (world.getLightLevel(pos) <= cfg.darkLightLevel) {
			return true;
		}
		return world.isNight() && world.isSkyVisible(pos);
	}

	private static int resetPhase(ServerPlayerEntity player) {
		PHASE_TICKS.put(player.getUuid(), 0);
		return 0;
	}

	/** Every Stalker, phantoms included — for sanity drain, cleanup and commands. */
	public static List<StalkerEntity> findStalkerNear(ServerPlayerEntity player, ServerWorld world, double radius) {
		return world.getEntitiesByClass(StalkerEntity.class,
				new Box(player.getBlockPos()).expand(radius), e -> true);
	}

	/**
	 * Only the real thing. The Director must never count a hallucination as a live threat, or a single
	 * phantom would suppress the actual hunt for as long as it survived.
	 */
	public static List<StalkerEntity> findRealStalkerNear(ServerPlayerEntity player, ServerWorld world, double radius) {
		return world.getEntitiesByClass(StalkerEntity.class,
				new Box(player.getBlockPos()).expand(radius), e -> !e.isPhantom());
	}

	/** Called when the Stalker lands a hit: crash straight into RELEASE. */
	public static void forceRelease(net.minecraft.entity.player.PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}
		HorrorState state = ModAttachments.get(serverPlayer);
		ModAttachments.set(serverPlayer, new HorrorState(state.sanity(), 0f, Phase.RELEASE));
		PHASE_TICKS.put(serverPlayer.getUuid(), 0);
		despawnStalkersNear(serverPlayer, serverPlayer.getServerWorld());
	}

	public static void setPhase(ServerPlayerEntity player, Phase phase) {
		ModAttachments.set(player, ModAttachments.get(player).withPhase(phase));
		PHASE_TICKS.put(player.getUuid(), 0);
	}

	private static void despawnStalkersNear(ServerPlayerEntity player, ServerWorld world) {
		for (StalkerEntity stalker : findStalkerNear(player, world, 96)) {
			stalker.discard();
		}
	}

	/**
	 * Spawns out of sight, in the dark, at a distance. Returns the entity, or null if nowhere suitable
	 * was found — in which case the Director simply tries again next tick rather than forcing it.
	 */
	public static StalkerEntity trySpawnStalker(ServerPlayerEntity player, ServerWorld world, Config cfg) {
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = world.random.nextDouble() * Math.PI * 2;
			double distance = cfg.spawnMinDistance
					+ world.random.nextDouble() * (cfg.spawnMaxDistance - cfg.spawnMinDistance);
			int x = (int) (player.getX() + Math.cos(angle) * distance);
			int z = (int) (player.getZ() + Math.sin(angle) * distance);
			int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);

			// Prefer a spot near the player's own elevation (caves), not the surface above them.
			int searchY = (int) player.getY();
			BlockPos pos = findStandable(world, x, searchY, z);
			if (pos == null) {
				pos = findStandable(world, x, y, z);
			}
			if (pos == null) {
				continue;
			}
			if (world.getLightLevel(pos) > cfg.darkLightLevel) {
				continue;
			}
			if (isInView(player, Vec3d.ofCenter(pos))) {
				continue;
			}

			StalkerEntity stalker = com.unseen.ModEntities.STALKER.create(world);
			if (stalker == null) {
				return null;
			}
			stalker.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
					world.random.nextFloat() * 360f, 0f);
			stalker.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null);
			world.spawnEntity(stalker);
			UnseenMod.LOGGER.debug("Director spawned a Stalker at {} for {}", pos, player.getName().getString());
			return stalker;
		}
		return null;
	}

	/**
	 * Spawns a hallucination. Unlike a real Stalker this is driven by the player's sanity rather than by
	 * the Director's phase, so a deteriorating player sees things even during a RELEASE — the calm stops
	 * being trustworthy without the Director having to break its own promise.
	 */
	public static StalkerEntity trySpawnPhantom(ServerPlayerEntity player, ServerWorld world, Config cfg) {
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = world.random.nextDouble() * Math.PI * 2;
			double distance = cfg.phantomMinDistance
					+ world.random.nextDouble() * (cfg.phantomMaxDistance - cfg.phantomMinDistance);
			int x = (int) (player.getX() + Math.cos(angle) * distance);
			int z = (int) (player.getZ() + Math.sin(angle) * distance);
			BlockPos pos = findStandable(world, x, (int) player.getY(), z);
			if (pos == null || isInView(player, Vec3d.ofCenter(pos))) {
				continue;
			}
			StalkerEntity phantom = ModEntities.STALKER.create(world);
			if (phantom == null) {
				return null;
			}
			phantom.setPhantom(true);
			phantom.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
					world.random.nextFloat() * 360f, 0f);
			world.spawnEntity(phantom);
			return phantom;
		}
		return null;
	}

	/** Scans up and down from {@code y} for a spot with floor below and two blocks of air. */
	private static BlockPos findStandable(ServerWorld world, int x, int y, int z) {
		for (int dy = 0; dy <= 8; dy++) {
			for (int sign : new int[]{-1, 1}) {
				BlockPos p = new BlockPos(x, y + dy * sign, z);
				if (world.isOutOfHeightLimit(p.getY())) {
					continue;
				}
				if (world.getBlockState(p.down()).isSolidBlock(world, p.down())
						&& world.getBlockState(p).isAir()
						&& world.getBlockState(p.up()).isAir()
						&& world.getBlockState(p.up(2)).isAir()) {
					return p;
				}
			}
		}
		return null;
	}

	/** True if {@code point} is inside the player's forward cone — used to spawn only out of sight. */
	private static boolean isInView(ServerPlayerEntity player, Vec3d point) {
		Vec3d look = player.getRotationVec(1f).normalize();
		Vec3d toPoint = point.subtract(player.getEyePos()).normalize();
		return look.dotProduct(toPoint) > 0.5; // ~60 degrees either side
	}

	public static void onPlayerDisconnect(UUID uuid) {
		PHASE_TICKS.remove(uuid);
	}
}
