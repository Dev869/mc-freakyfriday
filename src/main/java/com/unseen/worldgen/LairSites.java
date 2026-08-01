package com.unseen.worldgen;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Where the lairs are.
 * <p>
 * The problem this solves: {@link LairFeature} knows exactly where it just built a lair, but it runs on
 * worldgen threads and must not touch {@link ServerWorld} — reaching for it there is the deadlock that
 * already cost an iteration. So the feature drops the position into a concurrent queue and walks away,
 * and the server drains that queue on the main thread into a {@link PersistentState} that survives a
 * restart. Nothing on the worldgen side ever touches the world.
 */
public class LairSites extends PersistentState {

	private static final String KEY = "unseen_lairs";

	/** Written from worldgen threads, drained on the server thread. */
	private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

	private record Pending(RegistryKey<World> world, BlockPos pos) {
	}

	private final List<BlockPos> sites = new ArrayList<>();

	public static final Type<LairSites> TYPE =
			new Type<>(LairSites::new, LairSites::fromNbt, null);

	private static LairSites fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		LairSites state = new LairSites();
		NbtList list = nbt.getList("Sites", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			state.sites.add(new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtList list = new NbtList();
		for (BlockPos pos : this.sites) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("x", pos.getX());
			entry.putInt("y", pos.getY());
			entry.putInt("z", pos.getZ());
			list.add(entry);
		}
		nbt.put("Sites", list);
		return nbt;
	}

	/** Called from worldgen. Must not touch the world — only the queue. */
	public static void record(RegistryKey<World> world, BlockPos pos) {
		PENDING.add(new Pending(world, pos.toImmutable()));
	}

	/** Called once per server tick. Cheap when there is nothing pending, which is almost always. */
	public static void drain(MinecraftServer server) {
		Pending pending;
		while ((pending = PENDING.poll()) != null) {
			ServerWorld world = server.getWorld(pending.world());
			if (world == null) {
				continue;
			}
			LairSites state = get(world);
			// Deduped: the debug command can place two lairs on the same spot, and nothing else should
			// be able to grow this list without bound either.
			if (state.sites.contains(pending.pos())) {
				continue;
			}
			state.sites.add(pending.pos());
			state.markDirty();
		}
	}

	public static LairSites get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	/**
	 * The nearest lair to a position, or null if none is known within range.
	 * <p>
	 * Linear over every lair the world has generated. That is fine and will stay fine: lairs are rare,
	 * and this runs once when a Stalker takes hold of someone, not on a tick.
	 */
	public BlockPos nearest(BlockPos from, double maxDistance) {
		BlockPos best = null;
		double bestSq = maxDistance * maxDistance;
		for (BlockPos site : this.sites) {
			double distSq = site.getSquaredDistance(from);
			if (distSq <= bestSq) {
				bestSq = distSq;
				best = site;
			}
		}
		return best;
	}

	public int count() {
		return this.sites.size();
	}
}
