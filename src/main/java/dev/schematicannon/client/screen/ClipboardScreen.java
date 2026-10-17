package dev.schematicannon.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.clipboard.ClipboardContent;
import dev.schematicannon.clipboard.ClipboardItem;
import dev.schematicannon.network.ModPackets;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.registry.ModItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Read and edit a clipboard: a checkbox and a line of text per entry, several pages. Saved when closed. */
public class ClipboardScreen extends Screen {
	public static final Identifier TEXTURE = Schematicannon.id("textures/gui/clipboard.png");
	private static final int W = 176, H = 220;
	private static final int ROW_Y = 36, ROW_H = 16, CHECK_X = 20, ICON_X = 33;
	private static final int TEXT = 0xFF3B3226, TEXT_DONE = 0xFF8A8270;

	private final InteractionHand hand;
	private final List<List<ClipboardContent.Entry>> pages;
	private int page;
	private final List<EditBox> rows = new ArrayList<>();
	private IconButton prev;
	private IconButton next;

	public ClipboardScreen(InteractionHand hand) {
		super(Component.translatable("item.schematicannon.clipboard"));
		this.hand = hand;
		ClipboardContent content = ClipboardItem.content(heldStack());
		this.pages = content.mutablePages();
		this.page = Math.max(0, Math.min(content.page(), pages.size() - 1));
	}

	private ItemStack heldStack() {
		return minecraftOrNull() == null ? ItemStack.EMPTY : minecraftOrNull().player.getItemInHand(hand);
	}

	private static net.minecraft.client.Minecraft minecraftOrNull() {
		return net.minecraft.client.Minecraft.getInstance();
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return (height - H) / 2;
	}

	private List<ClipboardContent.Entry> entries() {
		return pages.get(page);
	}

	@Override
	protected void init() {
		buildRows(-1);
	}

	private void buildRows(int focusRow) {
		clearWidgets();
		rows.clear();
		int x = left(), y = top();

		List<ClipboardContent.Entry> entries = entries();
		int count = Math.min(entries.size() + 1, ClipboardContent.ENTRIES_PER_PAGE);
		for (int i = 0; i < count; i++) {
			int index = i;
			boolean hasIcon = i < entries.size() && !entries.get(i).icon().isEmpty();
			int textX = x + (hasIcon ? ICON_X + 18 : ICON_X);
			EditBox box = new EditBox(font, textX, y + ROW_Y + i * ROW_H + 4, x + 158 - textX, 10, Component.empty());
			box.setBordered(false);
			box.setMaxLength(ClipboardContent.MAX_TEXT);
			box.setTextShadow(false);
			if (i < entries.size()) {
				box.setValue(entries.get(i).text());
				box.moveCursorToStart(false); // show the start of long names, not the end
				box.setTextColor(entries.get(i).checked() ? TEXT_DONE : TEXT);
			} else {
				box.setTextColor(TEXT); // placeholder is drawn by the screen (crisp, no shadow)
			}
			box.setResponder(text -> {
				List<ClipboardContent.Entry> list = entries();
				if (index < list.size())
					list.set(index, list.get(index).withText(text));
				else if (!text.isEmpty())
					list.add(ClipboardContent.Entry.text(text));
			});
			rows.add(addRenderableWidget(box));
		}

		prev = addRenderableWidget(new IconButton(x + 16, y + H - 26, UiDraw.PREV,
			Component.translatable("schematicannon.clipboard.previous"), () -> turn(-1)));
		next = addRenderableWidget(new IconButton(x + W - 34, y + H - 26, UiDraw.NEXT,
			Component.translatable("schematicannon.clipboard.next"), () -> turn(1)));
		prev.active = page > 0;
		next.active = page < ClipboardContent.MAX_PAGES - 1 && (page < pages.size() - 1 || !entries().isEmpty());

		if (focusRow >= 0 && focusRow < rows.size()) {
			setFocused(rows.get(focusRow));
			rows.get(focusRow).setFocused(true);
		}
	}

