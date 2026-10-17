package dev.schematicannon.schematic;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import dev.schematicannon.client.ClientHooks;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public class SchematicItem extends Item {

	public SchematicItem(Properties properties) {
		super(properties);
	}

	public static ItemStack create(Level level, String schematic, String owner) {
		ItemStack blueprint = new ItemStack(ModItems.SCHEMATIC.get());
		blueprint.set(ModComponents.SCHEMATIC_DEPLOYED.get(), false);
		blueprint.set(ModComponents.SCHEMATIC_OWNER.get(), owner);
		blueprint.set(ModComponents.SCHEMATIC_FILE.get(), schematic);
		blueprint.set(ModComponents.SCHEMATIC_ANCHOR.get(), BlockPos.ZERO);
		blueprint.set(ModComponents.SCHEMATIC_ROTATION.get(), Rotation.NONE);
		blueprint.set(ModComponents.SCHEMATIC_MIRROR.get(), Mirror.NONE);
		writeSize(level, blueprint);
		return blueprint;
	}

	public static void writeSize(Level level, ItemStack blueprint) {
		SchematicFile file = loadSchematic(level, blueprint);
		blueprint.set(ModComponents.SCHEMATIC_BOUNDS.get(), file.size());
	}

	public static StructurePlaceSettings getSettings(ItemStack blueprint) {
		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.setRotation(blueprint.getOrDefault(ModComponents.SCHEMATIC_ROTATION.get(), Rotation.NONE));
		settings.setMirror(blueprint.getOrDefault(ModComponents.SCHEMATIC_MIRROR.get(), Mirror.NONE));
		return settings;
	}

	/**
	 * Server side: reads {@code schematics/uploaded/<owner>/<file>}. Client side: reads {@code schematics/<file>}.
	 */
	public static SchematicFile loadSchematic(Level level, ItemStack blueprint) {
		String owner = blueprint.get(ModComponents.SCHEMATIC_OWNER.get());
		String schematic = blueprint.get(ModComponents.SCHEMATIC_FILE.get());

		if (owner == null || schematic == null || !schematic.endsWith(".nbt"))
			return SchematicFile.EMPTY;

		Path dir;
		Path file;
		try {
			if (!level.isClientSide()) {
				dir = SchematicPaths.UPLOADED_SCHEMATICS_DIR;
				file = Paths.get(owner, schematic);
			} else {
				dir = SchematicPaths.SCHEMATICS_DIR;
				file = Paths.get(schematic);
			}
		} catch (RuntimeException e) {
			return SchematicFile.EMPTY;
		}

		Path path = dir.resolve(file).normalize();
		if (!path.startsWith(dir) || !java.nio.file.Files.isRegularFile(path))
			return SchematicFile.EMPTY;

		return SchematicFile.read(path, level.holderLookup(Registries.BLOCK));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		String file = stack.get(ModComponents.SCHEMATIC_FILE.get());
		if (file != null)
			builder.accept(Component.literal(file).withStyle(ChatFormatting.GOLD));
		else
			builder.accept(Component.translatable("schematicannon.schematic.invalid").withStyle(ChatFormatting.RED));
		super.appendHoverText(stack, context, display, builder, flag);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() != null && onItemUse(context.getPlayer(), context.getHand()))
			return InteractionResult.SUCCESS;
		return super.useOn(context);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (onItemUse(player, hand))
			return InteractionResult.SUCCESS;
		return super.use(level, player, hand);
	}

	private boolean onItemUse(Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND)
			return false;
		if (!player.getItemInHand(hand).has(ModComponents.SCHEMATIC_FILE.get()))
			return false;
		if (player.level().isClientSide())
			ClientHooks.openSchematicEditScreen();
		return true;
	}
}
