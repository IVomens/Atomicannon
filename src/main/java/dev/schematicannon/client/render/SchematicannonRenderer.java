package dev.schematicannon.client.render;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.LaunchedItem;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.client.util.OutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Like Create's SchematicannonRenderer: the head turns and the barrel tilts to aim at the block being fired,
 * the barrel kicks back on every shot, and launched blocks fly in an arc to their target.
 */
public class SchematicannonRenderer implements BlockEntityRenderer<SchematicannonBlockEntity, SchematicannonRenderer.State> {
	public static final StandaloneModelKey<BlockStateModelPart> HEAD = new StandaloneModelKey<>(() -> "schematicannon:head");
	public static final StandaloneModelKey<BlockStateModelPart> BARREL = new StandaloneModelKey<>(() -> "schematicannon:barrel");

	private static final float PIVOT_Y = 15 / 16f;
	private static final int[] NO_TINT = new int[0];

	public static void registerModels(ModelEvent.RegisterStandalone event) {
		event.register(HEAD, SimpleUnbakedStandaloneModel.simpleModelWrapper(Schematicannon.id("block/schematicannon_head")));
		event.register(BARREL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Schematicannon.id("block/schematicannon_barrel")));
	}

	public SchematicannonRenderer(BlockEntityRendererProvider.Context context) {}

	public static class State extends BlockEntityRenderState {
		final List<Flight> flights = new ArrayList<>();
		float yaw;
		float pitch;
		float recoil;
	}

	record Flight(Vec3 offset, float scale, @Nullable MovingBlockRenderState block) {}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SchematicannonBlockEntity be, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
		BlockPos origin = be.getBlockPos();
		Vec3 pivot = Vec3.atBottomCenterOf(origin).add(0, PIVOT_Y, 0);

		aim(be, state, pivot, partialTicks);
		extractFlights(be, state, origin, pivot, partialTicks);
	}

	/** Barrel points north (-Z) when level; yaw turns it around Y, pitch raises the muzzle. */
	private void aim(SchematicannonBlockEntity be, State state, Vec3 pivot, float partialTicks) {
		float targetYaw = 180 + be.defaultYaw;
		float targetPitch = 25;

		BlockPos target = be.printer.getCurrentTarget();
		if (target == null && !be.flyingBlocks.isEmpty())
			target = be.flyingBlocks.get(be.flyingBlocks.size() - 1).target;
		if (target != null && be.state != SchematicannonBlockEntity.State.STOPPED) {
			Vec3 diff = Vec3.atCenterOf(target).subtract(pivot);
			double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
			if (horizontal > 0.5) {
				targetYaw = (float) Math.toDegrees(Math.atan2(-diff.x, -diff.z));
				// lob shots: aim above the target, more for far targets, never flatter than 10 degrees
				targetPitch = Mth.clamp((float) Math.toDegrees(Math.atan2(diff.y + horizontal * .6, horizontal)), 10, 70);
			}
		}

		// smooth the turret movement (client-side only state on the block entity)
		if (Float.isNaN(be.clientYaw))
			be.clientYaw = targetYaw;
		be.clientYaw += Mth.wrapDegrees(targetYaw - be.clientYaw) * .15f;
		be.clientPitch += (targetPitch - be.clientPitch) * .15f;
		state.yaw = be.clientYaw;
		state.pitch = be.clientPitch;

		// recoil: the newest shot kicks the barrel back for a few ticks
		state.recoil = 0;
		for (LaunchedItem launched : be.flyingBlocks) {
			float sinceFired = launched.totalTicks - launched.ticksRemaining + partialTicks;
			if (sinceFired < 5)
				state.recoil = Math.max(state.recoil, (1 - sinceFired / 5f) * .25f);
		}
	}

	private void extractFlights(SchematicannonBlockEntity be, State state, BlockPos origin, Vec3 pivot, float partialTicks) {
		state.flights.clear();
		// shots leave from the muzzle
		Vec3 muzzle = pivot.add(new Vec3(0, 0, -.75).xRot(state.pitch * Mth.DEG_TO_RAD).yRot(state.yaw * Mth.DEG_TO_RAD));

		for (LaunchedItem launched : be.flyingBlocks) {
			if (launched.ticksRemaining == 0 || launched.totalTicks <= 0)
				continue;
			float progress = (launched.totalTicks - (launched.ticksRemaining + 1 - partialTicks)) / launched.totalTicks;
			progress = Mth.clamp(progress, 0, 1);

			Vec3 target = Vec3.atCenterOf(launched.target);
			double distance = target.distanceTo(muzzle);
			double arc = Math.max(2, distance * .3);
			Vec3 pos = muzzle.lerp(target, progress).add(0, Math.sin(progress * Math.PI) * arc, 0);
			Vec3 offset = pos.subtract(origin.getX(), origin.getY(), origin.getZ());
			float scale = .3f + .7f * progress;

			MovingBlockRenderState block = null;
			if (launched instanceof LaunchedItem.ForBlockState forBlock && forBlock.state != null
				&& forBlock.state.getRenderShape() == RenderShape.MODEL && be.getLevel() instanceof ClientLevel level) {
				block = new MovingBlockRenderState();
				BlockState blockState = forBlock.state;
				block.blockState = blockState;
				block.blockPos = launched.target;
				block.randomSeedPos = launched.target;
				block.biome = level.getBiome(launched.target);
				block.cardinalLighting = level.cardinalLighting();
				block.lightEngine = level.getLightEngine();
			}
			state.flights.add(new Flight(offset, scale, block));
		}
	}

	@Override
	public void submit(State state, PoseStack ms, SubmitNodeCollector collector, CameraRenderState camera) {
		var models = Minecraft.getInstance().getModelManager();
		BlockStateModelPart head = models.getStandaloneModel(HEAD);
		BlockStateModelPart barrel = models.getStandaloneModel(BARREL);
		float yaw = state.yaw * Mth.DEG_TO_RAD;
		float pitch = state.pitch * Mth.DEG_TO_RAD;

		if (head != null) {
			ms.pushPose();
			ms.translate(.5, 0, .5);
			ms.mulPose(new Quaternionf().rotationY(yaw));
			ms.translate(-.5, 0, -.5);
			collector.submitBlockModel(ms, Sheets.cutoutBlockItemSheet(), List.of(head), NO_TINT, state.lightCoords,
				OverlayTexture.NO_OVERLAY, 0);
			ms.popPose();
		}

		if (barrel != null) {
			ms.pushPose();
			ms.translate(.5, PIVOT_Y, .5);
			ms.mulPose(new Quaternionf().rotationY(yaw));
			ms.mulPose(new Quaternionf().rotationX(pitch));
			ms.translate(0, 0, state.recoil);
			ms.translate(-.5, -PIVOT_Y, -.5);
			collector.submitBlockModel(ms, Sheets.cutoutBlockItemSheet(), List.of(barrel), NO_TINT, state.lightCoords,
				OverlayTexture.NO_OVERLAY, 0);
			ms.popPose();
		}

		for (Flight flight : state.flights) {
			ms.pushPose();
			ms.translate(flight.offset());
			ms.scale(flight.scale(), flight.scale(), flight.scale());
			ms.translate(-.5, -.5, -.5);
			if (flight.block() != null)
				collector.submitMovingBlock(ms, flight.block(), 0);
			else
				OutlineRenderer.box(collector, ms, new AABB(0, 0, 0, 1, 1, 1), OutlineRenderer.SCHEMATIC_BLUE, 2);
			ms.popPose();
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
