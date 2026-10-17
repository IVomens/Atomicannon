package dev.schematicannon.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.schematicannon.client.ModKeys;
import dev.schematicannon.client.SchematicTransformation;
import dev.schematicannon.client.util.OutlineRenderer;
import dev.schematicannon.client.util.Raycast;
import dev.schematicannon.registry.ModComponents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The six schematic tools, ported one-to-one from Create. */
public final class Tools {

	public static class DeployTool extends SchematicToolBase {
		@Override
		public void init() {
			super.init();
			selectionRange = -1;
		}

		@Override
		public void updateSelection() {
			if (schematicHandler.isActive() && selectionRange == -1) {
				selectionRange = (int) (schematicHandler.getBounds().getCenter().length() / 2);
				selectionRange = Mth.clamp(selectionRange, 1, 100);
			}
			selectIgnoreBlocks = ModKeys.ctrlDown();
			super.updateSelection();
		}

		@Override
		public void renderTool(SubmitNodeCollector collector, PoseStack ms, Vec3 camera) {
			if (selectedPos == null)
				return;

			ms.pushPose();
			float pt = Raycast.partialTicks();
			double x = Mth.lerp(pt, lastChasingSelectedPos.x, chasingSelectedPos.x);
			double y = Mth.lerp(pt, lastChasingSelectedPos.y, chasingSelectedPos.y);
			double z = Mth.lerp(pt, lastChasingSelectedPos.z, chasingSelectedPos.z);

			SchematicTransformation transformation = schematicHandler.getTransformation();
			AABB bounds = schematicHandler.getBounds();
			Vec3 center = bounds.getCenter();
			Vec3 rotationOffset = transformation.getRotationOffset(true);
			int centerX = (int) center.x;
			int centerZ = (int) center.z;
			double xOrigin = bounds.getXsize() / 2f;
			double zOrigin = bounds.getZsize() / 2f;
			Vec3 origin = new Vec3(xOrigin, 0, zOrigin);

			ms.translate(x - centerX - camera.x, y - camera.y, z - centerZ - camera.z);
			ms.translate(origin.add(rotationOffset));
			ms.mulPose(new org.joml.Quaternionf().rotationY(transformation.getCurrentRotation() * Mth.DEG_TO_RAD));
			ms.translate(origin.add(rotationOffset).scale(-1));

			OutlineRenderer.box(collector, ms, bounds, OutlineRenderer.SCHEMATIC_BLUE, 2);
			ms.popPose();
		}

		@Override
		public boolean handleMouseWheel(double delta) {
			if (!selectIgnoreBlocks)
				return super.handleMouseWheel(delta);
			selectionRange += (int) Math.signum(delta);
			selectionRange = Mth.clamp(selectionRange, 1, 100);
			return true;
		}

		@Override
		public boolean handleRightClick() {
			if (selectedPos == null)
				return super.handleRightClick();
			Vec3 center = schematicHandler.getBounds().getCenter();
			BlockPos target = selectedPos.offset(-((int) center.x), 0, -((int) center.z));

			ItemStack item = schematicHandler.getActiveSchematicItem();
			if (item != null) {
				item.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
				item.set(ModComponents.SCHEMATIC_ANCHOR.get(), target);
				schematicHandler.getTransformation().startAt(target);
			}

			schematicHandler.getTransformation().moveTo(target);
			schematicHandler.markDirty();
			schematicHandler.deploy();
			return true;
		}
	}

	public static class MoveTool extends SchematicToolBase {
		@Override
		public void init() {
			super.init();
			renderSelectedFace = true;
		}

		@Override
		public boolean handleMouseWheel(double delta) {
			if (!schematicSelected || selectedFace == null || !selectedFace.getAxis().isHorizontal())
				return true;

			SchematicTransformation transformation = schematicHandler.getTransformation();
			Vec3 vec = Vec3.atLowerCornerOf(selectedFace.getUnitVec3i()).scale(-Math.signum(delta));
			vec = vec.multiply(transformation.getMirrorModifier(Axis.X), 1, transformation.getMirrorModifier(Axis.Z));
			vec = SchematicTransformation.rotate(vec, transformation.getRotationTarget());
			transformation.move((int) Math.round(vec.x), 0, (int) Math.round(vec.z));
			schematicHandler.markDirty();
			return true;
		}
	}

	public static class MoveVerticalTool extends SchematicToolBase {
		@Override
		public boolean handleMouseWheel(double delta) {
			if (schematicHandler.isDeployed()) {
				schematicHandler.getTransformation().move(0, (int) Math.signum(delta), 0);
				schematicHandler.markDirty();
			}
			return true;
		}
	}

	public static class RotateTool extends SchematicToolBase {
		@Override
		public boolean handleMouseWheel(double delta) {
			schematicHandler.getTransformation().rotate90(delta > 0);
			schematicHandler.markDirty();
			return true;
		}

		@Override
		public void renderOnSchematic(SubmitNodeCollector collector, PoseStack ms) {
			AABB bounds = schematicHandler.getBounds();
			double height = bounds.getYsize() + Math.max(20, bounds.getYsize());
			Vec3 center = bounds.getCenter().add(schematicHandler.getTransformation().getRotationOffset(false));
			Vec3 start = center.subtract(0, height / 2, 0);
			Vec3 end = center.add(0, height / 2, 0);
			OutlineRenderer.line(collector, ms, start, end, OutlineRenderer.LIGHT_GRAY, 2);
			super.renderOnSchematic(collector, ms);
		}
	}

	public static class FlipTool extends SchematicToolBase {
		@Override
		public void init() {
			super.init();
			renderSelectedFace = false;
		}

		@Override
		public boolean handleRightClick() {
			mirror();
			return true;
		}

		@Override
		public boolean handleMouseWheel(double delta) {
			mirror();
			return true;
		}

		private void mirror() {
			if (schematicSelected && selectedFace != null && selectedFace.getAxis().isHorizontal()) {
				schematicHandler.getTransformation().flip(selectedFace.getAxis());
				schematicHandler.markDirty();
			}
		}

		@Override
		public void renderOnSchematic(SubmitNodeCollector collector, PoseStack ms) {
			if (!schematicSelected || selectedFace == null || !selectedFace.getAxis().isHorizontal()) {
				super.renderOnSchematic(collector, ms);
				return;
			}

			Direction facing = selectedFace.getClockWise();
			AABB bounds = schematicHandler.getBounds();

			Vec3 directionVec = Vec3.atLowerCornerOf(Direction.get(AxisDirection.POSITIVE, facing.getAxis()).getUnitVec3i());
			Vec3 boundsSize = new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
			Vec3 vec = boundsSize.multiply(directionVec);
			bounds = bounds.contract(vec.x, vec.y, vec.z).inflate(1 - directionVec.x, 1 - directionVec.y, 1 - directionVec.z);
			bounds = bounds.move(directionVec.scale(.5f).multiply(boundsSize));

			OutlineRenderer.box(collector, ms, bounds, OutlineRenderer.LIGHT_GRAY, 2);
			super.renderOnSchematic(collector, ms);
		}
	}

	public static class PlaceTool extends SchematicToolBase {
		@Override
		public boolean handleRightClick() {
			schematicHandler.printInstantly();
			return true;
		}
	}

	private Tools() {}
}
