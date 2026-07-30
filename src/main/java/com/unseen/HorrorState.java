package com.unseen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The whole per-player horror state: what the Director thinks, and what the player's mind is doing.
 * One record so it is one attachment, one codec, one automatic sync.
 */
public record HorrorState(float sanity, float stress, Phase phase) {


	public static final HorrorState INITIAL = new HorrorState(100f, 0f, Phase.BUILD_UP);

	public static final Codec<HorrorState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.fieldOf("sanity").forGetter(HorrorState::sanity),
			Codec.FLOAT.fieldOf("stress").forGetter(HorrorState::stress),
			Codec.STRING.fieldOf("phase").forGetter(s -> s.phase().name())
	).apply(i, (sanity, stress, phase) -> new HorrorState(sanity, stress, Phase.byName(phase))));

	public static final PacketCodec<RegistryByteBuf, HorrorState> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, HorrorState::sanity,
			PacketCodecs.FLOAT, HorrorState::stress,
			PacketCodecs.STRING, s -> s.phase().name(),
			(sanity, stress, phase) -> new HorrorState(sanity, stress, Phase.byName(phase))
	);

	public HorrorState withSanity(float v) {
		return new HorrorState(clamp(v), stress, phase);
	}

	public HorrorState withStress(float v) {
		return new HorrorState(sanity, clamp(v), phase);
	}

	public HorrorState withPhase(Phase p) {
		return new HorrorState(sanity, stress, p);
	}

	public static float clamp(float v) {
		return Math.max(0f, Math.min(100f, v));
	}
}
