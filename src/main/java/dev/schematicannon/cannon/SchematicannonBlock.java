package dev.schematicannon.cannon;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.schematicannon.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SchematicannonBlock extends BaseEntityBlock {
	public static final MapCodec<SchematicannonBlock> CODEC = simpleCodec(SchematicannonBlock::new);

	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(1, 0, 1, 15, 8, 15),
		Block.box(0.5, 8, 0.5, 15.5, 11, 15.5));

	private final boolean electric;

	public SchematicannonBlock(Properties properties) {
		this(properties, false);
	}

	/** @param electric the Advanced Atomicannon, powered by HBM NTM instead of gunpowder */
	public SchematicannonBlock(Properties properties, boolean electric) {
		super(properties);
		this.electric = electric;
	}

	public boolean isElectric() {
		return electric;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SchematicannonBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ModBlockEntities.SCHEMATICANNON.get(), SchematicannonBlockEntity::tick);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity entity, ItemStack stack) {
		if (entity != null && level.getBlockEntity(pos) instanceof SchematicannonBlockEntity be)
			be.defaultYaw = (-Mth.floor((entity.getYRot() + (entity.isShiftKeyDown() ? 180.0F : 0.0F)) * 16.0F / 360.0F + 0.5F)
				& 15) * 360.0F / 16.0F;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof SchematicannonBlockEntity be)
			player.openMenu(be, pos);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
		boolean movedByPiston) {
		if (level.getBlockEntity(pos) instanceof SchematicannonBlockEntity be)
			be.neighbourCheckCooldown = 0;
	}
}
