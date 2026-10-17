package dev.schematicannon.client.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.client.util.OutlineRenderer;
import dev.schematicannon.client.util.Raycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Port of Create's SchematicRenderer. Block models are tessellated once into a quad cache and re-submitted
 * every frame through the 26.2 custom-geometry hook. Blocks drawn by a block entity renderer (NTM machines,
 * chests, signs, ...) get a preview-only block entity that is fed to that renderer, like Create's
 * BlockEntityRenderHelper. Anything that still cannot be drawn falls back to a faint outline box.
 */
public class SchematicRenderer {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int MAX_GHOST_BOXES = 4096;
	private static final int MAX_BLOCK_ENTITIES = 2048;

	private record CachedQuad(float x, float y, float z, BakedQuad quad, int c0, int c1, int c2, int c3, int overlay) {}

	/** A preview-only block entity and the vanilla/modded renderer that draws it. */
	private static final class PreviewBlockEntity {
		final BlockPos pos;
		final BlockEntity blockEntity;
		final BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer;
		final BlockEntityRenderState state;
		boolean broken;

		PreviewBlockEntity(BlockPos pos, BlockEntity blockEntity, BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer) {
			this.pos = pos;
			this.blockEntity = blockEntity;
			this.renderer = renderer;
			this.state = renderer.createRenderState();
		}
	}

	private final Map<BlockPos, BlockState> blocks;
	private final Map<BlockPos, CompoundTag> blockEntityData;
	private final Map<ChunkSectionLayer, List<CachedQuad>> quads = new EnumMap<>(ChunkSectionLayer.class);
	private final List<BlockPos> ghostBoxes = new ArrayList<>();
	private final List<PreviewBlockEntity> blockEntities = new ArrayList<>();
	private boolean changed = true;
	private final PoseStack scratch = new PoseStack();
	private final QuadInstance instance = new QuadInstance();

	/**
	 * @param blocks          schematic blocks in local coordinates (min corner at 0,0,0)
	 * @param blockEntityData stored block entity data at the same local positions, used for visuals only
	 */
	public SchematicRenderer(Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> blockEntityData) {
		this.blocks = blocks;
		this.blockEntityData = blockEntityData;
	}

	public void update() {
		changed = true;
	}

