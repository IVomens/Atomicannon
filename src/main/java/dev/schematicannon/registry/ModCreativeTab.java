package dev.schematicannon.registry;

import dev.schematicannon.Schematicannon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {
	public static final DeferredRegister<CreativeModeTab> REGISTER =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Schematicannon.MOD_ID);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = REGISTER.register("main",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.schematicannon"))
			.icon(() -> ModItems.SCHEMATICANNON.get().getDefaultInstance())
			.displayItems((params, output) -> {
				output.accept(ModItems.SCHEMATICANNON.get());
				if (ModItems.ADVANCED_SCHEMATICANNON != null)
					output.accept(ModItems.ADVANCED_SCHEMATICANNON.get());
				output.accept(ModItems.SCHEMATIC_TABLE.get());
				output.accept(ModItems.EMPTY_SCHEMATIC.get());
				output.accept(ModItems.SCHEMATIC_AND_QUILL.get());
				output.accept(ModItems.CLIPBOARD.get());
				output.accept(ModItems.CREATIVE_CRATE.get());
			})
			.build());

	private ModCreativeTab() {}
}
