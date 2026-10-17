package dev.schematicannon.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.client.render.SchematicannonRenderer;
import dev.schematicannon.client.screen.SchematicTableScreen;
import dev.schematicannon.client.screen.SchematicannonScreen;
import dev.schematicannon.registry.ModBlockEntities;
import dev.schematicannon.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Schematicannon.MOD_ID, dist = Dist.CLIENT)
public class SchematicannonClient {
	public static final SchematicHandler SCHEMATIC_HANDLER = new SchematicHandler();
	public static final SchematicAndQuillHandler SCHEMATIC_AND_QUILL_HANDLER = new SchematicAndQuillHandler();
	public static final ClientSchematicLoader SCHEMATIC_SENDER = new ClientSchematicLoader();

	public SchematicannonClient(IEventBus modBus, ModContainer container) {
		modBus.addListener(ModKeys::register);
		modBus.addListener(SchematicannonClient::registerScreens);
		modBus.addListener(SchematicannonClient::registerRenderers);
		modBus.addListener(SchematicannonRenderer::registerModels);
		modBus.addListener(dev.schematicannon.client.render.SchematicPreviewRenderer::register);
		modBus.addListener(SchematicannonClient::registerGuiLayers);

		NeoForge.EVENT_BUS.addListener(SchematicannonClient::onTick);
		NeoForge.EVENT_BUS.addListener(SchematicannonClient::onMouseButton);
		NeoForge.EVENT_BUS.addListener(SchematicannonClient::onMouseScroll);
		NeoForge.EVENT_BUS.addListener(SchematicannonClient::onKey);
		NeoForge.EVENT_BUS.addListener(SchematicannonClient::onSubmitGeometry);
	}

	private static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenus.SCHEMATICANNON.get(), SchematicannonScreen::new);
		event.register(ModMenus.SCHEMATIC_TABLE.get(), SchematicTableScreen::new);
	}

	private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(ModBlockEntities.SCHEMATICANNON.get(), SchematicannonRenderer::new);
	}

	private static void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAboveAll(Schematicannon.id("schematic_tools"), (graphics, deltaTracker) ->
			SCHEMATIC_HANDLER.renderOverlay(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
	}

	private static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null)
			return;
		SCHEMATIC_SENDER.tick();
		SCHEMATIC_AND_QUILL_HANDLER.tick();
		SCHEMATIC_HANDLER.tick();
		if (DevPreviewTest.ENABLED)
			DevPreviewTest.tick();
	}

	private static void onMouseButton(InputEvent.MouseButton.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || mc.player == null)
			return;
		boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
		int button = event.getButton();
		if (SCHEMATIC_HANDLER.onMouseInput(button, pressed) || SCHEMATIC_AND_QUILL_HANDLER.onMouseInput(button, pressed))
			event.setCanceled(true);
	}

	private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || mc.player == null)
			return;
		double delta = event.getScrollDeltaY();
		if (SCHEMATIC_HANDLER.mouseScrolled(delta) || SCHEMATIC_AND_QUILL_HANDLER.mouseScrolled(delta))
			event.setCanceled(true);
	}

	private static void onKey(InputEvent.Key event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || mc.player == null)
			return;
		if (!ModKeys.TOOL_MENU.matches(event.getKeyEvent()))
			return;
		if (event.getAction() == GLFW.GLFW_PRESS)
			SCHEMATIC_HANDLER.onKeyInput(true);
		else if (event.getAction() == GLFW.GLFW_RELEASE)
			SCHEMATIC_HANDLER.onKeyInput(false);
	}

	private static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
		Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
		PoseStack ms = event.getPoseStack();
		ms.pushPose();
		SCHEMATIC_HANDLER.render(event.getSubmitNodeCollector(), ms, event.getLevelRenderState().cameraRenderState);
		SCHEMATIC_AND_QUILL_HANDLER.render(event.getSubmitNodeCollector(), ms, camera);
		ms.popPose();
	}
}
