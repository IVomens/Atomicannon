package dev.schematicannon.table;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.registry.ModBlockEntities;
import dev.schematicannon.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SchematicTableBlockEntity extends BlockEntity implements MenuProvider {
	public final SimpleContainer inventory = new SimpleContainer(2) {
		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot == 0 && (stack.is(ModItems.EMPTY_SCHEMATIC.get()) || stack.is(ModItems.SCHEMATIC_AND_QUILL.get())
				|| stack.is(ModItems.SCHEMATIC.get()));
		}

		@Override
		public boolean canTakeItem(net.minecraft.world.Container into, int slot, ItemStack stack) {
			return slot == 1;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			SchematicTableBlockEntity.this.setChanged();
		}
	};

	public boolean isUploading;
	public @Nullable String uploadingSchematic;
	public float uploadingProgress;
	public boolean sendUpdate;
	private ItemStack uploadInput = ItemStack.EMPTY;

	public SchematicTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SCHEMATIC_TABLE.get(), pos, state);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		if (!input.getBooleanOr("ClientPacket", false)) {
			inventory.clearContent();
			ContainerHelper.loadAllItems(input.childOrEmpty("Inventory"), inventory.getItems());
			return;
		}
		isUploading = input.getBooleanOr("Uploading", false);
		uploadingSchematic = isUploading ? input.getStringOr("Schematic", "") : null;
		uploadingProgress = isUploading ? input.getFloatOr("Progress", 0) : 0;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output.child("Inventory"), inventory.getItems(), true);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.putBoolean("ClientPacket", true);
		if (isUploading && uploadingSchematic != null) {
			output.putBoolean("Uploading", true);
			output.putString("Schematic", uploadingSchematic);
			output.putFloat("Progress", uploadingProgress);
		}
		return output.buildResult();
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null)
			Containers.dropContents(level, pos, inventory);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SchematicTableBlockEntity be) {
		// Update Client block entity
		if (be.sendUpdate) {
			be.sendUpdate = false;
			level.sendBlockUpdated(pos, state, state, 6);
		}
	}

	public void startUpload(String schematic) {
		isUploading = true;
		uploadingProgress = 0;
		uploadingSchematic = schematic;
		sendUpdate = true;
		// use exactly one input item per upload, even from a stack
		uploadInput = inventory.getItem(0).copyWithCount(1);
		inventory.getItem(0).shrink(1);
		inventory.setChanged();
	}

	/** True when there is something to write onto and the output slot is free. */
	public boolean canStartUpload() {
		return !isUploading && !inventory.getItem(0).isEmpty() && inventory.getItem(1).isEmpty();
	}

	/** Upload failed or was cancelled: give back the item it used. */
	public void cancelUpload() {
		ItemStack refund = uploadInput;
		finishUpload();
		if (refund.isEmpty() || level == null)
			return;
		ItemStack input = inventory.getItem(0);
		if (input.isEmpty())
			inventory.setItem(0, refund);
		else if (ItemStack.isSameItemSameComponents(input, refund) && input.getCount() < input.getMaxStackSize())
			input.grow(1);
		else
			Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, refund);
		inventory.setChanged();
	}

	public void finishUpload() {
		uploadInput = ItemStack.EMPTY;
		isUploading = false;
		uploadingProgress = 0;
		uploadingSchematic = null;
		sendUpdate = true;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new SchematicTableMenu(id, inv, this);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("schematicannon.gui.schematicTable.title");
	}
}
