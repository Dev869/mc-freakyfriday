package com.unseen.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * An invisible anchor the player rides while hidden.
 * <p>
 * Riding is doing a lot of work here for free: position lock, camera handling, a dismount key the
 * player already knows, and correct behaviour on logout and chunk unload. Writing a bespoke "player
 * is inside a thing" state would have reimplemented all of it, worse.
 */
public class SeatEntity extends Entity {

	public SeatEntity(EntityType<? extends SeatEntity> type, World world) {
		super(type, world);
		this.setNoGravity(true);
		this.setInvisible(true);
		this.setSilent(true);
	}

	@Override
	public void tick() {
		super.tick();
		// The seat exists only to be sat on.
		if (!this.getWorld().isClient && !this.hasPassengers()) {
			this.discard();
		}
	}

	/** Riding a seat is the definition of being hidden — there is no separate flag to keep in sync. */
	public static boolean isHidden(Entity entity) {
		return entity.getVehicle() instanceof SeatEntity;
	}

	public static void release(PlayerEntity player) {
		if (isHidden(player)) {
			player.stopRiding();
		}
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
