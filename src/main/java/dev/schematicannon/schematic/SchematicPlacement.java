package dev.schematicannon.schematic;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.compat.NtmCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** Port of Create's {@code BlockHelper.placeSchematicBlock} and related helpers. */
public final class SchematicPlacement {
	/** Key inside block data carrying copied machine settings (applied after placement, never loaded as data). */
	public static final String SETTINGS_KEY = "schematicannon:settings";

	/** Blocks that need something to hang on are placed after everything else, like Create's "brittle" blocks. */
	public static boolean shouldDefer(BlockState state) {
		Block block = state.getBlock();
		return block instanceof TorchBlock || block instanceof BaseRailBlock || block instanceof SignBlock
			|| block instanceof LadderBlock || block instanceof DoorBlock || block instanceof RedStoneWireBlock
			|| block instanceof DiodeBlock || block instanceof CarpetBlock || block instanceof BasePressurePlateBlock
			|| block instanceof LanternBlock || block instanceof BellBlock || block instanceof FlowerPotBlock
			|| block instanceof TripWireHookBlock || block instanceof ButtonBlock || block instanceof VegetationBlock
			|| block instanceof FaceAttachedHorizontalDirectionalBlock || state.is(BlockTags.BANNERS)
			|| state.hasProperty(BlockStateProperties.HANGING);
	}

	public static BlockState setZeroAge(BlockState state) {
		if (state.hasProperty(BlockStateProperties.AGE_1))
			return state.setValue(BlockStateProperties.AGE_1, 0);
		if (state.hasProperty(BlockStateProperties.AGE_2))
			return state.setValue(BlockStateProperties.AGE_2, 0);
		if (state.hasProperty(BlockStateProperties.AGE_3))
			return state.setValue(BlockStateProperties.AGE_3, 0);
		if (state.hasProperty(BlockStateProperties.AGE_5))
			return state.setValue(BlockStateProperties.AGE_5, 0);
		if (state.hasProperty(BlockStateProperties.AGE_7))
			return state.setValue(BlockStateProperties.AGE_7, 0);
		if (state.hasProperty(BlockStateProperties.AGE_15))
			return state.setValue(BlockStateProperties.AGE_15, 0);
		if (state.hasProperty(BlockStateProperties.AGE_25))
			return state.setValue(BlockStateProperties.AGE_25, 0);
		if (state.hasProperty(BlockStateProperties.LEVEL_HONEY))
			return state.setValue(BlockStateProperties.LEVEL_HONEY, 0);
		if (state.hasProperty(BlockStateProperties.HATCH))
			return state.setValue(BlockStateProperties.HATCH, 0);
		if (state.hasProperty(BlockStateProperties.STAGE))
			return state.setValue(BlockStateProperties.STAGE, 0);
		if (state.is(BlockTags.CAULDRONS))
			return Blocks.CAULDRON.defaultBlockState();
		if (state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))
			return state.setValue(BlockStateProperties.LEVEL_COMPOSTER, 0);
		if (state.hasProperty(BlockStateProperties.EXTENDED))
			return state.setValue(BlockStateProperties.EXTENDED, false);
		return state;
	}

	public static void placeSchematicBlock(Level level, BlockState state, BlockPos target, @Nullable ItemStack stack,
		@Nullable CompoundTag data) {
		if (state.hasProperty(BlockStateProperties.EXTENDED))
			state = state.setValue(BlockStateProperties.EXTENDED, Boolean.FALSE);
		if (state.hasProperty(BlockStateProperties.WATERLOGGED))
			state = state.setValue(BlockStateProperties.WATERLOGGED, Boolean.FALSE);
		if (state.getBlock() == Blocks.COMPOSTER)
			state = Blocks.COMPOSTER.defaultBlockState();
		else if (state.is(BlockTags.CAULDRONS))
			state = Blocks.CAULDRON.defaultBlockState();

		if (level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, target)
			&& state.getFluidState().is(FluidTags.WATER)) {
			level.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
				2.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8F);
			if (level instanceof ServerLevel serverLevel)
				serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX() + .5, target.getY() + .5,
					target.getZ() + .5, 8, .3, .3, .3, 0);
			return;
		}

		CompoundTag settings = null;
		if (data != null && data.contains(SETTINGS_KEY)) {
			data = data.copy();
			settings = data.getCompoundOrEmpty(SETTINGS_KEY);
			data.remove(SETTINGS_KEY);
			if (data.isEmpty())
				data = null;
		}

		if (NtmCompat.isMultiblockCore(state)) {
			NtmCompat.assemble(level, target, state);
			loadData(level, state, target, data);
			NtmCompat.applySettings(level, target, settings);
			return;
		}

		if (state.getBlock() instanceof BaseRailBlock)
			level.setBlock(target, state, Block.UPDATE_CLIENTS);
		else
			level.setBlock(target, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

		loadData(level, state, target, data);

		try {
			state.getBlock().setPlacedBy(level, target, state, null, stack == null ? ItemStack.EMPTY : stack);
		} catch (Exception ignored) {
		}
		NtmCompat.applySettings(level, target, settings);
	}

	private static void loadData(Level level, BlockState state, BlockPos target, @Nullable CompoundTag data) {
		if (data == null)
			return;
		BlockEntity blockEntity = level.getBlockEntity(target);
		if (blockEntity == null)
			return;
		CompoundTag tag = data.copy();
		tag.putInt("x", target.getX());
		tag.putInt("y", target.getY());
		tag.putInt("z", target.getZ());
		try {
			blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
			blockEntity.setChanged();
			level.sendBlockUpdated(target, level.getBlockState(target), level.getBlockState(target), Block.UPDATE_CLIENTS);
		} catch (Exception e) {
			Schematicannon.LOGGER.warn("Could not apply schematic data to {} at {}", state, target, e);
		}
	}

	/** Creates an entity from schematic data, emptied of any items it was holding. */
	public static @Nullable Entity createEntity(Level level, CompoundTag nbt, Vec3 worldPos, Rotation rotation, Mirror mirror) {
		var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), nbt);
		EntityType<?> type = EntityType.by(input).orElse(null);
		if (type == null || ItemRequirement.ofEntity(type).isInvalid())
			return null;
		Entity entity = EntityType.create(type, input, level, EntitySpawnReason.STRUCTURE).orElse(null);
		if (entity == null)
			return null;
		float yaw = entity.rotate(rotation);
		yaw += entity.mirror(mirror) - entity.getYRot();
		entity.snapTo(worldPos.x, worldPos.y, worldPos.z, yaw, entity.getXRot());
		if (entity instanceof ItemFrame frame)
			frame.setItem(ItemStack.EMPTY, false);
		if (entity instanceof ArmorStand stand)
			for (EquipmentSlot slot : EquipmentSlot.values())
				stand.setItemSlot(slot, ItemStack.EMPTY);
		return entity;
	}

	private SchematicPlacement() {}
}
