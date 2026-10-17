package dev.schematicannon.table;

import dev.schematicannon.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SchematicTableMenu extends AbstractContainerMenu {
	public final SchematicTableBlockEntity contentHolder;
	private final Slot inputSlot;
	private final Slot outputSlot;

	public SchematicTableMenu(int id, Inventory inv, SchematicTableBlockEntity be) {
		super(ModMenus.SCHEMATIC_TABLE.get(), id);
		this.contentHolder = be;
		Container container = be == null ? new SimpleContainer(2) : be.inventory;

		inputSlot = addSlot(new Slot(container, 0, 21, 59) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return container.canPlaceItem(0, stack);
			}
		});
		outputSlot = addSlot(new Slot(container, 1, 166, 59) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});

		// player Slots
		for (int row = 0; row < 3; ++row)
			for (int col = 0; col < 9; ++col)
				addSlot(new Slot(inv, col + row * 9 + 9, 38 + col * 18, 107 + row * 18));
		for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot)
			addSlot(new Slot(inv, hotbarSlot, 38 + hotbarSlot * 18, 165));
	}

	public static SchematicTableMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
		var pos = buf.readBlockPos();
		var be = inv.player.level().getBlockEntity(pos) instanceof SchematicTableBlockEntity table ? table : null;
		return new SchematicTableMenu(id, inv, be);
	}

	public boolean canWrite() {
		return inputSlot.hasItem() && !outputSlot.hasItem();
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
		if (index < 2)
			moveItemStackTo(stack, 2, slots.size(), true);
		else
			moveItemStackTo(stack, 0, 1, false);
		clickedSlot.setChanged();
		return ItemStack.EMPTY;
	}
}
