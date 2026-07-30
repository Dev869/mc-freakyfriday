package com.unseen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Sound events for the Stalker.
 * <p>
 * The definitions in {@code sounds.json} point at vanilla audio files rather than shipping new ones,
 * so the mod is audible immediately and the event ids stay stable — dropping real {@code .ogg} files
 * in later is a resource change, not a code change.
 * <p>
 * Everything here is emitted through {@link net.minecraft.entity.Entity#playSound}, which is
 * positional. That is the one rule that keeps Sound Physics Remastered working: a global or UI sound
 * bypasses attenuation entirely and cannot be occluded or reverberated.
 */
public final class ModSounds {
	public static final SoundEvent STALKER_BREATH = register("stalker.breath");
	public static final SoundEvent STALKER_STEP = register("stalker.step");
	public static final SoundEvent STALKER_NOTICE = register("stalker.notice");
	public static final SoundEvent STALKER_LUNGE = register("stalker.lunge");
	/** The player's own heartbeat. Correctly non-positional — this one is in your head, not the world. */
	public static final SoundEvent HEARTBEAT = register("heartbeat");
	/** Low drone under a PEAK phase. Also non-positional: it is dread, not a thing in the room. */
	public static final SoundEvent DREAD_DRONE = register("dread.drone");

	private ModSounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = UnseenMod.id(path);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	static void init() {
	}
}
