package dev.schematicannon.schematic;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides which block entity data a schematic is allowed to carry into the world.
 * <p>
 * Like Create, the default is <b>no data at all</b>: chests arrive empty and machines arrive freshly built,
 * so nothing built in creative can be smuggled into survival. Server owners can opt blocks in with the
 * {@code schematicannon:safe_nbt} block tag. Signs and banners are allowed by default (signs have
 * click-commands stripped).
 */
public final class NbtSafety {
	public static final TagKey<Block> SAFE_NBT = TagKey.create(Registries.BLOCK, Schematicannon.id("safe_nbt"));

	private static final String[] FORBIDDEN_KEYS = {
		"Items", "Item", "item", "items", "inventory", "Inventory", "RecordItem", "Book", "LootTable", "LootTableSeed",
		"SpawnData", "SpawnPotentials", "Command", "fluids", "fluid", "Fluid", "Tank", "tanks", "power", "Power",
		"energy", "Energy", "heat", "Heat", "fuel", "Fuel", "Bees", "flower_pos"
	};

	/** @return the data to place, or null to place the block with fresh, empty data. */
	public static @Nullable CompoundTag prepare(BlockState state, @Nullable CompoundTag data) {
		if (data == null)
			return null;
		boolean sign = state.is(BlockTags.ALL_SIGNS);
		if (!sign && !state.is(BlockTags.BANNERS) && !state.is(SAFE_NBT))
			return null;

		CompoundTag copy = data.copy();
		for (String key : FORBIDDEN_KEYS)
			copy.remove(key);
		copy.remove("x");
		copy.remove("y");
		copy.remove("z");

		if (sign && containsClickEvent(copy))
			return null;
		return copy;
	}

	private static boolean containsClickEvent(CompoundTag tag) {
		String text = tag.toString();
		return text.contains("click_event") || text.contains("clickEvent");
	}

	private NbtSafety() {}
}
