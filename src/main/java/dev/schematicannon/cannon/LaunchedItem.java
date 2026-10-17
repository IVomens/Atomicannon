package dev.schematicannon.cannon;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.schematic.SchematicPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A block or entity in flight from the cannon to its target (port of Create's LaunchedItem, minus belts). */
public abstract class LaunchedItem {
	public int totalTicks;
	public int ticksRemaining;
	public BlockPos target = BlockPos.ZERO;
	public ItemStack stack = ItemStack.EMPTY;

	LaunchedItem() {}

	private LaunchedItem(BlockPos start, BlockPos target, ItemStack stack) {
		this.target = target;
		this.stack = stack;
		this.totalTicks = this.ticksRemaining = ticksForDistance(start, target);
	}

	private static int ticksForDistance(BlockPos start, BlockPos target) {
		return (int) (Math.max(10, Math.sqrt(Math.sqrt(target.distSqr(start))) * 4f));
	}

	/** @return true when this item has landed and can be removed. */
	public boolean update(Level world) {
		if (ticksRemaining > 0) {
			ticksRemaining--;
			return false;
		}
		if (world.isClientSide())
			return false;
		// never force-load a chunk to land a block: wait until the target is loaded again
		if (!world.isLoaded(target))
			return false;
		try {
			place(world);
		} catch (RuntimeException | LinkageError e) {
			// a block (often another mod's) failing to place must not take the server down
			dev.schematicannon.Schematicannon.LOGGER.error("Schematicannon could not place a block at {}; skipped", target, e);
		}
		return true;
	}

	abstract void place(Level world);

	/** For clients: only what is needed to draw the projectile (no block entity data). */
	public CompoundTag serializeForClient(net.minecraft.core.HolderLookup.Provider registries) {
		return serialize(registries);
	}

	public CompoundTag serialize(net.minecraft.core.HolderLookup.Provider registries) {
		CompoundTag c = new CompoundTag();
		c.putInt("TotalTicks", totalTicks);
		c.putInt("TicksLeft", ticksRemaining);
		if (!stack.isEmpty())
			c.store("Stack", ItemStack.CODEC, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack);
		c.store("Target", BlockPos.CODEC, target);
		return c;
	}

	void read(CompoundTag c, net.minecraft.core.HolderLookup.Provider registries, HolderGetter<Block> blocks) {
		target = c.read("Target", BlockPos.CODEC).orElse(BlockPos.ZERO);
		ticksRemaining = c.getIntOr("TicksLeft", 0);
		totalTicks = c.getIntOr("TotalTicks", 1);
		stack = c.read("Stack", ItemStack.CODEC, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE))
			.orElse(ItemStack.EMPTY);
	}

	public static @Nullable LaunchedItem fromNBT(CompoundTag c, net.minecraft.core.HolderLookup.Provider registries,
		HolderGetter<Block> blocks, boolean clientPacket) {
		LaunchedItem launched;
		if (c.contains("BlockState"))
			launched = new ForBlockState();
		else if (clientPacket)
			launched = new ForDisplay();
		else
			return null; // entities in flight are not persisted to disk
		launched.read(c, registries, blocks);
		return launched;
	}

	/** Client-side stand-in for an entity in flight; only used to draw the projectile. */
	public static class ForDisplay extends LaunchedItem {
		@Override
		void place(Level world) {}
	}

	public static class ForBlockState extends LaunchedItem {
		public BlockState state;
		public @Nullable CompoundTag data;

		ForBlockState() {}

		public ForBlockState(BlockPos start, BlockPos target, ItemStack stack, BlockState state, @Nullable CompoundTag data) {
			super(start, target, stack);
			this.state = state;
			this.data = data;
		}

		@Override
		public CompoundTag serialize(net.minecraft.core.HolderLookup.Provider registries) {
			CompoundTag tag = super.serialize(registries);
			tag.put("BlockState", NbtUtils.writeBlockState(state));
			if (data != null)
				tag.put("Data", data);
			return tag;
		}

		@Override
		public CompoundTag serializeForClient(net.minecraft.core.HolderLookup.Provider registries) {
			CompoundTag tag = super.serialize(registries);
			tag.put("BlockState", NbtUtils.writeBlockState(state));
			return tag;
		}

		@Override
		void read(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, HolderGetter<Block> blocks) {
			super.read(nbt, registries, blocks);
			state = NbtUtils.readBlockState(blocks, nbt.getCompoundOrEmpty("BlockState"));
			data = nbt.getCompound("Data").orElse(null);
		}

		@Override
		void place(Level world) {
			SchematicPlacement.placeSchematicBlock(world, state, target, stack, data);
		}
	}

	public static class ForEntity extends LaunchedItem {
		public final Entity entity;

		public ForEntity(BlockPos start, BlockPos target, ItemStack stack, Entity entity) {
			super(start, target, stack);
			this.entity = entity;
		}

		@Override
		public CompoundTag serialize(net.minecraft.core.HolderLookup.Provider registries) {
			// Only sent to clients for rendering the projectile; not reloaded from disk.
			return super.serialize(registries);
		}

		@Override
		void place(Level world) {
			world.addFreshEntity(entity);
		}
	}
}
