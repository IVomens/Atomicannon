package dev.schematicannon.registry;

import java.util.function.Supplier;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.table.SchematicTableBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

public final class ModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> REGISTER =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Schematicannon.MOD_ID);

	/** Shared by the Basic and Advanced cannons; the block decides whether it runs on gunpowder or NTM power. */
	public static final Supplier<BlockEntityType<SchematicannonBlockEntity>> SCHEMATICANNON = REGISTER.register(
		"schematicannon", () -> ModBlocks.ADVANCED_SCHEMATICANNON == null
			? new BlockEntityType<>(SchematicannonBlockEntity::new, ModBlocks.SCHEMATICANNON.get())
			: new BlockEntityType<>(SchematicannonBlockEntity::new, ModBlocks.SCHEMATICANNON.get(),
				ModBlocks.ADVANCED_SCHEMATICANNON.get()));

	public static final Supplier<BlockEntityType<SchematicTableBlockEntity>> SCHEMATIC_TABLE = REGISTER.register(
		"schematic_table", () -> new BlockEntityType<>(SchematicTableBlockEntity::new, ModBlocks.SCHEMATIC_TABLE.get()));

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.Item.BLOCK, SCHEMATICANNON.get(),
			(be, side) -> VanillaContainerWrapper.of(be.inventory));
		event.registerBlockEntity(Capabilities.Item.BLOCK, SCHEMATIC_TABLE.get(),
			(be, side) -> VanillaContainerWrapper.of(be.inventory));
		dev.schematicannon.compat.NtmCompat.registerCapabilities(event, SCHEMATICANNON.get());
	}

	private ModBlockEntities() {}
}
