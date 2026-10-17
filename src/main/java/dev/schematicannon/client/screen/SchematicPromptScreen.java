package dev.schematicannon.client.screen;

import dev.schematicannon.client.SchematicannonClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Port of Create's SchematicPromptScreen: name the selection, then save (and optionally upload right away). */
public class SchematicPromptScreen extends Screen {
	private static final int W = 200;
	private static final int H = 74;

	private EditBox nameField;

	public SchematicPromptScreen() {
		super(Component.translatable("schematicannon.schematicAndQuill.title"));
	}

	@Override
	protected void init() {
		int x = (width - W) / 2;
		int y = (height - H) / 2;

		nameField = new EditBox(font, x + 10, y + 20, W - 20, 16, Component.empty());
		nameField.setMaxLength(35);
		nameField.setValue("");
		addRenderableWidget(nameField);
		setInitialFocus(nameField);

		addRenderableWidget(Button.builder(Component.translatable("schematicannon.gui.prompt.save"), b -> confirm(false))
			.bounds(x + 10, y + 44, 56, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("schematicannon.gui.prompt.upload"), b -> confirm(true))
			.bounds(x + 70, y + 44, 60, 20)
			.tooltip(Tooltip.create(Component.translatable("schematicannon.schematicAndQuill.convert"))).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
			.bounds(x + 134, y + 44, 56, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		UiDraw.panel(graphics, x, y, W, H);
		graphics.text(font, title, x + 10, y + 7, UiDraw.TEXT, false);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			confirm(false);
			return true;
		}
		return super.keyPressed(event);
	}

	private void confirm(boolean convertImmediately) {
		SchematicannonClient.SCHEMATIC_AND_QUILL_HANDLER.saveSchematic(nameField.getValue(), convertImmediately);
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
