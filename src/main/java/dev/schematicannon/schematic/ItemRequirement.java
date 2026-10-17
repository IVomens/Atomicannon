package dev.schematicannon.schematic;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.compat.NtmCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Port of Create's ItemRequirement: what the Schematicannon has to pull from inventories to place something. */
public class ItemRequirement {
	public static final ItemRequirement NONE = new ItemRequirement(Collections.emptyList());
	public static final ItemRequirement INVALID = new ItemRequirement(Collections.emptyList());

	protected final List<StackRequirement> requiredItems;

	public ItemRequirement(List<StackRequirement> requiredItems) {
		this.requiredItems = requiredItems;
	}

	public ItemRequirement(ItemUseType usage, ItemStack stack) {
		this(List.of(new StackRequirement(stack, usage)));
	}

	public ItemRequirement(ItemUseType usage, Item item) {
		this(usage, new ItemStack(item));
	}

	public static ItemRequirement of(BlockState state, @Nullable CompoundTag data) {
		ItemRequirement ntm = NtmCompat.getRequirement(state);
		ItemRequirement base = ntm != null ? ntm : defaultOf(state);
		// painted NTM cables / ducts / tubes also cost the block they are painted with
		// and NTM machines carry their recipe blueprints over, so they cost those exact blueprints
		return base.union(NtmCompat.paintRequirement(state, data)).union(NtmCompat.blueprintRequirement(state, data));
	}

	private static ItemRequirement defaultOf(BlockState state) {
		Block block = state.getBlock();
		if (state.isAir())
			return NONE;
		Item item = block.asItem();
		if (item == Items.AIR)
			return INVALID;

		// double slab needs two items
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE)
			return new ItemRequirement(ItemUseType.CONSUME, new ItemStack(item, 2));
		if (block instanceof TurtleEggBlock)
			return new ItemRequirement(ItemUseType.CONSUME, new ItemStack(item, state.getValue(TurtleEggBlock.EGGS)));
		if (block instanceof SeaPickleBlock)
			return new ItemRequirement(ItemUseType.CONSUME, new ItemStack(item, state.getValue(SeaPickleBlock.PICKLES)));
		if (block instanceof SnowLayerBlock)
			return new ItemRequirement(ItemUseType.CONSUME, new ItemStack(item, state.getValue(SnowLayerBlock.LAYERS)));
		if (block instanceof FarmlandBlock || block instanceof DirtPathBlock)
			return new ItemRequirement(ItemUseType.CONSUME, Items.DIRT);
		// Tall grass doesnt exist as a block so use 2 grass blades
		if (block == Blocks.TALL_GRASS)
			return new ItemRequirement(ItemUseType.CONSUME, new ItemStack(Items.SHORT_GRASS, 2));
		// Large ferns don't exist as blocks so use 2 ferns instead
		if (block == Blocks.LARGE_FERN)
			return new ItemRequirement(ItemUseType.CONSUME, new ItemStack(Items.FERN, 2));

		return new ItemRequirement(ItemUseType.CONSUME, item);
	}

	/**
	 * Entities are placed without their contents (empty item frames, bare armor stands) so creative items
	 * can never be smuggled into survival through a schematic.
	 */
	public static ItemRequirement ofEntity(EntityType<?> type) {
		if (type == EntityTypes.ITEM_FRAME)
			return new ItemRequirement(ItemUseType.CONSUME, Items.ITEM_FRAME);
		if (type == EntityTypes.GLOW_ITEM_FRAME)
			return new ItemRequirement(ItemUseType.CONSUME, Items.GLOW_ITEM_FRAME);
		if (type == EntityTypes.PAINTING)
			return new ItemRequirement(ItemUseType.CONSUME, Items.PAINTING);
		if (type == EntityTypes.ARMOR_STAND)
			return new ItemRequirement(ItemUseType.CONSUME, Items.ARMOR_STAND);
		return INVALID;
	}

	public boolean isEmpty() {
		return NONE == this;
	}

	public boolean isInvalid() {
		return INVALID == this;
	}

	public List<StackRequirement> getRequiredItems() {
		return requiredItems;
	}

	public ItemRequirement union(ItemRequirement other) {
		if (this.isInvalid() || other.isInvalid())
			return INVALID;
		if (this.isEmpty())
			return other;
		if (other.isEmpty())
			return this;
		return new ItemRequirement(Stream.concat(requiredItems.stream(), other.requiredItems.stream())
			.collect(Collectors.toList()));
	}

	public enum ItemUseType {
		CONSUME, DAMAGE
	}

	public static class StackRequirement {
		public final ItemStack stack;
		public final ItemUseType usage;
		/** Must match components too (e.g. an NTM blueprint for one specific recipe pool). */
		public final boolean exact;

		public StackRequirement(ItemStack stack, ItemUseType usage) {
			this(stack, usage, false);
		}

		/** Other items accepted instead (e.g. any tier of a structure part); empty for just {@link #stack}. */
		public final List<ItemStack> alternatives;

		public StackRequirement(ItemStack stack, ItemUseType usage, boolean exact) {
			this(stack, usage, exact, List.of());
		}

		public StackRequirement(ItemStack stack, ItemUseType usage, boolean exact, List<ItemStack> alternatives) {
			this.stack = stack;
			this.usage = usage;
			this.exact = exact;
			this.alternatives = alternatives;
		}

		public boolean matches(ItemStack other) {
			if (exact ? ItemStack.isSameItemSameComponents(stack, other) : ItemStack.isSameItem(stack, other))
				return true;
			for (ItemStack alternative : alternatives)
				if (ItemStack.isSameItem(alternative, other))
					return true;
			return false;
		}
	}
}
