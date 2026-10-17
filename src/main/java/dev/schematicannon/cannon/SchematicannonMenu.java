package dev.schematicannon.cannon;

import dev.schematicannon.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Same slot layout as Create's SchematicannonMenu so the screen positions match. */
public class SchematicannonMenu extends AbstractContainerMenu {
	public final SchematicannonBlockEntity contentHolder;
	private final Container container;

	public SchematicannonMenu(int id, Inventory inv, SchematicannonBlockEntity be) {
		super(ModMenus.SCHEMATICANNON.get(), id);
		this.contentHolder = be;
		this.container = be == null ? new SimpleContainer(5) : be.inventory;

		addSlot(new FilteredSlot(container, 0, 15, 65));
		addSlot(new OutputSlot(container, 1, 171, 65));
		addSlot(new FilteredSlot(container, 2, 134, 19));
		addSlot(new OutputSlot(container, 3, 174, 19));
		// gunpowder, or an NTM battery on the Advanced Atomicannon
		addSlot(new FilteredSlot(container, 4, 15, 19));
		addStandardInventorySlots(inv, 37, 161);
	}

	public static SchematicannonMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
		var pos = buf.readBlockPos();
		var found = inv.player.level().getBlockEntity(pos);
		var be = found instanceof SchematicannonBlockEntity cannon ? cannon : null;
		if (be == null)
			dev.schematicannon.Schematicannon.LOGGER.warn("Schematicannon menu opened for {} but found {} ({})", pos, found,
				inv.player.level().getBlockState(pos));
		return new SchematicannonMenu(id, inv, be);
	}

	@Override
	public boolean stillValid(Player player) {
		return contentHolder != null && Container.stillValidBlockEntity(contentHolder, player);
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		Slot clickedSlot = getSlot(index);
		if (!clickedSlot.hasItem())
			return ItemStack.EMPTY;
		ItemStack stack = clickedSlot.getItem();
		if (index < 5) {
			moveItemStackTo(stack, 5, slots.size(), true);
		} else {
			if (!moveItemStackTo(stack, 0, 1, false) && !moveItemStackTo(stack, 2, 3, false))
				moveItemStackTo(stack, 4, 5, false);
		}
		clickedSlot.setChanged();
		return ItemStack.EMPTY;
	}

	private static class FilteredSlot extends Slot {
		FilteredSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return container.canPlaceItem(getContainerSlot(), stack);
		}
	}

	private static class OutputSlot extends Slot {
		OutputSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
