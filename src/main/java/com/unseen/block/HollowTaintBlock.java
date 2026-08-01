package com.unseen.block;

import com.unseen.HorrorState;
import com.unseen.ModAttachments;
import com.unseen.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Bleached grey matter that creeps out from a portal and eats the world around it.
 * <p>
 * Spread is bounded by construction rather than by a radius check: every block carries a
 * {@link #VIGOUR}, a portal seeds it at maximum, and each block it infects gets one less. At zero it
 * stops. That means the taint always dies out on its own, cannot creep across a whole world while the
 * player is away, and needs no origin tracking, no tick scheduler and no persistent bookkeeping — the
 * bound lives in the blockstate itself.
 */
public class HollowTaintBlock extends Block {
	/** How much further this block can push. 0 means it is spent. */
	public static final IntProperty VIGOUR = IntProperty.of("vigour", 0, 4);
	public static final int MAX_VIGOUR = 4;

	public HollowTaintBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getStateManager().getDefaultState().with(VIGOUR, MAX_VIGOUR));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(VIGOUR);
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return state.get(VIGOUR) > 0;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		int vigour = state.get(VIGOUR);
		if (vigour <= 0) {
			return;
		}
		// Slow. The taint should be something the player notices has changed between visits, not
		// something they can stand and watch grow.
		if (random.nextInt(3) != 0) {
			return;
		}
		BlockPos target = pos.offset(Direction.random(random));
		if (!world.isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) {
			return;
		}
		if (!canCorrupt(world.getBlockState(target))) {
			return;
		}
		world.setBlockState(target, ModBlocks.HOLLOW_TAINT.getDefaultState().with(VIGOUR, vigour - 1),
				Block.NOTIFY_ALL);
	}

	/**
	 * Natural ground only. Logs, planks and leaves are deliberately excluded: the taint spreading into
	 * a mansion or a player's house would eat hours of building, and a horror mechanic that deletes
	 * someone's work is a grief tool rather than an atmosphere.
	 */
	public static boolean canCorrupt(BlockState state) {
		return state.isIn(BlockTags.BASE_STONE_OVERWORLD)
				|| state.isIn(BlockTags.DIRT)
				|| state.isIn(BlockTags.SAND);
	}

	/**
	 * Standing on it costs you. The taint is a piece of the Hollow sitting in your world, and being in
	 * contact with it is treated exactly as being there.
	 */
	@Override
	public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
		if (world.isClient || !(entity instanceof PlayerEntity player)) {
			return;
		}
		// Per-player, not world time: onSteppedOn only fires as you move onto a block, so gating on a
		// global clock means the drain lands only when a step happens to coincide with it — most
		// crossings cost nothing at all, and when it does fire it fires for everyone at once.
		if (player.age % 20 != 0) {
			return;
		}
		HorrorState current = ModAttachments.get(player);
		ModAttachments.set(player, current.withSanity(current.sanity() - 0.5f));
	}
}
