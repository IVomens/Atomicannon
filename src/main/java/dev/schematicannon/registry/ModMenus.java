package dev.schematicannon.registry;

import java.util.function.Supplier;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.SchematicannonMenu;
import dev.schematicannon.table.SchematicTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
	public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, Schematicannon.MOD_ID);

	public static final Supplier<MenuType<SchematicannonMenu>> SCHEMATICANNON = REGISTER.register("schematicannon",
		() -> IMenuTypeExtension.create(SchematicannonMenu::fromNetwork));
	public static final Supplier<MenuType<SchematicTableMenu>> SCHEMATIC_TABLE = REGISTER.register("schematic_table",
		() -> IMenuTypeExtension.create(SchematicTableMenu::fromNetwork));

	private ModMenus() {}
}
