package dev.schematicannon.client.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum ToolType {
	DEPLOY(new Tools.DeployTool(), Items.TARGET),
	MOVE(new Tools.MoveTool(), Items.PISTON),
	MOVE_Y(new Tools.MoveVerticalTool(), Items.LADDER),
	ROTATE(new Tools.RotateTool(), Items.COMPASS),
	FLIP(new Tools.FlipTool(), Items.GLASS_PANE),
	PRINT(new Tools.PlaceTool(), Items.EMERALD);

	private final SchematicTool tool;
	private final net.minecraft.world.item.Item iconItem;
	private ItemStack icon;

	ToolType(SchematicTool tool, net.minecraft.world.item.Item icon) {
		this.tool = tool;
		this.iconItem = icon;
	}

	public SchematicTool getTool() {
		return tool;
	}

	/** Index into the icon sheet (tools start at row 1). */
	public int getIconIndex() {
		return dev.schematicannon.client.screen.UiDraw.TOOLS + ordinal();
	}

	public ItemStack getIcon() {
		// created lazily: item stacks cannot exist before registries are bound
		if (icon == null)
			icon = new ItemStack(iconItem);
		return icon;
	}

	public MutableComponent getDisplayName() {
		return Component.translatable("schematicannon.schematic.tool." + name().toLowerCase(Locale.ROOT));
	}

	public List<Component> getDescription() {
		List<Component> lines = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			String key = "schematicannon.schematic.tool." + name().toLowerCase(Locale.ROOT) + ".description." + i;
			Component line = Component.translatable(key);
			if (!line.getString().isEmpty() && !line.getString().equals(key))
				lines.add(line);
		}
		return lines;
	}

	public static List<ToolType> getTools(boolean creative) {
		List<ToolType> tools = new ArrayList<>();
		Collections.addAll(tools, MOVE, MOVE_Y, DEPLOY, ROTATE, FLIP);
		if (creative)
			tools.add(PRINT);
		return tools;
	}
}
