package dev.schematicannon.client.tools;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.schematicannon.client.ModKeys;
import dev.schematicannon.client.SchematicHandler;
import dev.schematicannon.client.SchematicTransformation;
import dev.schematicannon.client.SchematicannonClient;
import dev.schematicannon.client.util.OutlineRenderer;
import dev.schematicannon.client.util.Raycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class SchematicToolBase implements SchematicTool {
	protected SchematicHandler schematicHandler;

	protected @Nullable BlockPos selectedPos;
	protected Vec3 chasingSelectedPos = Vec3.ZERO;
	protected Vec3 lastChasingSelectedPos = Vec3.ZERO;

	protected boolean selectIgnoreBlocks;
	protected int selectionRange;
	protected boolean schematicSelected;
	protected boolean renderSelectedFace;
	protected @Nullable Direction selectedFace;

	@Override
	public void init() {
		schematicHandler = SchematicannonClient.SCHEMATIC_HANDLER;
		selectedPos = null;
		selectedFace = null;
		schematicSelected = false;
		chasingSelectedPos = Vec3.ZERO;
		lastChasingSelectedPos = Vec3.ZERO;
	}

	@Override
	public void updateSelection() {
		updateTargetPos();

		if (selectedPos == null)
			return;
		lastChasingSelectedPos = chasingSelectedPos;
		Vec3 target = Vec3.atLowerCornerOf(selectedPos);
		if (target.distanceTo(chasingSelectedPos) < 1 / 512f) {
			chasingSelectedPos = target;
			return;
		}

		chasingSelectedPos = chasingSelectedPos.add(target.subtract(chasingSelectedPos).scale(1 / 2f));
	}

	public void updateTargetPos() {
		LocalPlayer player = Minecraft.getInstance().player;

		// Select Blueprint
		if (schematicHandler.isDeployed()) {
			SchematicTransformation transformation = schematicHandler.getTransformation();
			AABB localBounds = schematicHandler.getBounds();

			Vec3 traceOrigin = player.getEyePosition(Raycast.partialTicks());
			Vec3 start = transformation.toLocalSpace(traceOrigin);
			Vec3 end = transformation.toLocalSpace(Raycast.getTraceTarget(player, 70, traceOrigin));
			Raycast.PredicateTraceResult result =
				Raycast.rayTraceUntil(start, end, pos -> localBounds.contains(Vec3.atCenterOf(pos)));
			schematicSelected = !result.missed();
			selectedFace = schematicSelected ? result.getFacing() : null;
		}

		boolean snap = this.selectedPos == null;

		// Select location at distance
		if (selectIgnoreBlocks) {
			float pt = Raycast.partialTicks();
			selectedPos = BlockPos.containing(player.getEyePosition(pt).add(player.getViewVector(pt).scale(selectionRange)));
			if (snap)
				lastChasingSelectedPos = chasingSelectedPos = Vec3.atLowerCornerOf(selectedPos);
			return;
		}

		// Select targeted Block
		selectedPos = null;
		BlockHitResult trace = Raycast.rayTraceRange(player.level(), player, 75);
		if (trace == null || trace.getType() != HitResult.Type.BLOCK)
			return;

		BlockPos hit = BlockPos.containing(trace.getLocation());
		boolean replaceable = player.level().getBlockState(hit).canBeReplaced();
		if (trace.getDirection().getAxis().isVertical() && !replaceable)
			hit = hit.relative(trace.getDirection());
		selectedPos = hit;
		if (snap)
			lastChasingSelectedPos = chasingSelectedPos = Vec3.atLowerCornerOf(selectedPos);
	}

	@Override
	public void renderTool(SubmitNodeCollector collector, PoseStack ms, Vec3 camera) {}

	@Override
	public void renderOnSchematic(SubmitNodeCollector collector, PoseStack ms) {
		if (!schematicHandler.isDeployed())
			return;
		AABB bounds = schematicHandler.getBounds();
		OutlineRenderer.box(collector, ms, bounds, OutlineRenderer.SCHEMATIC_BLUE, 2);
		if (renderSelectedFace && selectedFace != null)
			OutlineRenderer.face(collector, ms, bounds, selectedFace, ModKeys.ctrlDown() ? 0x606886C5 : 0x306886C5);
	}

	@Override
	public boolean handleMouseWheel(double delta) {
		return false;
	}

	@Override
	public boolean handleRightClick() {
		return false;
	}
}
