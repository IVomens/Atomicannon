package dev.schematicannon.client.screen;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** 18x18 textured button showing one 16x16 icon from the mod's icon sheet. */
public class IconButton extends AbstractButton {
	private final int icon;
	private final Runnable action;
	private BooleanSupplier selected = () -> false;

	public IconButton(int x, int y, int icon, Component tooltip, Runnable action) {
		super(x, y, 18, 18, tooltip);
		this.icon = icon;
		this.action = action;
		setTooltip(Tooltip.create(tooltip));
	}

	/** A selected button is drawn pressed in (e.g. the active replace mode). */
	public IconButton selectedWhen(BooleanSupplier selected) {
		this.selected = selected;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int state = !active ? 3 : selected.getAsBoolean() ? 2 : isHoveredOrFocused() ? 1 : 0;
		UiDraw.icons(graphics, getX(), getY(), state * 18, 64, 18, 18);
		int offset = state == 2 ? 2 : 1;
		UiDraw.icon(graphics, getX() + offset, getY() + offset, icon);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
