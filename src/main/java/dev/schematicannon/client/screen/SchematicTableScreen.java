package dev.schematicannon.client.screen;

import java.util.List;

import dev.schematicannon.client.SchematicannonClient;
import dev.schematicannon.schematic.SchematicPaths;
import dev.schematicannon.table.SchematicTableMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

/** Port of Create's SchematicTableScreen: pick a local schematic file and write it onto an Empty Schematic. */
public class SchematicTableScreen extends AbstractContainerScreen<SchematicTableMenu> {
	private static final int INV_X = 30, INV_Y = 100;
	// 3D preview panel to the right of the table
	private static final int PREVIEW_X = 218, PREVIEW_W = 120, PREVIEW_H = 190;
	private static final int VIEW_X = 226, VIEW_Y = 9, VIEW_W = 104, VIEW_H = 118;

	private float yaw = 30, pitch = 25;
	private boolean dragging;
	private long lastFrame = net.minecraft.util.Util.getMillis();

	private int selected;
	private float progress;
	private float chasingProgress;
	private float lastChasingProgress;
	private IconButton uploadButton;
	private IconButton prevButton;
	private IconButton nextButton;

	public SchematicTableScreen(SchematicTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PREVIEW_X + PREVIEW_W, INV_Y + 90);
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos;
		int y = topPos;

