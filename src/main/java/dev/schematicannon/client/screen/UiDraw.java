package dev.schematicannon.client.screen;

import dev.schematicannon.Schematicannon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws the mod's GUI textures (all original art, see tools/art). */
public final class UiDraw {
	public static final Identifier SCHEMATICANNON = Schematicannon.id("textures/gui/schematicannon.png");
	public static final Identifier SCHEMATIC_TABLE = Schematicannon.id("textures/gui/schematic_table.png");
	public static final Identifier ICONS = Schematicannon.id("textures/gui/icons.png");
	public static final Identifier PANEL = Schematicannon.id("textures/gui/panel.png");

	public static final int TEXT = 0xFF3B3226;
	public static final int PLATE_TEXT = 0xFF3D2A0E;
	public static final int SCREEN_TEXT = 0xFFB8E0FF;
	public static final int ACCENT = 0xFF6886C5;

	// icon indices in icons.png
	public static final int PLAY = 0, PAUSE = 1, STOP = 2, REPLACE_NONE = 3, REPLACE_SOLID = 4, REPLACE_ANY = 5,
		REPLACE_EMPTY = 6, SKIP_MISSING = 7, PROTECT = 8, UPLOAD = 9, REFRESH = 10, FOLDER = 11, PREV = 12, NEXT = 13,
		TOOLS = 16;

	public static void blit(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, 256, 256);
	}

	public static void icons(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
		blit(g, ICONS, x, y, u, v, w, h);
	}

	public static void icon(GuiGraphicsExtractor g, int x, int y, int index) {
		icons(g, x, y, (index % 16) * 16, (index / 16) * 16, 16, 16);
	}

	/** Lamp under a toggle; (x, y) is the top-left of the 6x2 light. */
	public static void lamp(GuiGraphicsExtractor g, int x, int y, boolean on) {
		icons(g, x, y, on ? 8 : 0, 88, 6, 2);
	}

	/** Brass-framed panel of any size, nine-sliced from the 64x64 panel texture. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int c = 10;
		blit(g, PANEL, x, y, 0, 0, c, c, 64);
		blit(g, PANEL, x + w - c, y, 64 - c, 0, c, c, 64);
		blit(g, PANEL, x, y + h - c, 0, 64 - c, c, c, 64);
		blit(g, PANEL, x + w - c, y + h - c, 64 - c, 64 - c, c, c, 64);
		for (int i = x + c; i < x + w - c; i += 40) {
			int len = Math.min(40, x + w - c - i);
			blit(g, PANEL, i, y, 12, 0, len, c, 64);
			blit(g, PANEL, i, y + h - c, 12, 64 - c, len, c, 64);
		}
		for (int j = y + c; j < y + h - c; j += 40) {
			int len = Math.min(40, y + h - c - j);
			blit(g, PANEL, x, j, 0, 12, c, len, 64);
			blit(g, PANEL, x + w - c, j, 64 - c, 12, c, len, 64);
			for (int i = x + c; i < x + w - c; i += 40)
				blit(g, PANEL, i, j, 12, 12, Math.min(40, x + w - c - i), len, 64);
		}
	}

	private static void blit(GuiGraphicsExtractor g, Identifier tex, int x, int y, int u, int v, int w, int h, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, size, size);
	}

	/** Coloured fill inside a recessed bar baked into a background texture. */
	public static void barFill(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int color) {
		int filled = (int) ((w - 2) * Math.max(0, Math.min(1, progress)));
		if (filled <= 0)
			return;
		g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
		g.fill(x + 1, y + 1, x + 1 + filled, y + 2, (color & 0x00FFFFFF) | 0x60000000 | 0xFFFFFF);
	}

	private UiDraw() {}
}
