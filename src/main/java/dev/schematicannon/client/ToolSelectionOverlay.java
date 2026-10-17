package dev.schematicannon.client;

import java.util.List;
import java.util.function.Consumer;

import dev.schematicannon.client.screen.UiDraw;
import dev.schematicannon.client.tools.ToolType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Port of Create's ToolSelectionScreen. It is drawn as part of the HUD (never as a real screen) so the
 * player keeps control while holding the tool-menu key, exactly like Create.
 */
public class ToolSelectionOverlay {
	private final List<ToolType> tools;
	private final Consumer<ToolType> callback;
	public boolean focused;
	private float yOffset;
	protected int selection;

	public ToolSelectionOverlay(List<ToolType> tools, Consumer<ToolType> callback) {
		this.tools = tools;
		this.callback = callback;
		focused = false;
		yOffset = 0;
		selection = 0;
		callback.accept(tools.get(selection));
	}

	public void setSelectedElement(ToolType tool) {
		if (!tools.contains(tool))
			return;
		selection = tools.indexOf(tool);
	}

	public void cycle(int direction) {
		selection += (direction < 0) ? 1 : -1;
		selection = (selection + tools.size()) % tools.size();
	}

	public void update() {
		if (focused)
			yOffset += (10 - yOffset) * .1f;
		else
			yOffset *= .9f;
	}

	public void onClose() {
		callback.accept(tools.get(selection));
	}

	public void renderPassive(GuiGraphicsExtractor graphics, float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();

		int x = (w - (tools.size() * 50 + 16)) / 2 + 8;
		int y = h - 74 - (int) yOffset;

		// textured bar: left cap, one 50px tile per tool, right cap
		UiDraw.icons(graphics, x - 8, y - 4, 0, 96, 8, 38);
		for (int i = 0; i < tools.size(); i++)
			UiDraw.icons(graphics, x + i * 50, y - 4, 8, 96, 50, 38);
		UiDraw.icons(graphics, x + tools.size() * 50, y - 4, 58, 96, 8, 38);

		for (int i = 0; i < tools.size(); i++) {
			ToolType tool = tools.get(i);
			int tx = x + i * 50 + 8;
			boolean selected = i == selection;
			if (selected)
				UiDraw.icons(graphics, tx, y, 0, 136, 34, 30);
			UiDraw.icon(graphics, tx + 9, y + 3, tool.getIconIndex());
			if (selected || focused)
				graphics.centeredText(font, tool.getDisplayName(), tx + 17, y + 20, selected ? 0xFFFFFFFF : 0xFF3B3226);
		}

		if (!focused) {
			Component hint = Component.translatable("schematicannon.gui.toolmenu.focusKey",
				ModKeys.TOOL_MENU.getTranslatedKeyMessage().getString());
			graphics.centeredText(font, hint, w / 2, y - 14, 0xFFCCDDFF);
			return;
		}

		Component cycle = Component.translatable("schematicannon.gui.toolmenu.cycle");
		graphics.centeredText(font, cycle, w / 2, y - 14, 0xFFCCDDFF);

		List<Component> description = tools.get(selection).getDescription();
		int dy = y - 18 - description.size() * 10;
		for (Component line : description) {
			graphics.centeredText(font, line, w / 2, dy, 0xFFEEEEEE);
			dy += 10;
		}
	}
}
