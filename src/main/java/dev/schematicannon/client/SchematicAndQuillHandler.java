package dev.schematicannon.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.client.screen.SchematicPromptScreen;
import dev.schematicannon.client.util.OutlineRenderer;
import dev.schematicannon.client.util.Raycast;
import dev.schematicannon.network.ModPackets;
import dev.schematicannon.registry.ModItems;
import dev.schematicannon.schematic.SchematicExport;
import dev.schematicannon.schematic.SchematicPaths;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Port of Create's SchematicAndQuillHandler: two-corner selection and saving. */
public class SchematicAndQuillHandler {
	public @Nullable BlockPos firstPos;
	public @Nullable BlockPos secondPos;
	private @Nullable BlockPos selectedPos;
	private @Nullable Direction selectedFace;
	private int range = 10;

	public boolean mouseScrolled(double delta) {
		if (!isActive())
			return false;
		if (!ModKeys.ctrlDown())
			return false;
		if (secondPos == null)
			range = (int) Mth.clamp(range + delta, 1, 100);
		if (selectedFace == null || firstPos == null || secondPos == null)
			return true;

		AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos));
		Vec3i vec = selectedFace.getUnitVec3i();
		Vec3 projectedView = Minecraft.getInstance().gameRenderer.mainCamera().position();
		if (bb.contains(projectedView))
			delta *= -1;

		// Round away from zero to avoid an implicit floor
		int intDelta = (int) (delta > 0 ? Math.ceil(delta) : Math.floor(delta));

		int x = vec.getX() * intDelta;
		int y = vec.getY() * intDelta;
		int z = vec.getZ() * intDelta;

		AxisDirection axisDirection = selectedFace.getAxisDirection();
		if (axisDirection == AxisDirection.NEGATIVE)
			bb = bb.move(-x, -y, -z);

		double maxX = Math.max(bb.maxX - x * axisDirection.getStep(), bb.minX);
		double maxY = Math.max(bb.maxY - y * axisDirection.getStep(), bb.minY);
		double maxZ = Math.max(bb.maxZ - z * axisDirection.getStep(), bb.minZ);
		bb = new AABB(bb.minX, bb.minY, bb.minZ, maxX, maxY, maxZ);

		firstPos = BlockPos.containing(bb.minX, bb.minY, bb.minZ);
		secondPos = BlockPos.containing(bb.maxX, bb.maxY, bb.maxZ);
		LocalPlayer player = Minecraft.getInstance().player;
		player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.dimensions",
			(int) bb.getXsize() + 1, (int) bb.getYsize() + 1, (int) bb.getZsize() + 1));
		return true;
	}

	public boolean onMouseInput(int button, boolean pressed) {
		if (!pressed || button != 1)
			return false;
		if (!isActive())
			return false;

		LocalPlayer player = Minecraft.getInstance().player;

		if (player.isShiftKeyDown()) {
			discard();
			return true;
		}

		if (secondPos != null) {
			Minecraft.getInstance().gui.setScreen(new SchematicPromptScreen());
			return true;
		}

		if (selectedPos == null) {
			player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.noTarget"));
			return true;
		}

		if (firstPos != null) {
			secondPos = selectedPos;
			player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.secondPos"));
			return true;
		}

		firstPos = selectedPos;
		player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.firstPos"));
		return true;
	}

	public void discard() {
		LocalPlayer player = Minecraft.getInstance().player;
		firstPos = null;
		secondPos = null;
		player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.abort"));
	}

	public void tick() {
		if (!isActive())
			return;

		LocalPlayer player = Minecraft.getInstance().player;
		if (ModKeys.ctrlDown()) {
			float pt = Raycast.partialTicks();
			Vec3 targetVec = player.getEyePosition(pt).add(player.getViewVector(pt).scale(range));
			selectedPos = BlockPos.containing(targetVec);
		} else {
			BlockHitResult trace = Raycast.rayTraceRange(player.level(), player, 75);
			if (trace != null && trace.getType() == HitResult.Type.BLOCK) {
				BlockPos hit = trace.getBlockPos();
				boolean replaceable = player.level().getBlockState(hit)
					.canBeReplaced(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, trace)));
				if (trace.getDirection().getAxis().isVertical() && !replaceable)
					hit = hit.relative(trace.getDirection());
				selectedPos = hit;
			} else
				selectedPos = null;
		}

		selectedFace = null;
		if (secondPos != null && firstPos != null) {
			AABB bb = new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos)).expandTowards(1, 1, 1)
				.inflate(.45f);
			Vec3 projectedView = Minecraft.getInstance().gameRenderer.mainCamera().position();
			boolean inside = bb.contains(projectedView);
			Raycast.PredicateTraceResult result =
				Raycast.rayTraceUntil(player, 70, pos -> inside ^ bb.contains(Vec3.atCenterOf(pos)));
			selectedFace = result.missed() || result.getFacing() == null ? null
				: inside ? result.getFacing().getOpposite() : result.getFacing();
		}
	}

	public void render(SubmitNodeCollector collector, PoseStack ms, Vec3 camera) {
		if (!isActive())
			return;
		AABB box = getCurrentSelectionBox();
		if (box == null)
			return;
		ms.pushPose();
		ms.translate(-camera.x, -camera.y, -camera.z);
		OutlineRenderer.box(collector, ms, box, OutlineRenderer.SCHEMATIC_BLUE, 2);
		if (selectedFace != null)
			OutlineRenderer.face(collector, ms, box, selectedFace, ModKeys.ctrlDown() ? 0x606886C5 : 0x306886C5);
		ms.popPose();
	}

	private @Nullable AABB getCurrentSelectionBox() {
		if (secondPos == null) {
			if (firstPos == null)
				return selectedPos == null ? null : new AABB(selectedPos);
			return selectedPos == null ? new AABB(firstPos)
				: new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(selectedPos)).expandTowards(1, 1, 1);
		}
		return new AABB(Vec3.atLowerCornerOf(firstPos), Vec3.atLowerCornerOf(secondPos)).expandTowards(1, 1, 1);
	}

	private boolean isActive() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.player != null && mc.gui.screen() == null
			&& mc.player.getMainHandItem().is(ModItems.SCHEMATIC_AND_QUILL.get());
	}

	public void saveSchematic(String string, boolean convertImmediately) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (firstPos == null || secondPos == null)
			return;

		SchematicExport.SchematicExportResult result = SchematicExport.saveSchematic(SchematicPaths.SCHEMATICS_DIR, string,
			false, mc.level, firstPos, secondPos);
		if (result == null) {
			player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.failed")
				.withStyle(ChatFormatting.RED));
			return;
		}

		Path file = result.file();
		player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.saved", file.getFileName().toString()));
		firstPos = null;
		secondPos = null;
		SchematicannonClient.SCHEMATIC_SENDER.refresh();
		// this computer only knows what the server sent it: ask for the full machine settings of the area
		ClientPacketDistributor.sendToServer(new ModPackets.ServerDataRequest(result.fileName(), result.origin(), result.bounds()));
		if (!convertImmediately)
			return;
		try {
			if (!ClientSchematicLoader.validateSizeLimitation(Files.size(file)))
				return;
			ClientPacketDistributor.sendToServer(new ModPackets.InstantSchematic(result.fileName(), result.origin(), result.bounds()));
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Error instantly uploading Schematic file: {}", file, e);
		}
	}
}
