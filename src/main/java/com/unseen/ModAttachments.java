package com.unseen;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Persistent, self-syncing per-player state.
 * <p>
 * {@code syncWith} makes Fabric push changes to the owning client on its own, which is why this mod
 * has no networking layer at all — no payload record, no registration, no receiver.
 */
public final class ModAttachments {
	public static final AttachmentType<HorrorState> HORROR = AttachmentRegistry
			.<HorrorState>builder()
			.initializer(() -> HorrorState.INITIAL)
			.persistent(HorrorState.CODEC)
			.syncWith(HorrorState.PACKET_CODEC, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
			.buildAndRegister(UnseenMod.id("horror_state"));

	private ModAttachments() {
	}

	public static HorrorState get(PlayerEntity player) {
		return player.getAttachedOrCreate(HORROR);
	}

	public static void set(PlayerEntity player, HorrorState state) {
		player.setAttached(HORROR, state);
	}

	/** Touch the class so the static initialiser runs during mod init. */
	static void init() {
	}
}
