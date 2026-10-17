package dev.schematicannon.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import dev.schematicannon.Schematicannon;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Same default keys as Create: Left Alt opens the tool menu, Left Ctrl activates the tool's alternate mode. */
public final class ModKeys {
	public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Schematicannon.id("schematicannon"));

	public static final KeyMapping TOOL_MENU = new KeyMapping("key.schematicannon.toolMenu", KeyConflictContext.IN_GAME,
		InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
	public static final KeyMapping ACTIVATE_TOOL = new KeyMapping("key.schematicannon.toolbelt", KeyConflictContext.IN_GAME,
		InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);

	public static void register(RegisterKeyMappingsEvent event) {
		event.registerCategory(CATEGORY);
		event.register(TOOL_MENU);
		event.register(ACTIVATE_TOOL);
	}

	public static boolean ctrlDown() {
		return ACTIVATE_TOOL.isDown() || Minecraft.getInstance().hasControlDown();
	}

	private ModKeys() {}
}
