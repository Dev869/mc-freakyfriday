package com.unseen.portal;

import com.unseen.HollowDimension;
import com.unseen.UnseenMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;

/**
 * Every Immersive Portals call lives here, and nowhere else.
 * <p>
 * This separation is load-bearing, not tidiness. The JVM verifier resolves the types referenced by a
 * class's method bodies when that class is linked, so keeping the {@code isModLoaded} guard in the same
 * class as these calls would make the guard useless — merely reaching the check would try to load
 * {@code Portal} and throw {@link NoClassDefFoundError} for everyone without the mod installed. The
 * guard lives in {@link ImmersivePortalsBridge}, which names no Immersive Portals type at all; this
 * class is only ever touched once that guard has passed.
 */
final class ImmersivePortalsLink {

	private ImmersivePortalsLink() {
	}

	/**
	 * @return true when a seamless portal covers this frame — whether we just made it or found it
	 */
	static boolean link(ServerWorld world, BlockPos base, Direction.Axis axis) {
		RegistryKey<World> destination = HollowDimension.isHollow(world)
				? World.OVERWORLD
				: HollowDimension.WORLD;

		Direction across = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Vec3d centre = Vec3d.ofCenter(base)
				.add(across.getOffsetX() * 0.5, HollowPortal.HEIGHT / 2.0 - 0.5, across.getOffsetZ() * 0.5);

		// Ask the world, not a set we keep in memory. Portal entities are saved with the chunk, so after
		// a restart the world is the only thing that still knows; a process-local record would say no and
		// we would stack a second portal on top of the first.
		if (!world.getEntitiesByClass(Portal.class, Box.of(centre, 1.0, 1.0, 1.0), p -> true).isEmpty()) {
			return true;
		}

		Portal portal = Portal.ENTITY_TYPE.create(world);
		if (portal == null) {
			return false;
		}

		Vec3d widthAxis = new Vec3d(across.getOffsetX(), 0, across.getOffsetZ());
		Vec3d heightAxis = new Vec3d(0, 1, 0);

		portal.setOriginPos(centre);
		portal.setDestinationDimension(destination);
		// One-to-one coordinates, matching the native teleport, so the worlds stay superimposed.
		portal.setDestination(centre);
		portal.setOrientationAndSize(widthAxis, heightAxis, HollowPortal.WIDTH, HollowPortal.HEIGHT);
		portal.setTeleportable(true);
		portal.setInteractable(true);

		PortalAPI.spawnServerEntity(portal);
		// The reverse side, so walking back through is equally seamless.
		PortalManipulation.completeBiWayPortal(portal, Portal.ENTITY_TYPE);

		UnseenMod.LOGGER.debug("Seamless portal linked at {} -> {}", centre, destination.getValue());
		return true;
	}
}