	private void turn(int dir) {
		cleanPage();
		page += dir;
		if (page >= pages.size())
			pages.add(new ArrayList<>());
		page = Math.max(0, Math.min(page, pages.size() - 1));
		buildRows(-1);
	}

	/** Plain entries left blank are removed (entries written by the Schematicannon stay). */
	private void cleanPage() {
		entries().removeIf(e -> e.text().isBlank() && e.icon().isEmpty());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			int focused = rows.indexOf(getFocused());
			if (focused >= 0) {
				cleanPage();
				if (focused + 1 >= ClipboardContent.ENTRIES_PER_PAGE && entries().size() >= ClipboardContent.ENTRIES_PER_PAGE) {
					turn(1);
					buildRows(0);
				} else
					buildRows(Math.min(focused + 1, entries().size()));
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int x = left(), y = top();
		List<ClipboardContent.Entry> entries = entries();
		for (int i = 0; i < entries.size(); i++) {
			int cy = y + ROW_Y + i * ROW_H + 3;
			if (event.x() >= x + CHECK_X - 1 && event.x() < x + CHECK_X + 11 && event.y() >= cy - 1 && event.y() < cy + 11) {
				ClipboardContent.Entry e = entries.get(i);
				entries.set(i, e.withChecked(!e.checked()));
				rows.get(i).setTextColor(!e.checked() ? TEXT_DONE : TEXT);
				minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, .3f));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, W, H, 256, 256);

		List<ClipboardContent.Entry> entries = entries();
		for (int i = 0; i < Math.min(entries.size() + 1, ClipboardContent.ENTRIES_PER_PAGE); i++) {
			int ry = y + ROW_Y + i * ROW_H;
			if (i >= entries.size()) {
				graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, x + CHECK_X, ry + 3, 200, 0, 10, 10, 256, 256);
				continue;
			}
			ClipboardContent.Entry e = entries.get(i);
			graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, x + CHECK_X, ry + 3,
				e.checked() ? 190 : 180, 0, 10, 10, 256, 256);
			if (!e.icon().isEmpty())
				graphics.item(e.icon(), x + ICON_X, ry);
		}

		Component pageText = Component.translatable("schematicannon.clipboard.page", page + 1, pages.size());
		graphics.text(font, pageText, x + W / 2 - font.width(pageText) / 2, y + H - 21, TEXT, false);

		// placeholder on the blank "new entry" line, drawn without a shadow so it stays readable on paper
		if (entries.size() < rows.size()) {
			EditBox blank = rows.get(rows.size() - 1);
			if (blank.getValue().isEmpty() && !blank.isFocused())
				graphics.text(font, Component.translatable("schematicannon.clipboard.newEntry"), blank.getX(), blank.getY(),
					0xFFA89C82, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);

		// tooltip for item entries
		for (int i = 0; i < entries.size(); i++) {
			ClipboardContent.Entry e = entries.get(i);
			int ry = y + ROW_Y + i * ROW_H;
			if (!e.icon().isEmpty() && mouseX >= x + ICON_X && mouseX < x + ICON_X + 16 && mouseY >= ry && mouseY < ry + 16)
				graphics.setTooltipForNextFrame(font, e.icon(), mouseX, mouseY);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		extractTransparentBackground(graphics);
	}

	@Override
	public void removed() {
		cleanPage();
		while (pages.size() > 1 && pages.get(pages.size() - 1).isEmpty())
			pages.remove(pages.size() - 1);
		page = Math.min(page, pages.size() - 1);
		ClipboardContent content = new ClipboardContent(pages, page).sanitized();
		ItemStack stack = heldStack();
		if (stack.is(ModItems.CLIPBOARD.get())) {
			stack.set(ModComponents.CLIPBOARD.get(), content); // instant local feedback; the server applies its own copy
			ClientPacketDistributor.sendToServer(new ModPackets.EditClipboard(hand == InteractionHand.OFF_HAND, content));
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
