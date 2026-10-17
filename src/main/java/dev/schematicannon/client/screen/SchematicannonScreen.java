package dev.schematicannon.client.screen;

import dev.schematicannon.cannon.ConfigureSchematicannonOption;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.cannon.SchematicannonMenu;
import dev.schematicannon.network.ModPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Port of Create's SchematicannonScreen with the same controls, using this mod's own textures. */
public class SchematicannonScreen extends AbstractContainerScreen<SchematicannonMenu> {
	private static final String[] REPLACE_KEYS = { "dontReplaceSolid", "replaceWithSolid", "replaceWithAny", "replaceWithEmpty" };
	private static final int[] REPLACE_ICONS = { UiDraw.REPLACE_NONE, UiDraw.REPLACE_SOLID, UiDraw.REPLACE_ANY, UiDraw.REPLACE_EMPTY };
	private static final int INV_X = 29, INV_Y = 154;

	private IconButton playButton;
	private IconButton pauseButton;
	private IconButton resetButton;

	public SchematicannonScreen(SchematicannonMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 214, INV_Y + 90);
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos;
		int y = topPos;

		playButton = addRenderableWidget(new IconButton(x + 36, y + 92, UiDraw.PLAY,
			Component.translatable("schematicannon.action.start"), () -> send(ConfigureSchematicannonOption.PLAY, true)));
		pauseButton = addRenderableWidget(new IconButton(x + 56, y + 92, UiDraw.PAUSE,
			Component.translatable("schematicannon.action.pause"), () -> send(ConfigureSchematicannonOption.PAUSE, true)));
		resetButton = addRenderableWidget(new IconButton(x + 76, y + 92, UiDraw.STOP,
			Component.translatable("schematicannon.action.stop"), () -> send(ConfigureSchematicannonOption.STOP, true)));

		for (int i = 0; i < 4; i++) {
			int mode = i;
			ConfigureSchematicannonOption option = ConfigureSchematicannonOption.values()[i];
			String key = "schematicannon.gui.schematicannon.option." + REPLACE_KEYS[i];
			addRenderableWidget(new IconButton(x + 110 + i * 20, y + 92, REPLACE_ICONS[i],
				Component.translatable(key).append("\n")
					.append(Component.translatable(key + ".description").withStyle(ChatFormatting.GRAY)),
				() -> send(option, true))
				.selectedWhen(() -> menu.contentHolder != null && menu.contentHolder.replaceMode == mode));
		}

		addRenderableWidget(new IconButton(x + 110, y + 112, UiDraw.SKIP_MISSING,
			Component.translatable("schematicannon.gui.schematicannon.option.skipMissing").append("\n")
				.append(Component.translatable("schematicannon.gui.schematicannon.option.skipMissing.description")
					.withStyle(ChatFormatting.GRAY)), () -> {
			if (menu.contentHolder != null)
				send(ConfigureSchematicannonOption.SKIP_MISSING, !menu.contentHolder.skipMissing);
		}).selectedWhen(() -> menu.contentHolder != null && menu.contentHolder.skipMissing));

		// Create's "Protect Block Entities" is on when the cannon will NOT replace block entities.
		addRenderableWidget(new IconButton(x + 130, y + 112, UiDraw.PROTECT,
			Component.translatable("schematicannon.gui.schematicannon.option.skipBlockEntities").append("\n")
				.append(Component.translatable("schematicannon.gui.schematicannon.option.skipBlockEntities.description")
					.withStyle(ChatFormatting.GRAY)), () -> {
			if (menu.contentHolder != null)
				send(ConfigureSchematicannonOption.SKIP_BLOCK_ENTITIES, !menu.contentHolder.replaceBlockEntities);
		}).selectedWhen(() -> menu.contentHolder != null && !menu.contentHolder.replaceBlockEntities));

