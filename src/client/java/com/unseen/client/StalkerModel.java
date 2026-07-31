package com.unseen.client;

import com.unseen.UnseenMod;
import com.unseen.entity.StalkerEntity;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Everything the keyframes cannot express, applied per-frame on top of the baked animation.
 * <p>
 * Keyframes do not know who is watching. This does: {@code HorrorState} is a self-syncing attachment,
 * so the local player's sanity is available here with no networking, and the Stalker can physically
 * deform in proportion to how badly that specific player is doing.
 * <p>
 * Note the units. The animation JSON is in degrees; bone rotations at this layer are radians.
 * <p>
 * Head tracking is done by hand rather than by passing a head bone name to the superclass. The
 * superclass sets the head bone from the <em>full</em> head yaw, and what is wanted is the yaw
 * clamped hard with the remainder pushed into the eyes.
 */
public class StalkerModel extends DefaultedEntityGeoModel<StalkerEntity> {

	/** Peak extra rotation, in degrees, applied at zero sanity. */
	private static final float MAX_DISTORTION_DEGREES = 9f;

	/** How far the head bone itself will turn before it gives up and lets the eyes do the work. */
	private static final float HEAD_LIMIT_DEGREES = 35f;
	private static final float EYE_LIMIT_DEGREES = 145f;

	/** Ticks between blinks, and how long one lasts. Long enough that it never reads as a rhythm. */
	private static final int BLINK_PERIOD = 900;
	private static final int BLINK_LENGTH = 8;

	private static final String[] EXTRA_EYES = {
			"eye_extra_0", "eye_extra_1", "eye_extra_2", "eye_extra_3", "eye_extra_4",
			"eye_extra_5", "eye_extra_6", "eye_extra_7", "eye_extra_8"
	};

	public StalkerModel() {
		super(UnseenMod.id("stalker"));
	}

	@Override
	public void setCustomAnimations(StalkerEntity entity, long instanceId,
	                                AnimationState<StalkerEntity> state) {
		super.setCustomAnimations(entity, instanceId, state);

		// 0 at full sanity, 1 at zero.
		float sanity = UnseenClient.state().sanity();
		float dread = 1f - MathHelper.clamp(sanity / 100f, 0f, 1f);

		aimHeadAndEyes(state, sanity, dread);
		blink(entity);
		distort(entity, state, dread);
	}

	/**
	 * The head gives up at {@link #HEAD_LIMIT_DEGREES} and the eyes carry the rest. A head that turns
	 * to follow you is a monster; a head that does not need to is worse.
	 */
	private void aimHeadAndEyes(AnimationState<StalkerEntity> state, float sanity, float dread) {
		EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);

		float headYaw = MathHelper.clamp(look.netHeadYaw(), -HEAD_LIMIT_DEGREES, HEAD_LIMIT_DEGREES);
		float headPitch = MathHelper.clamp(look.headPitch(), -HEAD_LIMIT_DEGREES, HEAD_LIMIT_DEGREES);
		float eyeYaw = MathHelper.clamp(look.netHeadYaw() - headYaw, -EYE_LIMIT_DEGREES, EYE_LIMIT_DEGREES);
		float eyePitch = MathHelper.clamp(look.headPitch() - headPitch, -EYE_LIMIT_DEGREES, EYE_LIMIT_DEGREES);

		getBone("head").ifPresent(bone -> {
			bone.setRotY(headYaw * MathHelper.RADIANS_PER_DEGREE);
			bone.setRotX(headPitch * MathHelper.RADIANS_PER_DEGREE);
		});
		aimEye("eye_left", eyeYaw, eyePitch);
		aimEye("eye_right", eyeYaw, eyePitch);

		// Two eyes at full sanity, one more opening per ten points lost, eleven by sanity 10.
		int open = MathHelper.clamp((int) ((90f - sanity) / 10f), 0, EXTRA_EYES.length);
		for (int i = 0; i < EXTRA_EYES.length; i++) {
			boolean hidden = i >= open;
			final int index = i;
			getBone(EXTRA_EYES[i]).ifPresent(bone -> bone.setHidden(hidden));
			if (!hidden) {
				// They all look at you, and each is slightly wrong about where you are.
				aimEye(EXTRA_EYES[index], eyeYaw * (0.7f + index * 0.03f), eyePitch);
			}
		}
	}

	private void aimEye(String bone, float yawDegrees, float pitchDegrees) {
		getBone(bone).ifPresent(b -> {
			b.setRotY(yawDegrees * MathHelper.RADIANS_PER_DEGREE);
			b.setRotX(pitchDegrees * MathHelper.RADIANS_PER_DEGREE);
			b.setScaleY(1f); // reset; blink() may squash it again immediately after
		});
	}

	/**
	 * Not blinking at all reads as a doll. One rare blink where the left eye closes four ticks before
	 * the right reads as something operating a face it does not own.
	 */
	private void blink(StalkerEntity entity) {
		int since = entity.age % BLINK_PERIOD;
		if (since >= BLINK_LENGTH) {
			return;
		}
		boolean leftShut = since < BLINK_LENGTH / 2;
		getBone("eye_left").ifPresent(b -> b.setScaleY(leftShut ? 0.05f : 1f));
		getBone("eye_right").ifPresent(b -> b.setScaleY(leftShut ? 1f : 0.05f));
	}

	/** Proportions go wrong before the pose does. */
	private void distort(StalkerEntity entity, AnimationState<StalkerEntity> state, float dread) {
		if (dread <= 0.01f) {
			return;
		}
		float amount = dread * MAX_DISTORTION_DEGREES * MathHelper.RADIANS_PER_DEGREE;
		// Offset by entity id so two Stalkers in one frame never move in lockstep.
		double t = state.getAnimationTick() + entity.getId() * 37.0;

		addRot("neck", amount * wobble(t, 0.70), 0f, amount * wobble(t, 1.31));
		addRot("spine_upper", 0f, amount * 0.6f * wobble(t, 0.53), amount * 0.4f * wobble(t, 1.07));
		addRot("arm_left_fore", amount * 1.4f * wobble(t, 0.89), 0f, 0f);
		addRot("arm_right_fore", amount * 1.4f * wobble(t, 1.17), 0f, 0f);

		getBone("neck").ifPresent(b -> b.setScaleY(1f + dread * 0.15f));
		getBone("arm_left_fore").ifPresent(b -> b.setScaleY(1f + dread * 0.12f));
		getBone("arm_right_fore").ifPresent(b -> b.setScaleY(1f + dread * 0.12f));
	}

	/** Frequencies chosen so no two axes ever line up. */
	private static float wobble(double t, double freq) {
		return (float) Math.sin(t * freq);
	}

	private void addRot(String bone, float x, float y, float z) {
		getBone(bone).ifPresent((GeoBone b) -> {
			b.setRotX(b.getRotX() + x);
			b.setRotY(b.getRotY() + y);
			b.setRotZ(b.getRotZ() + z);
		});
	}
}
