package dev.schematicannon.client.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.schematicannon.schematic.PlacedSchematic;
import dev.schematicannon.schematic.SchematicFile;
import dev.schematicannon.schematic.SchematicPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * Draws a schematic file as a small turning 3D model inside a GUI (Schematic Table preview), using the 26.2
 * picture-in-picture system and the same block/block-entity renderer as the in-world hologram.
 */
public class SchematicPreviewRenderer extends PictureInPictureRenderer<SchematicPreviewRenderer.State> {
	/** Very large schematics are not previewed; tessellating them would stall the client. */
	public static final int MAX_BLOCKS = 40_000;

	public record State(Preview preview, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
		@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
		public State(Preview preview, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
			@Nullable ScreenRectangle scissorArea) {
			this(preview, yaw, pitch, x0, y0, x1, y1, scale, scissorArea,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
		}
	}

	/** A loaded schematic ready to draw. */
	public record Preview(SchematicRenderer renderer, Vec3i size, int blockCount) {}

	private static final Map<String, @Nullable Preview> CACHE = new HashMap<>();
	private static final CameraRenderState CAMERA = new CameraRenderState();

	public static void register(RegisterPictureInPictureRenderersEvent event) {
		event.register(State.class, SchematicPreviewRenderer::new);
	}

	/** Loads (and caches) a file from the local schematics folder; null if empty, unreadable or too large. */
	public static @Nullable Preview load(String fileName) {
		if (CACHE.containsKey(fileName))
			return CACHE.get(fileName);
		Preview preview = null;
		Minecraft mc = Minecraft.getInstance();
		Path path = SchematicPaths.SCHEMATICS_DIR.resolve(fileName).normalize();
		if (mc.level != null && path.startsWith(SchematicPaths.SCHEMATICS_DIR) && Files.isRegularFile(path)) {
			SchematicFile file = SchematicFile.read(path, mc.level.holderLookup(Registries.BLOCK));
			if (!file.isEmpty() && file.blocks().size() <= MAX_BLOCKS) {
				PlacedSchematic placed = new PlacedSchematic(file, BlockPos.ZERO, new StructurePlaceSettings());
				Map<BlockPos, BlockState> blocks = new HashMap<>(placed.localBlocks());
				Map<BlockPos, CompoundTag> data = new HashMap<>();
				blocks.keySet().forEach(pos -> {
					CompoundTag tag = placed.getDataLocal(pos);
					if (tag != null)
						data.put(pos, tag);
				});
				int count = (int) blocks.values().stream().filter(s -> !s.isAir()).count();
				preview = new Preview(new SchematicRenderer(blocks, data), file.size(), count);
			}
		}
		CACHE.put(fileName, preview);
		return preview;
	}

	/** Forget cached previews (e.g. after the file list is refreshed). */
	public static void clearCache() {
		CACHE.clear();
	}

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	@Override
	protected void renderToTexture(State state, PoseStack ms, SubmitNodeCollector collector) {
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
		Vec3i size = state.preview().size();
		ms.mulPose(Axis.XP.rotationDegrees(180)); // GUI y points down; models point up
		ms.mulPose(Axis.XP.rotationDegrees(state.pitch())); // look down at it from above
		ms.mulPose(Axis.YP.rotationDegrees(state.yaw()));
		ms.translate(-size.getX() / 2f, -size.getY() / 2f, -size.getZ() / 2f);
		state.preview().renderer().render(collector, ms, CAMERA);
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected String getTextureLabel() {
		return "schematic preview";
	}
}
