package com.unseen.entity;

import com.unseen.Config;
import com.unseen.HorrorState;
import com.unseen.ModAttachments;
import com.unseen.ModSounds;
import com.unseen.TensionManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.event.EntityPositionSource;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import net.minecraft.world.event.Vibrations;
import net.minecraft.world.event.listener.EntityGameEventHandler;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.BiConsumer;

/**
 * Hunts by sound, punishes being looked at, and cannot be fought.
 * <p>
 * Hearing is not implemented here: the entity plugs into the same vanilla vibration graph the Warden
 * uses, so block-breaking, sprinting, chest-opening and ~40 other signals arrive already occluded by
 * terrain. This class only decides what to do about a position it heard.
 */
public class StalkerEntity extends HostileEntity implements GeoEntity, Vibrations {
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation STALK = RawAnimation.begin().thenLoop("stalk");
	private static final RawAnimation TWITCH = RawAnimation.begin().thenPlay("twitch");

	/** Dark arterial red. Dust particles take an arbitrary colour, so no new particle type is needed. */
	private static final DustParticleEffect BLOOD =
			new DustParticleEffect(new org.joml.Vector3f(0.34f, 0.03f, 0.03f), 1.1f);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final Vibrations.Callback vibrationCallback = new HearingCallback();
	private final Vibrations.ListenerData listenerData = new Vibrations.ListenerData();
	private final EntityGameEventHandler<Vibrations.VibrationListener> gameEventHandler =
			new EntityGameEventHandler<>(new Vibrations.VibrationListener(this));

	/** Last position this entity heard something at. Null means it has nothing to go on. */
	private BlockPos lastHeardPos;
	/** Ticks remaining before it loses interest in {@link #lastHeardPos}. */
	private int interestTicks;
	/** Set when it lands a hit, so it withdraws instead of chain-hitting. */
	private int withdrawTicks;

	/**
	 * A hallucination: identical to look at, deaf, harmless, and gone if you approach it.
	 * <p>
	 * This exists to poison the player's evidence. Once a phantom has vanished on them once, every
	 * subsequent sighting is ambiguous — they cannot tell whether the thing at the end of the corridor
	 * is real without walking toward it, which is exactly the decision this mod wants them making.
	 */
	private boolean phantom;
	private int phantomTicks;

	/**
	 * How far it will look for a real lair before settling for the spot it spawned in. Generous: a lair
	 * two hundred blocks away is still a better destination than an anonymous patch of dark.
	 */
	private static final double LAIR_SEARCH_RADIUS = 256.0;

	/** Where it takes people. Decided when it takes hold of someone, not before. */
	private BlockPos lair;
	/** Counts down while it is hauling a victim. Zero means it is not dragging anyone. */
	private int dragTicks;

	public StalkerEntity(EntityType<? extends StalkerEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createStalkerAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new StalkerHuntGoal(this));
		// Doors do not stop it, and it leaves them standing open. The open door you did not open is a
		// better horror beat than any sighting: it is proof, it is behind you, and it is silent.
		// Pass true here instead if you would rather it closed them behind itself.
		this.goalSelector.add(2, new LongDoorInteractGoal(this, false));
		this.goalSelector.add(8, new LookAroundGoal(this));

