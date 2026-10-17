package dev.schematicannon.client.util;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Stand-in for Catnip's outliner: draws box outlines and translucent highlighted faces through the
 * 26.2 submit pipeline. The PoseStack passed in must already be camera-relative.
 */
public final class OutlineRenderer {
	public static final int SCHEMATIC_BLUE = 0xFF6886C5;
	public static final int LIGHT_GRAY = 0xFFDDDDDD;

	public static void box(SubmitNodeCollector collector, PoseStack ms, AABB box, int argb, float width) {
		ms.pushPose();
		ms.translate(box.minX, box.minY, box.minZ);
		collector.submitShapeOutline(ms, Shapes.box(0, 0, 0, box.getXsize(), box.getYsize(), box.getZsize()),
			RenderTypes.lines(), argb, width, false);
		ms.popPose();
	}

	public static void line(SubmitNodeCollector collector, PoseStack ms, Vec3 start, Vec3 end, int argb, float width) {
		collector.submitCustomGeometry(ms, RenderTypes.lines(), (pose, buffer) -> {
			float nx = (float) (end.x - start.x), ny = (float) (end.y - start.y), nz = (float) (end.z - start.z);
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 0) {
				nx /= len;
				ny /= len;
				nz /= len;
			}
			buffer.addVertex(pose, (float) start.x, (float) start.y, (float) start.z).setColor(argb).setNormal(pose, nx, ny, nz)
				.setLineWidth(width);
			buffer.addVertex(pose, (float) end.x, (float) end.y, (float) end.z).setColor(argb).setNormal(pose, nx, ny, nz)
				.setLineWidth(width);
		});
	}

	/** Fills one face of the box with a translucent colour (Create uses a checkered texture here). */
	public static void face(SubmitNodeCollector collector, PoseStack ms, AABB box, @Nullable Direction face, int argb) {
		if (face == null)
			return;
		AABB b = box.inflate(1 / 256d);
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		collector.submitCustomGeometry(ms, RenderTypes.debugQuads(), (pose, buffer) -> {
			switch (face) {
				case DOWN -> quad(buffer, pose, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
				case UP -> quad(buffer, pose, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
				case NORTH -> quad(buffer, pose, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
				case SOUTH -> quad(buffer, pose, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
				case WEST -> quad(buffer, pose, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
				case EAST -> quad(buffer, pose, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
			}
		});
	}

	private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, int argb,
		float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		// back side, so the highlight is visible from inside the box too
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
	}

	private OutlineRenderer() {}
}