	public void render(SubmitNodeCollector collector, PoseStack ms, CameraRenderState camera) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null)
			return;
		if (changed)
			redraw();
		changed = false;

		quads.forEach((layer, list) -> {
			if (list.isEmpty())
				return;
			RenderType renderType = switch (layer) {
				case SOLID -> RenderTypes.solidMovingBlock();
				case CUTOUT -> RenderTypes.cutoutMovingBlock();
				case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
			};
			collector.submitCustomGeometry(ms, renderType, (pose, buffer) -> {
				scratch.setIdentity();
				scratch.last().set(pose);
				for (CachedQuad q : list) {
					scratch.pushPose();
					scratch.translate(q.x, q.y, q.z);
					instance.setColor(0, q.c0);
					instance.setColor(1, q.c1);
					instance.setColor(2, q.c2);
					instance.setColor(3, q.c3);
					instance.setLightCoords(FULL_BRIGHT);
					instance.setOverlayCoords(q.overlay);
					buffer.putBakedQuad(scratch.last(), q.quad, instance);
					scratch.popPose();
				}
			});
		});

		renderBlockEntities(collector, ms, camera);

		for (BlockPos pos : ghostBoxes)
			OutlineRenderer.box(collector, ms, new AABB(pos).deflate(1 / 16d), 0x806886C5, 1);
	}

	private void renderBlockEntities(SubmitNodeCollector collector, PoseStack ms, CameraRenderState camera) {
		float pt = Raycast.partialTicks();
		Vec3 cameraPos = camera.pos;
		for (PreviewBlockEntity entry : blockEntities) {
			if (entry.broken) {
				OutlineRenderer.box(collector, ms, new AABB(entry.pos).deflate(1 / 16d), 0x806886C5, 1);
				continue;
			}
			ms.pushPose();
			try {
				entry.renderer.extractRenderState(entry.blockEntity, entry.state, pt, cameraPos, null);
				entry.state.lightCoords = FULL_BRIGHT;
				ms.translate(entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
				entry.renderer.submit(entry.state, ms, collector, camera);
			} catch (Exception e) {
				// A renderer that needs a real world around it: show it as a box from now on.
				entry.broken = true;
				Schematicannon.LOGGER.debug("Schematic preview could not render {}", entry.blockEntity.getType(), e);
			} finally {
				ms.popPose();
			}
		}
	}

	private void redraw() {
		quads.clear();
		ghostBoxes.clear();
		blockEntities.clear();
		Minecraft mc = Minecraft.getInstance();
		ModelBlockRenderer renderer = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true, mc.getBlockColors());
		var models = mc.getModelManager().getBlockStateModelSet();
		BlockAndTintGetter getter = new Getter();

		for (var entry : blocks.entrySet()) {
			BlockPos pos = entry.getKey();
			BlockState state = entry.getValue();
			if (state.isAir())
				continue;

			// block entity renderer (NTM machines, chests, signs...) - in addition to any block model
			boolean drawnByBlockEntity = state.hasBlockEntity() && createPreviewBlockEntity(pos, state);

			RenderShape shape = state.getRenderShape();
			if (shape == RenderShape.INVISIBLE) {
				// e.g. NTM multiblock cells: the core's renderer draws the whole machine
				continue;
			}
			if (shape != RenderShape.MODEL) {
				if (!drawnByBlockEntity && ghostBoxes.size() < MAX_GHOST_BOXES)
					ghostBoxes.add(pos);
				continue;
			}

			BlockStateModel model = models.get(state);
			boolean forceSolid = ModelBlockRenderer.forceOpaque(mc.options.cutoutLeaves().get(), state);
			try {
				renderer.tesselateBlock((x, y, z, quad, inst) -> {
					ChunkSectionLayer layer = forceSolid ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
					quads.computeIfAbsent(layer, l -> new ArrayList<>()).add(new CachedQuad(x, y, z, quad,
						inst.getColor(0), inst.getColor(1), inst.getColor(2), inst.getColor(3), inst.overlayCoords()));
				}, pos.getX(), pos.getY(), pos.getZ(), getter, pos, state, model, state.getSeed(pos));
			} catch (Exception ignored) {
				// a misbehaving modded model should never crash the preview
			}
		}
	}

	@SuppressWarnings("unchecked")
	private boolean createPreviewBlockEntity(BlockPos pos, BlockState state) {
		if (blockEntities.size() >= MAX_BLOCK_ENTITIES || !(state.getBlock() instanceof EntityBlock entityBlock))
			return false;
		Minecraft mc = Minecraft.getInstance();
		try {
			BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
			if (blockEntity == null)
				return false;
			blockEntity.setLevel(mc.level);
			CompoundTag data = blockEntityData.get(pos);
			if (data != null) {
				try {
					blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, mc.level.registryAccess(), data));
				} catch (Exception ignored) {
					// visuals only; a fresh block entity is fine
				}
			}
			BlockEntityRenderer<BlockEntity, BlockEntityRenderState> beRenderer =
				mc.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
			if (beRenderer == null)
				return false;
			blockEntities.add(new PreviewBlockEntity(pos, blockEntity, beRenderer));
			return true;
		} catch (Exception e) {
			Schematicannon.LOGGER.debug("No schematic preview for {}", state, e);
			return false;
		}
	}

	/** The schematic as seen by the block model renderer: neighbours come from the schematic itself. */
	private class Getter implements BlockAndTintGetter {
		@Override
		public CardinalLighting cardinalLighting() {
			var level = Minecraft.getInstance().level;
			return level != null ? level.cardinalLighting() : CardinalLighting.DEFAULT;
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver color) {
			var mc = Minecraft.getInstance();
			if (mc.level == null || mc.player == null)
				return -1;
			return mc.level.getBlockTint(mc.player.blockPosition(), color);
		}

		@Override
		public LevelLightEngine getLightEngine() {
			return LevelLightEngine.EMPTY;
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return getBlockState(pos).getFluidState();
		}

		@Override
		public int getHeight() {
			var level = Minecraft.getInstance().level;
			return level != null ? level.getHeight() : 384;
		}

		@Override
		public int getMinY() {
			var level = Minecraft.getInstance().level;
			return level != null ? level.getMinY() : -64;
		}
	}
}
