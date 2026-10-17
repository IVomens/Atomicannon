package dev.schematicannon.client.screen;

import dev.schematicannon.client.SchematicHandler;
import dev.schematicannon.client.SchematicannonClient;
import dev.schematicannon.network.ModPackets;
import dev.schematicannon.registry.ModComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Port of Create's SchematicEditScreen: type exact coordinates, rotation and mirror for the held schematic. */
public class SchematicEditScreen extends Screen {
	private static final int W = 210;
	private static final int H = 130;

	private EditBox xInput;
	private EditBox yInput;
	private EditBox zInput;
	private Rotation rotation = Rotation.NONE;
	private Mirror mirror = Mirror.NONE;
	private Button rotationButton;
	private Button mirrorButton;

	public SchematicEditScreen() {
		super(Component.translatable("schematicannon.schematic.position"));
	}

	@Override
	protected void init() {
		int x = (width - W) / 2;
		int y = (height - H) / 2;

		SchematicHandler handler = SchematicannonClient.SCHEMATIC_HANDLER;
		StructurePlaceSettings settings = handler.getTransformation().toSettings();
		BlockPos anchor = handler.isDeployed() ? handler.getTransformation().getAnchor() : minecraft.player.blockPosition();
		rotation = settings.getRotation();
		mirror = settings.getMirror();

		xInput = coordinate(x + 70, y + 22, anchor.getX());
		yInput = coordinate(x + 115, y + 22, anchor.getY());
		zInput = coordinate(x + 160, y + 22, anchor.getZ());

		rotationButton = addRenderableWidget(Button.builder(rotationLabel(), b -> {
			rotation = Rotation.values()[(rotation.ordinal() + 1) % Rotation.values().length];
			b.setMessage(rotationLabel());
		}).bounds(x + 70, y + 46, 130, 20).build());

		mirrorButton = addRenderableWidget(Button.builder(mirrorLabel(), b -> {
			mirror = Mirror.values()[(mirror.ordinal() + 1) % Mirror.values().length];
			b.setMessage(mirrorLabel());
		}).bounds(x + 70, y + 70, 130, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
			applyChanges();
			onClose();
		}).bounds(x + 70, y + 100, 64, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
			.bounds(x + 136, y + 100, 64, 20).build());
	}

	private EditBox coordinate(int x, int y, int value) {
		EditBox box = new EditBox(font, x, y, 40, 16, Component.empty());
		box.setValue(String.valueOf(value));
		box.setMaxLength(9);
		return addRenderableWidget(box);
	}

	private Component rotationLabel() {
		String key = switch (rotation) {
			case NONE -> "none";
			case CLOCKWISE_90 -> "cw90";
			case CLOCKWISE_180 -> "cw180";
			case COUNTERCLOCKWISE_90 -> "cw270";
		};
		return Component.translatable("schematicannon.schematic.rotation." + key);
	}

	private Component mirrorLabel() {
		String key = switch (mirror) {
			case NONE -> "none";
			case LEFT_RIGHT -> "leftRight";
			case FRONT_BACK -> "frontBack";
		};
		return Component.translatable("schematicannon.schematic.mirror." + key);
	}

	private void applyChanges() {
		ItemStack item = minecraft.player.getMainHandItem();
		if (!item.has(ModComponents.SCHEMATIC_FILE.get()))
			return;
		BlockPos anchor;
		try {
			anchor = new BlockPos(Integer.parseInt(xInput.getValue()), Integer.parseInt(yInput.getValue()),
				Integer.parseInt(zInput.getValue()));
		} catch (NumberFormatException e) {
			return;
		}
		item.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		item.set(ModComponents.SCHEMATIC_ANCHOR.get(), anchor);
		item.set(ModComponents.SCHEMATIC_ROTATION.get(), rotation);
		item.set(ModComponents.SCHEMATIC_MIRROR.get(), mirror);
		ClientPacketDistributor.sendToServer(new ModPackets.SchematicSync(minecraft.player.getInventory().getSelectedSlot(), true,
			anchor, rotation, mirror));
		SchematicannonClient.SCHEMATIC_HANDLER.reload();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		UiDraw.panel(graphics, x, y, W, H);
		String file = SchematicannonClient.SCHEMATIC_HANDLER.getCurrentSchematicName();
		graphics.text(font, Component.literal(file), x + 10, y + 7, UiDraw.ACCENT, false);
		graphics.text(font, Component.translatable("schematicannon.schematic.position"), x + 10, y + 26, UiDraw.TEXT, false);
		graphics.text(font, Component.translatable("schematicannon.schematic.rotation"), x + 10, y + 52, UiDraw.TEXT, false);
		graphics.text(font, Component.translatable("schematicannon.schematic.mirror"), x + 10, y + 76, UiDraw.TEXT, false);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
