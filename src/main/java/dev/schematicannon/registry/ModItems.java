package dev.schematicannon.registry;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.schematic.SchematicAndQuillItem;
import dev.schematicannon.schematic.SchematicItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
	public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Schematicannon.MOD_ID);

	public static final DeferredItem<Item> EMPTY_SCHEMATIC = REGISTER.registerSimpleItem("empty_schematic",
		p -> p.stacksTo(16));
	public static final DeferredItem<SchematicAndQuillItem> SCHEMATIC_AND_QUILL = REGISTER.registerItem(
		"schematic_and_quill", SchematicAndQuillItem::new, p -> p.stacksTo(1));
	public static final DeferredItem<SchematicItem> SCHEMATIC = REGISTER.registerItem("schematic",
		SchematicItem::new, p -> p.stacksTo(1));

	public static final DeferredItem<dev.schematicannon.clipboard.ClipboardItem> CLIPBOARD = REGISTER.registerItem("clipboard",
		dev.schematicannon.clipboard.ClipboardItem::new, p -> p.stacksTo(1));

	public static final DeferredItem<BlockItem> SCHEMATICANNON = REGISTER.registerSimpleBlockItem(ModBlocks.SCHEMATICANNON);
	public static final @org.jspecify.annotations.Nullable DeferredItem<BlockItem> ADVANCED_SCHEMATICANNON =
		ModBlocks.ADVANCED_SCHEMATICANNON == null ? null : REGISTER.registerSimpleBlockItem(ModBlocks.ADVANCED_SCHEMATICANNON);
	public static final DeferredItem<BlockItem> SCHEMATIC_TABLE = REGISTER.registerSimpleBlockItem(ModBlocks.SCHEMATIC_TABLE);
	public static final DeferredItem<BlockItem> CREATIVE_CRATE = REGISTER.registerSimpleBlockItem(ModBlocks.CREATIVE_CRATE,
		p -> p.rarity(Rarity.EPIC));

	private ModItems() {}
}
