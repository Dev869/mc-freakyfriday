package com.unseen;

import com.unseen.entity.SeatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Getting inside something so the Stalker loses you.
 * <p>
 * There is no invulnerability here and no timer that saves you. Hiding works only because the Stalker
 * hunts noise and line of sight: inside a wardrobe or under a bed you emit no vibrations it will
 * accept, and it cannot reach you. Step out while it is still searching and you are exactly as exposed
 * as you were. That asymmetry — safe but blind, and having to choose when to leave — is the mechanic.
 */
public final class Hiding {

	private Hiding() {
	}

	public static boolean isHidden(PlayerEntity player) {
		return SeatEntity.isHidden(player);
	}

	/**
	 * Puts the player inside the block at {@code pos}.
	 *
	 * @param yOffset height within the block: roughly 0.2 for a wardrobe, lower for under a bed
	 */
	public static ActionResult enter(World world, BlockPos pos, PlayerEntity player, double yOffset) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (isHidden(player)) {
			SeatEntity.release(player);
			return ActionResult.SUCCESS;
		}
		SeatEntity seat = ModEntities.SEAT.create(world);
		if (seat == null) {
			return ActionResult.PASS;
		}
		Vec3d centre = Vec3d.ofBottomCenter(pos);
		seat.refreshPositionAndAngles(centre.x, centre.y + yOffset, centre.z, player.getYaw(), 0f);
		world.spawnEntity(seat);
		player.startRiding(seat, true);
		return ActionResult.SUCCESS;
	}

	/**
	 * Sneak + use on a bed hides under it. Plain use is left alone so sleeping still works — a hiding
	 * mechanic that broke beds would cost more than it added.
	 */
	public static ActionResult tryBed(World world, BlockPos pos, PlayerEntity player) {
		if (!player.isSneaking()) {
			return ActionResult.PASS;
		}
		if (!world.getBlockState(pos).isIn(BlockTags.BEDS)) {
			return ActionResult.PASS;
		}
		return enter(world, pos, player, 0.05);
	}
}
