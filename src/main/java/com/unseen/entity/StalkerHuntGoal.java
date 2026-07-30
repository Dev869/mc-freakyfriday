package com.unseen.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Moves toward the last heard position, searches when it arrives, gives up when the trail goes cold.
 * <p>
 * Deliberately not a "run at the player" goal: it only ever knows where a <em>noise</em> was. A player
 * who stops making noise and keeps a wall between themselves and it is genuinely lost — that is the
 * whole hiding mechanic, implemented with vanilla line-of-sight rather than custom hiding blocks.
 */
public class StalkerHuntGoal extends Goal {
	private static final double CONTACT_DISTANCE_SQ = 2.5 * 2.5;

	private final StalkerEntity stalker;
	private BlockPos target;
	private int searchTicks;
	private int repathCooldown;

	public StalkerHuntGoal(StalkerEntity stalker) {
		this.stalker = stalker;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		return !this.stalker.isWithdrawing() && this.stalker.getLastHeardPos() != null;
	}

	@Override
	public boolean shouldContinue() {
		if (this.stalker.isWithdrawing() || this.target == null) {
			return false;
		}
		// Keep searching around the last noise for a while before losing interest.
		return this.searchTicks < 200;
	}

	@Override
	public void start() {
		this.target = this.stalker.getLastHeardPos();
		this.searchTicks = 0;
		this.repathCooldown = 0;
	}

	@Override
	public void stop() {
		this.target = null;
		this.stalker.getNavigation().stop();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		BlockPos heard = this.stalker.getLastHeardPos();
		if (heard != null && !heard.equals(this.target)) {
			// A fresher noise always wins, and resets the patience clock.
			this.target = heard;
			this.searchTicks = 0;
			this.repathCooldown = 0;
		}
		if (this.target == null) {
			return;
		}

		Vec3d centre = Vec3d.ofCenter(this.target);
		this.stalker.getLookControl().lookAt(centre.x, centre.y, centre.z);

		if (--this.repathCooldown <= 0) {
			this.repathCooldown = 10;
			this.stalker.getNavigation().startMovingTo(centre.x, centre.y, centre.z, 1.0);
		}

		if (this.stalker.squaredDistanceTo(centre) < 4.0) {
			// Arrived at the noise. Look around; the trail is now going cold.
			this.searchTicks++;
		}

		PlayerEntity nearest = this.stalker.getWorld()
				.getClosestPlayer(this.stalker, 3.0);
		if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()
				&& !com.unseen.Hiding.isHidden(nearest)
				&& this.stalker.squaredDistanceTo(nearest) < CONTACT_DISTANCE_SQ) {
			this.stalker.onReachPlayer(nearest);
		}
	}
}