		containerTick();
	}

	private void send(ConfigureSchematicannonOption option, boolean set) {
		ClientPacketDistributor.sendToServer(new ModPackets.ConfigureSchematicannon(option, set));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		SchematicannonBlockEntity be = menu.contentHolder;
		if (be == null)
			return;
		boolean running = be.state == SchematicannonBlockEntity.State.RUNNING;
		boolean stopped = be.state == SchematicannonBlockEntity.State.STOPPED;
		playButton.active = !running;
		pauseButton.active = running;
		resetButton.active = !stopped;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		UiDraw.blit(graphics, UiDraw.SCHEMATICANNON, x, y, 0, 0, 214, 146);
		UiDraw.blit(graphics, UiDraw.SCHEMATICANNON, x + INV_X, y + INV_Y, 0, 150, 178, 90);

		SchematicannonBlockEntity be = menu.contentHolder;
		if (be == null)
			return;

		if (be.isElectric()) {
			// battery slot in place of the gunpowder slot, and a charge bar
			UiDraw.blit(graphics, UiDraw.SCHEMATICANNON, x + 14, y + 18, 229, 9, 18, 18);
			float charge = be.hasCreativeCrate ? 1 : be.power / (float) Math.max(1, be.getMaxPower());
			UiDraw.barFill(graphics, x + 36, y + 22, 86, 8, charge, be.hasCreativeCrate ? 0xFFB070E0 : 0xFFE0572A);
		} else {
			float fuel = be.hasCreativeCrate ? 1 : be.getShotsPerGunpowder() == 0 ? 0
				: be.remainingFuel / (float) Math.max(1, be.getShotsPerGunpowder());
			UiDraw.barFill(graphics, x + 36, y + 22, 86, 8, fuel, be.hasCreativeCrate ? 0xFFB070E0 : 0xFFE0A040);
		}
		UiDraw.barFill(graphics, x + 153, y + 22, 18, 8, be.bookPrintingProgress, 0xFFEFE2C0);
		UiDraw.barFill(graphics, x + 36, y + 68, 132, 10, be.schematicProgress, 0xFF6FA0E0);

		graphics.text(font, statusLine(be), x + 40, y + 48, UiDraw.SCREEN_TEXT, false);

		if (!be.missingItem.isEmpty())
			graphics.item(be.missingItem, x + 187, y + 44);

		UiDraw.lamp(graphics, x + 116, y + 133, be.skipMissing);
		UiDraw.lamp(graphics, x + 136, y + 133, !be.replaceBlockEntities);
	}

	private Component statusLine(SchematicannonBlockEntity be) {
		MutableComponent status = Component.translatable("schematicannon.schematicannon.status." + be.statusMsg);
		if (be.statusMsg.equals("missingBlock") && !be.missingItem.isEmpty())
			status = status.append(" ").append(be.missingItem.getHoverName());
		if (be.blocksToPlace > 0 && (be.statusMsg.equals("placing") || be.statusMsg.equals("clearing")
			|| be.statusMsg.equals("searching") || be.statusMsg.equals("running")))
			status = status.append(" " + be.blocksPlaced + "/" + be.blocksToPlace);
		if (be.claimSkipped > 0)
			status = status.append(" ").append(Component.translatable("schematicannon.schematicannon.claimSkipped", be.claimSkipped));
		return Component.literal(font.plainSubstrByWidth(status.getString(), 124));
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		graphics.text(font, title, 9, 6, UiDraw.PLATE_TEXT, false);

		SchematicannonBlockEntity be = menu.contentHolder;
		if (be == null)
			return;
		String fuelText = be.hasCreativeCrate ? Component.translatable("schematicannon.gui.schematicannon.creativeCrate").getString()
			: be.isElectric() ? Component.translatable("schematicannon.gui.schematicannon.power", formatHe(be.power),
				formatHe(be.getMaxPower())).getString()
			: Component.translatable("schematicannon.gui.schematicannon.shotsRemaining", be.remainingFuel).getString();
		graphics.text(font, font.plainSubstrByWidth(fuelText, 86), 36, 33, UiDraw.TEXT, false);
		graphics.text(font, Component.translatable("schematicannon.gui.schematicannon.listPrinter"), 128, 8, UiDraw.TEXT, false);
	}

	/** 1500 -> "1.5k", like NTM's own displays. */
	private static String formatHe(long he) {
		if (he >= 1_000_000)
			return String.format(java.util.Locale.ROOT, "%.1fM", he / 1_000_000.0);
		if (he >= 1_000)
			return String.format(java.util.Locale.ROOT, "%.1fk", he / 1_000.0);
		return Long.toString(he);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		SchematicannonBlockEntity be = menu.contentHolder;
		if (be != null && be.isElectric() && menu.getCarried().isEmpty() && isHovering(14, 18, 18, 18, mouseX, mouseY)
			&& be.inventory.getItem(SchematicannonBlockEntity.SLOT_GUNPOWDER).isEmpty()) {
			graphics.setTooltipForNextFrame(font, java.util.List.of(
				Component.translatable("schematicannon.gui.schematicannon.powerPort").getVisualOrderText(),
				Component.translatable("schematicannon.gui.schematicannon.powerPort.description", be.getEnergyPerShot())
					.withStyle(ChatFormatting.GRAY).getVisualOrderText()), mouseX, mouseY);
			return;
		}
		if (menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
			String key = switch (hoveredSlot.index) {
				case 0 -> "schematic";
				case 2 -> "listPrinter";
				case 4 -> "gunpowder";
				default -> null;
			};
			if (key != null)
				graphics.setTooltipForNextFrame(font, Component.translatable("schematicannon.gui.schematicannon.slot." + key),
					mouseX, mouseY);
		}
	}
}
