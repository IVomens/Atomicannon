package dev.schematicannon.registry;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.CreativeCrateBlock;
import dev.schematicannon.cannon.SchematicannonBlock;
import dev.schematicannon.table.SchematicTableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
	public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Schematicannon.MOD_ID);

	public static final DeferredBlock<SchematicannonBlock> SCHEMATICANNON = REGISTER.registerBlock("schematicannon",
		SchematicannonBlock::new,
		p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion()
			.requiresCorrectToolForDrops());

	/** Runs on HBM NTM power; only registered when NTM is installed. */
	public static final @org.jspecify.annotations.Nullable DeferredBlock<SchematicannonBlock> ADVANCED_SCHEMATICANNON =
		dev.schematicannon.compat.NtmCompat.installed() ? REGISTER.registerBlock("advanced_schematicannon",
			p -> new SchematicannonBlock(p, true),
			p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()
				.requiresCorrectToolForDrops()) : null;

	public static final DeferredBlock<SchematicTableBlock> SCHEMATIC_TABLE = REGISTER.registerBlock("schematic_table",
		SchematicTableBlock::new,
		p -> p.mapColor(MapColor.PODZOL).strength(2.5f).sound(SoundType.WOOD).noOcclusion()
			.pushReaction(PushReaction.BLOCK));

	public static final DeferredBlock<CreativeCrateBlock> CREATIVE_CRATE = REGISTER.registerBlock("creative_crate",
		CreativeCrateBlock::new,
		p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0f, 3600000.0f).sound(SoundType.WOOD).noLootTable());

	private ModBlocks() {}
}
