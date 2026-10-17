package dev.schematicannon.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.client.render.SchematicRenderer;
import dev.schematicannon.client.tools.ToolType;
import dev.schematicannon.client.util.Raycast;
import dev.schematicannon.network.ModPackets;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.registry.ModItems;
import dev.schematicannon.schematic.PlacedSchematic;
import dev.schematicannon.schematic.SchematicFile;
import dev.schematicannon.schematic.SchematicItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Port of Create's SchematicHandler: drives the held schematic's hologram and tools. */
public class SchematicHandler {
	private static final int SYNC_DELAY = 10;

	private @Nullable String displayedSchematic;
	private SchematicTransformation transformation = new SchematicTransformation();
	private AABB bounds = new AABB(BlockPos.ZERO);
	private boolean deployed;
	private boolean active;
	private ToolType currentTool = ToolType.DEPLOY;

	private int syncCooldown;
	private int activeHotbarSlot;
	private @Nullable ItemStack activeSchematicItem;

	private final SchematicRenderer[] renderers = new SchematicRenderer[3];
	private ToolSelectionOverlay selectionScreen;

	public SchematicHandler() {
		selectionScreen = new ToolSelectionOverlay(List.of(ToolType.DEPLOY), this::equip);
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameMode == null || mc.player == null)
			return;
		if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			if (active) {
				active = false;
				syncCooldown = 0;
				activeHotbarSlot = 0;
				activeSchematicItem = null;
			}
			return;
		}

		if (activeSchematicItem != null)
			transformation.tick();

		LocalPlayer player = mc.player;
		ItemStack stack = findBlueprintInHand(player);
		if (stack == null) {
			active = false;
			syncCooldown = 0;
			if (activeSchematicItem != null && itemLost(player)) {
				activeHotbarSlot = 0;
				activeSchematicItem = null;
			}
			return;
		}

		if (!active || !stack.get(ModComponents.SCHEMATIC_FILE.get()).equals(displayedSchematic))
			init(player, stack);
		if (!active)
			return;

		if (syncCooldown > 0)
			syncCooldown--;
		if (syncCooldown == 1)
			sync();

		selectionScreen.update();
		currentTool.getTool().updateSelection();
	}

	private void init(LocalPlayer player, ItemStack stack) {
		loadSettings(stack);
		displayedSchematic = stack.get(ModComponents.SCHEMATIC_FILE.get());
		active = true;
		if (deployed) {
			setupRenderer();
			ToolType toolBefore = currentTool;
			selectionScreen = new ToolSelectionOverlay(ToolType.getTools(player.isCreative()), this::equip);
			if (toolBefore != null) {
				selectionScreen.setSelectedElement(toolBefore);
				equip(toolBefore);
			}
		} else
			selectionScreen = new ToolSelectionOverlay(List.of(ToolType.DEPLOY), this::equip);
	}

	private void setupRenderer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || activeSchematicItem == null)
			return;
		SchematicFile schematic = SchematicItem.loadSchematic(mc.level, activeSchematicItem);
		Vec3i size = schematic.size();
		if (size.equals(Vec3i.ZERO)) {
			renderers[0] = renderers[1] = renderers[2] = null;
			return;
		}

		try {
			renderers[0] = preview(schematic, Mirror.NONE, BlockPos.ZERO);
			renderers[1] = preview(schematic, Mirror.FRONT_BACK, BlockPos.ZERO.east(size.getX() - 1));
			renderers[2] = preview(schematic, Mirror.LEFT_RIGHT, BlockPos.ZERO.south(size.getZ() - 1));
		} catch (Exception e) {
			mc.player.sendSystemMessage(Component.translatable("schematicannon.schematic.error"));
			Schematicannon.LOGGER.error("Failed to load Schematic for Previewing", e);
		}
	}

	private static SchematicRenderer preview(SchematicFile file, Mirror mirror, BlockPos offset) {
		StructurePlaceSettings settings = new StructurePlaceSettings();
		settings.setMirror(mirror);
		PlacedSchematic placed = new PlacedSchematic(file, BlockPos.ZERO, settings);
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		Map<BlockPos, CompoundTag> data = new HashMap<>();
		placed.localBlocks().forEach((pos, state) -> {
			blocks.put(pos.offset(offset), state);
			CompoundTag tag = placed.getDataLocal(pos);
			if (tag != null)
				data.put(pos.offset(offset), tag);
		});
		return new SchematicRenderer(blocks, data);
	}

	public void render(SubmitNodeCollector collector, PoseStack ms, CameraRenderState cameraState) {
		Vec3 camera = cameraState.pos;
		if (!active || activeSchematicItem == null)
			return;

		ms.pushPose();
		currentTool.getTool().renderTool(collector, ms, camera);
		ms.popPose();

		ms.pushPose();
		transformation.applyTransformations(ms, camera);

		if (deployed) {
			float pt = Raycast.partialTicks();
			boolean lr = transformation.getScaleLR().getValue(pt) < 0;
			boolean fb = transformation.getScaleFB().getValue(pt) < 0;
			if (lr && !fb && renderers[2] != null)
				renderers[2].render(collector, ms, cameraState);
			else if (fb && !lr && renderers[1] != null)
				renderers[1].render(collector, ms, cameraState);
			else if (renderers[0] != null)
				renderers[0].render(collector, ms, cameraState);
		}

		currentTool.getTool().renderOnSchematic(collector, ms);
		ms.popPose();
	}

	public void renderOverlay(GuiGraphicsExtractor graphics, float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || !active)
			return;
		if (activeSchematicItem != null) {
			// highlight the hotbar slot holding the active schematic, like Create's hotbar overlay
			int x = graphics.guiWidth() / 2 - 92 + activeHotbarSlot * 20;
			int y = graphics.guiHeight() - 23;
			graphics.outline(x + 1, y + 1, 22, 22, 0xFF6886C5);
		}
		currentTool.getTool().renderOverlay(graphics, partialTicks, graphics.guiWidth(), graphics.guiHeight());
		selectionScreen.renderPassive(graphics, partialTicks);
	}

	public boolean onMouseInput(int button, boolean pressed) {
		if (!active)
			return false;
		if (!pressed || button != 1)
			return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player.isShiftKeyDown())
			return false;
		if (mc.hitResult instanceof BlockHitResult blockHit && mc.level != null
			&& mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof dev.schematicannon.cannon.SchematicannonBlock)
			return false;
		return currentTool.getTool().handleRightClick();
	}

	public void onKeyInput(boolean pressed) {
		if (!active)
			return;
		if (pressed && !selectionScreen.focused)
			selectionScreen.focused = true;
		if (!pressed && selectionScreen.focused) {
			selectionScreen.focused = false;
			selectionScreen.onClose();
		}
	}

	public boolean mouseScrolled(double delta) {
		if (!active)
			return false;
		if (selectionScreen.focused) {
			selectionScreen.cycle((int) Math.signum(delta));
			return true;
		}
		if (ModKeys.ctrlDown())
			return currentTool.getTool().handleMouseWheel(delta);
		return false;
	}

	private @Nullable ItemStack findBlueprintInHand(Player player) {
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.SCHEMATIC.get()))
			return null;
		if (!stack.has(ModComponents.SCHEMATIC_FILE.get()))
			return null;
		activeSchematicItem = stack;
		activeHotbarSlot = player.getInventory().getSelectedSlot();
		return stack;
	}

	private boolean itemLost(Player player) {
		for (int i = 0; i < 9; i++) {
			if (ItemStack.matches(player.getInventory().getItem(i), activeSchematicItem))
				return false;
		}
		return true;
	}

	public void markDirty() {
		syncCooldown = SYNC_DELAY;
	}

	public void sync() {
		if (activeSchematicItem == null)
			return;
		StructurePlaceSettings settings = transformation.toSettings();
		ClientPacketDistributor.sendToServer(new ModPackets.SchematicSync(activeHotbarSlot, deployed,
			transformation.getAnchor(), settings.getRotation(), settings.getMirror()));
	}

	public void equip(ToolType tool) {
		this.currentTool = tool;
		currentTool.getTool().init();
	}

	public void loadSettings(ItemStack blueprint) {
		StructurePlaceSettings settings = SchematicItem.getSettings(blueprint);
		transformation = new SchematicTransformation();
		deployed = blueprint.getOrDefault(ModComponents.SCHEMATIC_DEPLOYED.get(), false);
		BlockPos anchor = blueprint.getOrDefault(ModComponents.SCHEMATIC_ANCHOR.get(), BlockPos.ZERO);
		Vec3i size = blueprint.get(ModComponents.SCHEMATIC_BOUNDS.get());
		if (size == null)
			return;
		bounds = new AABB(0, 0, 0, size.getX(), size.getY(), size.getZ());
		transformation.init(anchor, settings, bounds);
	}

	public void deploy() {
		if (!deployed) {
			List<ToolType> tools = ToolType.getTools(Minecraft.getInstance().player.isCreative());
			selectionScreen = new ToolSelectionOverlay(tools, this::equip);
		}
		deployed = true;
		setupRenderer();
	}

	/** Re-reads position/rotation/mirror from the held item (after typing exact values in the edit screen). */
	public void reload() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && activeSchematicItem != null)
			init(player, activeSchematicItem);
	}

	public String getCurrentSchematicName() {
		return displayedSchematic != null ? displayedSchematic : "-";
	}

	public void printInstantly() {
		if (activeSchematicItem == null)
			return;
		StructurePlaceSettings settings = transformation.toSettings();
		ItemStack copy = activeSchematicItem.copy();
		copy.set(ModComponents.SCHEMATIC_ANCHOR.get(), transformation.getAnchor());
		copy.set(ModComponents.SCHEMATIC_ROTATION.get(), settings.getRotation());
		copy.set(ModComponents.SCHEMATIC_MIRROR.get(), settings.getMirror());
		ClientPacketDistributor.sendToServer(new ModPackets.SchematicPlace(copy));
		activeSchematicItem.set(ModComponents.SCHEMATIC_DEPLOYED.get(), false);
		deployed = false;
		sync();
		active = false;
	}

	public boolean isActive() {
		return active;
	}

	public AABB getBounds() {
		return bounds;
	}

	public SchematicTransformation getTransformation() {
		return transformation;
	}

	public boolean isDeployed() {
		return deployed;
	}

	public @Nullable ItemStack getActiveSchematicItem() {
		return activeSchematicItem;
	}
}
