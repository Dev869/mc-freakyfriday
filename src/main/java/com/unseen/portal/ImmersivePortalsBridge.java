package com.unseen.portal;

import com.unseen.UnseenMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Decides whether Immersive Portals is present, and hands off to it if so.
 * <p>
 * This class deliberately names no Immersive Portals type. The JVM verifier resolves the types a class
 * references when it links that class, so a guard sitting alongside the actual calls would throw
 * {@link NoClassDefFoundError} the moment it was reached on an install without the mod — the guard
 * would run too late to protect anything. All of that lives in {@link ImmersivePortalsLink}, which is
 * only referenced from inside the branch below.
 */
public final class ImmersivePortalsBridge {
	public static final String MOD_ID = "immersive_portals";

	private static Boolean present;

	private ImmersivePortalsBridge() {
	}

	public static boolean available() {
		if (present == null) {
			present = FabricLoader.getInstance().isModLoaded(MOD_ID);
			UnseenMod.LOGGER.info(present
					? "Immersive Portals found: Hollow portals will be see-through."
					: "Immersive Portals absent: Hollow portals will use the built-in teleport.");
		}
		return present;
	}

	/**
	 * Spawns a see-through portal over an existing frame, if Immersive Portals is installed.
	 *
	 * @param base bottom-centre interior block of the frame
	 * @return true only when Immersive Portals is now carrying travel through this frame. Callers use
	 *         this to decide whether to stand their own teleport down, so a false here — mod absent, or
	 *         the call threw — has to leave the native path running.
	 */
	public static boolean createSeamless(ServerWorld world, BlockPos base, Direction.Axis axis,
	                                     int width, int height) {
		if (!available()) {
			return false;
		}
		try {
			return ImmersivePortalsLink.link(world, base, axis, width, height);
		} catch (Throwable t) {
			// Never let another mod's API break portal creation: the native teleport still works.
			UnseenMod.LOGGER.warn("Immersive Portals link failed, falling back to plain teleport: {}",
					t.toString());
			return false;
		}
	}
}
