package com.unseen.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/**
 * Something wearing a player's skin.
 * <p>
 * It looks exactly like the person it killed, down to their skin and name tag, which is the entire
 * point: in multiplayer the horror is not that a monster is chasing you, it is that you cannot tell it
 * from your friend until you are close enough for the twitching to be obvious. It bleeds constantly
 * because the skin does not fit, and it never despawns — reclaiming a face means hunting it down.
 */
public class ImpersonatorEntity extends HostileEntity {
	private static final TrackedData<Optional<UUID>> VICTIM_ID =
			DataTracker.registerData(ImpersonatorEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<String> VICTIM_NAME =
			DataTracker.registerData(ImpersonatorEntity.class, TrackedDataHandlerRegistry.STRING);

	private static final DustParticleEffect BLOOD =
			new DustParticleEffect(new org.joml.Vector3f(0.34f, 0.03f, 0.03f), 1.0f);

	/** Counts down to the next involuntary jerk. The stolen body does not sit still properly. */
	private int twitchTicks;

	public ImpersonatorEntity(EntityType<? extends ImpersonatorEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createImpersonatorAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
				// Slightly faster than a walking player: it should close distance if you hesitate.
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.29)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VICTIM_ID, Optional.empty());
		builder.add(VICTIM_NAME, "");
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.7));
		// It watches you from further away than it attacks from.
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 24f));
		this.goalSelector.add(8, new LookAroundGoal(this));
		this.targetSelector.add(1, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
				this, PlayerEntity.class, true));

		if (this.getNavigation() instanceof MobNavigation navigation) {
			navigation.setCanPathThroughDoors(true);
			navigation.setCanEnterOpenDoors(true);
		}
	}

	public void setVictim(UUID id, String name) {
		this.dataTracker.set(VICTIM_ID, Optional.ofNullable(id));
		boolean named = name != null && !name.isEmpty();
		this.dataTracker.set(VICTIM_NAME, named ? name : "");
		// No name, no nameplate. Loading one saved without a victim used to give it an empty custom
		// name and then turn the label on, which renders as a blank tag hanging in the air.
		this.setCustomName(named ? Text.literal(name) : null);
		this.setCustomNameVisible(named);
	}

	public Optional<UUID> getVictimId() {
		return this.dataTracker.get(VICTIM_ID);
	}

	public String getVictimName() {
		return this.dataTracker.get(VICTIM_NAME);
	}

	/** Never despawns. The face stays stolen until someone takes it back. */
	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		// A slow, constant leak from a skin that does not fit.
		if (this.age % 12 == 0) {
			world.spawnParticles(BLOOD, this.getX(), this.getY() + 1.1, this.getZ(),
					1, 0.16, 0.25, 0.16, 0.0);
		}
		// Involuntary jerks: the give-away, if you are close enough to notice.
		if (--this.twitchTicks <= 0) {
			this.twitchTicks = 30 + this.random.nextInt(70);
			float jerk = (this.random.nextFloat() - 0.5f) * 90f;
			this.setHeadYaw(this.getHeadYaw() + jerk);
			this.setPitch(Math.max(-60f, Math.min(60f, this.getPitch() + jerk * 0.4f)));
		}
	}

	@Override
	public void onDeath(net.minecraft.entity.damage.DamageSource source) {
		super.onDeath(source);
		if (this.getWorld() instanceof ServerWorld world) {
			// It comes apart when it dies, and gives the face back.
			world.spawnParticles(BLOOD, this.getX(), this.getY() + 1.0, this.getZ(),
					60, 0.4, 0.6, 0.4, 0.1);
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		this.getVictimId().ifPresent(id -> nbt.putUuid("VictimId", id));
		nbt.putString("VictimName", this.getVictimName());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		UUID id = nbt.containsUuid("VictimId") ? nbt.getUuid("VictimId") : null;
		this.setVictim(id, nbt.getString("VictimName"));
	}
}