		if (this.getNavigation() instanceof MobNavigation navigation) {
			navigation.setCanPathThroughDoors(true);
			navigation.setCanEnterOpenDoors(true);
		}
	}

	// --- Unkillable. Weapons are not an answer to this. ---

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Still allow out-of-world removal (/kill, void) so it can never become unremovable.
		if (source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)
				|| source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
			return super.damage(source, amount);
		}
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	// --- Audio. All positional (Entity#playSound), never global, so Sound Physics Remastered can
	// occlude and reverberate it: footsteps genuinely echo down a tunnel. ---

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.STALKER_BREATH;
	}

	@Override
	public int getMinAmbientSoundDelay() {
		return 120;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(ModSounds.STALKER_STEP, 0.35f, 0.7f + this.random.nextFloat() * 0.2f);
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	// --- Hearing ---

	@Override
	public Vibrations.ListenerData getVibrationListenerData() {
		return this.listenerData;
	}

	@Override
	public Vibrations.Callback getVibrationCallback() {
		return this.vibrationCallback;
	}

	@Override
	public void updateEventHandler(BiConsumer<EntityGameEventHandler<?>, ServerWorld> callback) {
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			callback.accept(this.gameEventHandler, serverWorld);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		if (this.phantom) {
			this.tickPhantom(serverWorld);
			return;
		}
		// Where it first appeared, remembered on the first tick. chooseLair falls back to this, and
		// reading getBlockPos() at grab time instead would answer "wherever the victim is standing",
		// which ends the drag the tick it starts.
		if (this.lair == null) {
			this.lair = this.getBlockPos();
		}
		if (this.dragTicks > 0) {
			this.tickDrag(serverWorld);
			this.bleed(serverWorld);
			return;
		}
		Vibrations.Ticker.tick(serverWorld, this.listenerData, this.vibrationCallback);
		if (this.interestTicks > 0 && --this.interestTicks == 0) {
			this.lastHeardPos = null;
		}
		if (this.withdrawTicks > 0) {
			this.withdrawTicks--;
		}
		this.bleed(serverWorld);
	}

	/**
	 * It leaks constantly. Two purposes beyond looking wrong: a trail the player can read after it has
	 * gone, and a slow drip that gives away that something is standing still nearby.
	 */
	private void bleed(ServerWorld world) {
		boolean moving = this.getVelocity().horizontalLengthSquared() > 1.0e-4;
		int interval = moving ? 3 : 14;
		if (this.age % interval != 0) {
			return;
		}
		// From the open ribcage rather than the feet, so the trail sits where the wound is.
		world.spawnParticles(BLOOD,
				this.getX(), this.getY() + 1.3, this.getZ(),
				moving ? 2 : 1, 0.18, 0.12, 0.18, 0.0);
		if (this.age % 40 == 0) {
			world.spawnParticles(BLOOD, this.getX(), this.getY() + 0.1, this.getZ(),
					3, 0.22, 0.02, 0.22, 0.0);
		}
	}

	/**
	 * A phantom does not hunt. It stands where it was put, twitching, and is gone the moment the player
	 * commits to closing the distance — leaving them with no way to confirm what they saw.
	 */
	private void tickPhantom(ServerWorld world) {
		Config cfg = Config.get();
		if (++this.phantomTicks > cfg.phantomLifetimeTicks) {
			this.discard();
			return;
		}
		PlayerEntity nearest = world.getClosestPlayer(this, cfg.phantomVanishDistance);
		if (nearest != null) {
			this.discard();
			return;
		}
		// Stare back. A still figure that occasionally jerks reads worse than one that paces.
		PlayerEntity watcher = world.getClosestPlayer(this, 48.0);
		if (watcher != null) {
			this.lookAtEntity(watcher, 30f, 30f);
			if (this.phantomTicks % 45 == 0) {
				this.triggerAnim("twitch", "twitch");
			}
		}
	}

	public boolean isPhantom() {
		return this.phantom;
	}

	public void setPhantom(boolean phantom) {
		this.phantom = phantom;
		if (phantom) {
			// Nothing about it should move, chase, or make noise of its own.
			this.setAiDisabled(true);
			this.setSilent(true);
		}
	}

	@Override
	public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Phantom", this.phantom);
		if (this.lair != null) {
			nbt.putIntArray("Lair", new int[] {this.lair.getX(), this.lair.getY(), this.lair.getZ()});
		}
	}

	@Override
	public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.setPhantom(nbt.getBoolean("Phantom"));
		int[] lair = nbt.getIntArray("Lair");
		// Deliberately not restoring dragTicks: a drag interrupted by a reload should let the victim go
		// rather than resume with a passenger the world may no longer have.
		this.lair = lair.length == 3 ? new BlockPos(lair[0], lair[1], lair[2]) : null;
	}

	public BlockPos getLastHeardPos() {
		return this.lastHeardPos;
	}

	public void clearInterest() {
		this.lastHeardPos = null;
		this.interestTicks = 0;
	}

	public boolean isWithdrawing() {
		// Dragging counts: while it is hauling someone it is not listening for anyone else.
		return this.withdrawTicks > 0 || this.dragTicks > 0;
	}

	/**
	 * Called by the hunt goal on contact. Heavy hit plus blindness plus a sanity crash, then the
	 * Director is forced into RELEASE and this entity backs off. Being caught has to hurt without
	 * becoming a death-and-inventory-recovery chore.
	 */
	public void onReachPlayer(PlayerEntity player) {
		Config cfg = Config.get();
		// A blow that would finish them does not. It takes them instead — the death is not the horror
		// beat, the journey to the lair is, and you have to be alive to be made to watch it.
		if (player instanceof ServerPlayerEntity victim
				&& !victim.isCreative() && !victim.isSpectator()
				&& player.getHealth() - cfg.contactDamage <= 0f
				&& this.dragTicks == 0
				&& this.getWorld() instanceof ServerWorld world) {
			this.beginDrag(world, victim, cfg);
			return;
		}
		player.damage(this.getDamageSources().mobAttack(this), cfg.contactDamage);
		player.addStatusEffect(new StatusEffectInstance(
				StatusEffects.BLINDNESS, cfg.contactBlindnessTicks, 0, false, false, true));
		this.playSound(ModSounds.STALKER_LUNGE, 1.0f, 0.5f);
		if (this.getWorld() instanceof ServerWorld world) {
			// Splatter across the player, not the Stalker: the blood you are now wearing is theirs.
			world.spawnParticles(BLOOD, player.getX(), player.getY() + 1.0, player.getZ(),
					40, 0.45, 0.5, 0.45, 0.12);
		}

		HorrorState state = ModAttachments.get(player);
		ModAttachments.set(player, state.withSanity(state.sanity() - cfg.contactSanityLoss));

		// A kill it did not intend — drowning, a fall during the chase — still costs the face, just
		// without the walk. The drag is the good version of this; this is the fallback.
		if (!player.isAlive() && this.getWorld() instanceof ServerWorld serverWorld) {
			com.unseen.SkinTheft.take(serverWorld, player, this.getBlockPos());
		}

		TensionManager.forceRelease(player);
		this.withdrawTicks = 200;
		this.clearInterest();
	}

	// --- Being taken ---

	public boolean isDragging() {
		return this.dragTicks > 0;
	}

	/**
	 * Where it will take people.
	 * <p>
	 * A real generated lair if there is one within reach — that is the room with the faces already on
	 * the walls, and arriving somewhere that has clearly been used before is the whole point. Otherwise
	 * wherever it first appeared, which the Director only ever picks somewhere dark and out of sight, so
	 * it is at least the right kind of place.
	 */
	private BlockPos chooseLair(ServerWorld world) {
		BlockPos known = com.unseen.worldgen.LairSites.get(world)
				.nearest(this.getBlockPos(), LAIR_SEARCH_RADIUS);
		if (known != null) {
			return known;
		}
		// Where it appeared, not where it is now: at grab time it is standing on top of the victim, and
		// picking that would put the destination underfoot and end the haul immediately.
		return this.lair != null ? this.lair : this.getBlockPos();
	}

	/**
	 * Debug entry point. Closes the distance and starts the drag immediately, keeping the lair it was
	 * spawned at — the approach is not what needs testing, the haul is.
	 */
	public void grab(ServerPlayerEntity victim) {
		if (!(this.getWorld() instanceof ServerWorld world) || this.dragTicks > 0) {
			return;
		}
		// Fixed before it moves onto the victim, so the haul still has somewhere to go.
		if (this.lair == null) {
			this.lair = this.getBlockPos();
		}
		this.refreshPositionAndAngles(victim.getX(), victim.getY(), victim.getZ(), this.getYaw(), 0f);
		this.beginDrag(world, victim, Config.get());
	}

	/** Grabs them off their feet. They keep one point of health so that they keep their eyes. */
	private void beginDrag(ServerWorld world, ServerPlayerEntity victim, Config cfg) {
		// Chosen now rather than cached at spawn: a Stalker that appeared before any lair was known
		// would otherwise settle for an anonymous patch of dark and never reconsider.
		this.lair = chooseLair(world);

		// Take hold first. If the grab fails — they are already riding something — nothing else should
		// have happened to them, or we would leave them gutted and standing there for no reason.
		if (!victim.startRiding(this, true)) {
			return;
		}
		victim.setHealth(1f);
		victim.addStatusEffect(new StatusEffectInstance(
				StatusEffects.WEAKNESS, cfg.dragTicks + 100, 4, false, false, false));
		this.dragTicks = cfg.dragTicks;
		this.getNavigation().stop();
		this.playSound(ModSounds.STALKER_LUNGE, 1.2f, 0.35f);
		world.spawnParticles(BLOOD, victim.getX(), victim.getY() + 1.0, victim.getZ(),
				60, 0.4, 0.5, 0.4, 0.1);

		HorrorState state = ModAttachments.get(victim);
		ModAttachments.set(victim, state.withSanity(0f));
	}

	private void tickDrag(ServerWorld world) {
		this.dragTicks--;
		Entity passenger = this.getFirstPassenger();
		// isAlive matters as much as the instanceof: a victim who drowns or suffocates on the way is
		// refused a dismount by removePassenger below, so vanilla leaves the corpse in the passenger
		// list and the haul would otherwise carry on and finish on a body that has already respawned.
		if (!(passenger instanceof PlayerEntity victim) || !victim.isAlive()) {
			// Logged out, died on the way, or pulled off by a command. Nothing left to haul.
			this.dragTicks = 0;
			if (passenger != null) {
				super.removePassenger(passenger);
			}
			return;
		}
		if (this.lair != null && this.age % 20 == 0) {
			this.getNavigation().startMovingTo(
					this.lair.getX() + 0.5, this.lair.getY(), this.lair.getZ() + 0.5, 0.9);
		}
		boolean arrived = this.lair != null && this.squaredDistanceTo(
				this.lair.getX() + 0.5, this.lair.getY(), this.lair.getZ() + 0.5) < 4.0;
		// The timeout matters: if the lair is unreachable it must still finish, or you are held forever.
		if (arrived || this.dragTicks <= 0) {
			this.finishDrag(world, victim);
		}
	}

	/** Home. It puts them down, finishes them, and hangs the face. */
	private void finishDrag(ServerWorld world, PlayerEntity victim) {
		this.dragTicks = 0;
		BlockPos where = this.lair == null ? this.getBlockPos() : this.lair;
		victim.stopRiding();
		// A big finite number, not Float.MAX_VALUE: armour and resistance are multiplied through the
		// damage pipeline, and feeding it MAX_VALUE risks an infinity landing in someone's health.
		victim.damage(this.getDamageSources().mobAttack(this), 1000f);
		// Only if it actually finished them. A totem, or a switch to creative mid-haul, leaves them
		// standing there — and a live player with their face already hanging on a wall and something
		// wearing their name walking around is not a scare, it is a bug report.
		if (!victim.isAlive()) {
			com.unseen.SkinTheft.take(world, victim, where);
		}

		TensionManager.forceRelease(victim);
		this.withdrawTicks = 200;
		this.clearInterest();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		// You do not get to shrug it off. The grip holds until it is done with you.
		if (this.dragTicks > 0 && passenger instanceof PlayerEntity) {
			return;
		}
		super.removePassenger(passenger);
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
		if (this.dragTicks <= 0) {
			super.updatePassengerPosition(passenger, positionUpdater);
			return;
		}
		// Hauled along the ground behind it rather than carried on top: you are on your back, watching
		// the thing that has you, which is the only thing you still get to do.
		float yaw = this.bodyYaw * ((float) Math.PI / 180f);
		double behind = -1.35;
		positionUpdater.accept(passenger,
				this.getX() + -MathHelper.sin(yaw) * behind,
				this.getY() + 0.1,
				this.getZ() + MathHelper.cos(yaw) * behind);
	}

	// --- GeckoLib ---

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 6, state -> {
			if (state.isMoving()) {
				return state.setAndContinue(STALK);
			}
			return state.setAndContinue(IDLE);
		}));
		// Fires on a trigger so the twitch reads as involuntary rather than looping wallpaper.
		controllers.add(new AnimationController<>(this, "twitch", 0, state -> PlayState.STOP)
				.triggerableAnim("twitch", TWITCH));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	/** Bridges the vanilla vibration graph into {@link #lastHeardPos}. */
	private class HearingCallback implements Vibrations.Callback {
		private final PositionSource positionSource =
				new EntityPositionSource(StalkerEntity.this, StalkerEntity.this.getStandingEyeHeight());

		@Override
		public int getRange() {
			return Config.get().hearingRadius;
		}

		@Override
		public PositionSource getPositionSource() {
			return this.positionSource;
		}

		@Override
		public boolean accepts(ServerWorld world, BlockPos pos, RegistryEntry<GameEvent> event,
		                       GameEvent.Emitter emitter) {
			if (StalkerEntity.this.isRemoved() || StalkerEntity.this.isWithdrawing()
					|| StalkerEntity.this.phantom) {
				return false; // a hallucination hears nothing
			}
			if (emitter.sourceEntity() == StalkerEntity.this) {
				return false; // never chase its own noise
			}
			// A player inside a wardrobe or under a bed makes no noise this thing will accept.
			// This is the entire hiding mechanic: no invulnerability, just an absence of signal.
			if (emitter.sourceEntity() instanceof PlayerEntity player && com.unseen.Hiding.isHidden(player)) {
				return false;
			}
			return true;
		}

		@Override
		public void accept(ServerWorld world, BlockPos pos, RegistryEntry<GameEvent> event,
		                   net.minecraft.entity.Entity sourceEntity, net.minecraft.entity.Entity entity,
		                   float distance) {
			BlockPos previous = StalkerEntity.this.lastHeardPos;
			StalkerEntity.this.lastHeardPos = pos.toImmutable();
			StalkerEntity.this.interestTicks = 400;
			// Only on a fresh trail, so it does not chirp on every footstep you take.
			if (previous == null) {
				StalkerEntity.this.playSound(ModSounds.STALKER_NOTICE, 0.7f, 0.6f);
			}
			if (sourceEntity instanceof LivingEntity living) {
				StalkerEntity.this.setTarget(living);
			}
		}
	}
}