		prevButton = addRenderableWidget(new IconButton(x + 8, y + 23, UiDraw.PREV,
			Component.translatable("schematicannon.gui.schematicTable.previous"), () -> cycle(-1)));
		nextButton = addRenderableWidget(new IconButton(x + 188, y + 23, UiDraw.NEXT,
			Component.translatable("schematicannon.gui.schematicTable.next"), () -> cycle(1)));
		uploadButton = addRenderableWidget(new IconButton(x + 98, y + 58, UiDraw.UPLOAD,
			Component.translatable("schematicannon.gui.schematicTable.upload"), this::upload));
		addRenderableWidget(new IconButton(x + 168, y + 4, UiDraw.REFRESH,
			Component.translatable("schematicannon.gui.schematicTable.refresh"), () -> {
			SchematicannonClient.SCHEMATIC_SENDER.refresh();
			dev.schematicannon.client.render.SchematicPreviewRenderer.clearCache();
			selected = 0;
		}));
		addRenderableWidget(new IconButton(x + 188, y + 4, UiDraw.FOLDER,
			Component.translatable("schematicannon.gui.schematicTable.open_folder"), () -> {
			SchematicPaths.createFolderIfMissing(SchematicPaths.SCHEMATICS_DIR);
			Util.getPlatform().openFile(SchematicPaths.SCHEMATICS_DIR.toFile());
		}));
	}

	private void cycle(int dir) {
		List<Component> files = SchematicannonClient.SCHEMATIC_SENDER.getAvailableSchematics();
		if (files.isEmpty())
			return;
		selected = Math.floorMod(selected + dir, files.size());
	}

	private void upload() {
		List<Component> files = SchematicannonClient.SCHEMATIC_SENDER.getAvailableSchematics();
		if (menu.canWrite() && !files.isEmpty() && menu.contentHolder != null && !menu.contentHolder.isUploading) {
			lastChasingProgress = chasingProgress = progress = 0;
			SchematicannonClient.SCHEMATIC_SENDER.startNewUpload(files.get(Math.min(selected, files.size() - 1)).getString());
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (isHovering(28, 24, 158, 16, x, y)) {
			cycle(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		var be = menu.contentHolder;
		boolean uploading = be != null && be.isUploading;
		if (uploading) {
			lastChasingProgress = chasingProgress;
			progress = be.uploadingProgress;
			chasingProgress += (progress - chasingProgress) * .5f;
		} else {
			lastChasingProgress = chasingProgress = progress = 0;
		}
		List<Component> files = SchematicannonClient.SCHEMATIC_SENDER.getAvailableSchematics();
		uploadButton.active = !uploading && menu.canWrite() && !files.isEmpty();
		prevButton.active = nextButton.active = files.size() > 1;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		UiDraw.blit(graphics, UiDraw.SCHEMATIC_TABLE, x, y, 0, 0, 214, 92);
		UiDraw.blit(graphics, UiDraw.SCHEMATIC_TABLE, x + INV_X, y + INV_Y, 0, 150, 178, 90);

		List<Component> files = SchematicannonClient.SCHEMATIC_SENDER.getAvailableSchematics();
		String name = files.isEmpty()
			? Component.translatable("schematicannon.gui.schematicTable.noSchematics").getString()
			: files.get(Math.min(selected, files.size() - 1)).getString();
		if (name.endsWith(".nbt"))
			name = name.substring(0, name.length() - 4);
		String text = font.plainSubstrByWidth(name, 150);
		graphics.text(font, text, x + 107 - font.width(text) / 2, y + 28, files.isEmpty() ? 0xFFA9C4EA : 0xFFFFFFFF, true);
		if (files.size() > 1) {
			String count = (Math.min(selected, files.size() - 1) + 1) + "/" + files.size();
			graphics.text(font, count, x + 184 - font.width(count), y + 44, UiDraw.TEXT, false);
		}

		float bar = lastChasingProgress + (chasingProgress - lastChasingProgress) * a;
		UiDraw.barFill(graphics, x + 46, y + 80, 116, 5, bar, 0xFF6FA0E0);

		extractPreview(graphics, files.isEmpty() ? null : files.get(Math.min(selected, files.size() - 1)).getString());
	}

	/** Turning 3D model of the selected file. */
	private void extractPreview(GuiGraphicsExtractor graphics, String file) {
		int x = leftPos + PREVIEW_X, y = topPos;
		UiDraw.panel(graphics, x, y, PREVIEW_W, PREVIEW_H);
		int vx = leftPos + VIEW_X, vy = topPos + VIEW_Y;
		graphics.fill(vx - 1, vy - 1, vx + VIEW_W + 1, vy + VIEW_H + 1, 0xFF3A3631);
		graphics.fill(vx, vy, vx + VIEW_W, vy + VIEW_H, 0xFF2B3139);
		graphics.fill(vx, vy + VIEW_H, vx + VIEW_W + 1, vy + VIEW_H + 1, 0xFFD6D1C6);

		long now = net.minecraft.util.Util.getMillis();
		if (!dragging)
			yaw += (now - lastFrame) * 0.03f;
		lastFrame = now;

		var preview = file == null ? null : dev.schematicannon.client.render.SchematicPreviewRenderer.load(file);
		int textY = vy + VIEW_H + 6;
		if (preview == null) {
			Component msg = Component.translatable(file == null ? "schematicannon.gui.schematicTable.noPreview"
				: "schematicannon.gui.schematicTable.previewUnavailable");
			graphics.textWithWordWrap(font, msg, vx + 4, vy + VIEW_H / 2 - 8, VIEW_W - 8, 0xFFA9C4EA, false);
			return;
		}
		var size = preview.size();
		double span = Math.max(Math.sqrt(size.getX() * size.getX() + size.getZ() * size.getZ()), size.getY() * 1.2);
		float scale = (float) (Math.min(VIEW_W, VIEW_H) / Math.max(1, span * 1.15));
		graphics.submitPictureInPictureRenderState(new dev.schematicannon.client.render.SchematicPreviewRenderer.State(
			preview, yaw, pitch, vx, vy, vx + VIEW_W, vy + VIEW_H, scale, null));

		graphics.text(font, size.getX() + " x " + size.getY() + " x " + size.getZ(), vx, textY, UiDraw.TEXT, false);
		graphics.text(font, Component.translatable("schematicannon.gui.schematicTable.blocks", preview.blockCount()),
			vx, textY + 11, UiDraw.TEXT, false);
		graphics.text(font, Component.translatable("schematicannon.gui.schematicTable.dragToRotate"), vx, textY + 30,
			0xFF7A7266, false);
	}

	private boolean overPreview(double mx, double my) {
		return mx >= leftPos + VIEW_X && mx < leftPos + VIEW_X + VIEW_W && my >= topPos + VIEW_Y && my < topPos + VIEW_Y + VIEW_H;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (overPreview(event.x(), event.y())) {
			dragging = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			yaw += (float) dx * 2;
			pitch = net.minecraft.util.Mth.clamp(pitch + (float) dy * 2, -10, 80);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		graphics.text(font, title, 10, 6, UiDraw.PLATE_TEXT, false);
		var be = menu.contentHolder;
		if (be != null && be.isUploading)
			graphics.text(font, Component.translatable("schematicannon.gui.schematicTable.uploading"), 28, 44, UiDraw.ACCENT, false);
		else if (menu.getSlot(1).hasItem())
			graphics.text(font, Component.translatable("schematicannon.gui.schematicTable.finished"), 28, 44, 0xFF2F7A2F, false);
		else
			graphics.text(font, Component.translatable("schematicannon.gui.schematicTable.availableSchematics"), 28, 44,
				UiDraw.TEXT, false);
	}
}
