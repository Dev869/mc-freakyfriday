package com.unseen.block;

import com.unseen.portal.HollowPortal;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The surface of a way into the Hollow.
 * <p>
 * Modelled on the nether portal — no collision, a dwell timer before it takes you — because that
 * behaviour is already in players' hands. What differs is that it is silent and unlit: it emits no
 * light, so a portal in a dark room is something you walk into rather than something you see coming.
 */
public class HollowPortalBlock extends Block {
	public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

	private static final VoxelShape X_SHAPE = Block.createCuboidShape(0, 0, 6, 16, 16, 10);
	private static final VoxelShape Z_SHAPE = Block.createCuboidShape(6, 0, 0, 10, 16, 16);

	public HollowPortalBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getStateManager().getDefaultState().with(AXIS, Direction.Axis.X));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
		return state.get(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
		return VoxelShapes.empty();
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		HollowPortal.onEntityInPortal(world, entity);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		// Sparse and dark. A portal should read as an absence, not a light source.
		if (random.nextInt(4) != 0) {
			return;
		}
		world.addParticle(ParticleTypes.SMOKE,
				pos.getX() + random.nextDouble(),
				pos.getY() + random.nextDouble(),
				pos.getZ() + random.nextDouble(),
				0.0, 0.02, 0.0);
	}
}
