package dev.schematicannon.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

public interface SchematicTool {
	void init();

	void updateSelection();

	boolean handleRightClick();

	boolean handleMouseWheel(double delta);

	/** Rendered in world space (PoseStack is camera relative, untransformed). */
	void renderTool(SubmitNodeCollector collector, PoseStack ms, Vec3 camera);

	/** Rendered in schematic space (PoseStack already carries the schematic transformation). */
	void renderOnSchematic(SubmitNodeCollector collector, PoseStack ms);

	default void renderOverlay(GuiGraphicsExtractor graphics, float partialTicks, int width, int height) {}
}
