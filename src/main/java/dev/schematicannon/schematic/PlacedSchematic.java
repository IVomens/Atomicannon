package dev.schematicannon.schematic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * A schematic with its rotation/mirror applied, stored in coordinates relative to the anchor.
 * This replaces Create's {@code SchematicLevel} (a fake world) with plain maps, which is all the printer needs.
 */
public class PlacedSchematic {
	private final BlockPos anchor;
	private final Map<BlockPos, BlockState> blocks = new HashMap<>();
	private final Map<BlockPos, CompoundTag> blockEntityData = new HashMap<>();
	/** Settings of blocks without a block entity (NTM fluid duct types). */
	private final Map<BlockPos, CompoundTag> blockSettings = new HashMap<>();
	private final List<SchematicFile.EntityEntry> entities = new ArrayList<>();
	private final BoundingBox bounds;

	private final StructurePlaceSettings settings;

	/** Where a position from the schematic file ends up in the world for this placement (rotation/mirror applied). */
	public BlockPos fileToWorld(BlockPos filePos) {
		return anchor.offset(StructureTemplate.calculateRelativePosition(settings, filePos));
	}

	private final @Nullable BlockPos savedAt;
	private final Vec3i fileSize;

	/**
	 * A world position from where the schematic was saved (e.g. the reactor an RBMK display was linked to), moved to
	 * where it ends up in this placement. Null if the file does not know where it was saved, or the position is not at
	 * or near the build (then it is not a link into it).
	 */
	public @Nullable BlockPos savedWorldToWorld(BlockPos savedWorld) {
		if (savedAt == null)
			return null;
		BlockPos rel = savedWorld.subtract(savedAt);
		int m = 256;
		if (rel.getX() < -m || rel.getY() < -m || rel.getZ() < -m || rel.getX() > fileSize.getX() + m
			|| rel.getY() > fileSize.getY() + m || rel.getZ() > fileSize.getZ() + m)
			return null;
		return fileToWorld(rel);
	}

	public PlacedSchematic(SchematicFile file, BlockPos anchor, StructurePlaceSettings settings) {
		this.anchor = anchor;
		this.settings = settings;
		this.savedAt = file.savedAt();
		this.fileSize = file.size();

		for (SchematicFile.BlockEntry entry : file.blocks()) {
			BlockPos local = StructureTemplate.calculateRelativePosition(settings, entry.pos());
			BlockState state = entry.state().mirror(settings.getMirror()).rotate(settings.getRotation());
			blocks.put(local, state);
			if (entry.nbt() != null && state.hasBlockEntity())
				blockEntityData.put(local, entry.nbt());
			else if (entry.nbt() != null && entry.nbt().contains(SchematicPlacement.SETTINGS_KEY))
				blockSettings.put(local, entry.nbt().getCompoundOrEmpty(SchematicPlacement.SETTINGS_KEY));
		}

		for (SchematicFile.EntityEntry entity : file.entities()) {
			Vec3 pos = StructureTemplate.transformedVec3d(settings, entity.pos());
			BlockPos blockPos = StructureTemplate.calculateRelativePosition(settings, entity.blockPos());
			entities.add(new SchematicFile.EntityEntry(pos, blockPos, entity.nbt()));
		}

		Vec3i size = file.size();
		BlockPos farCorner = StructureTemplate.calculateRelativePosition(settings,
			new BlockPos(Math.max(0, size.getX() - 1), Math.max(0, size.getY() - 1), Math.max(0, size.getZ() - 1)));
		bounds = BoundingBox.fromCorners(BlockPos.ZERO, farCorner);
	}

	public BlockPos anchor() {
		return anchor;
	}

	/** Bounds relative to the anchor. */
	public BoundingBox bounds() {
		return bounds;
	}

	public BlockState getLocal(BlockPos local) {
		return blocks.getOrDefault(local, Blocks.AIR.defaultBlockState());
	}

	public BlockState getWorld(BlockPos world) {
		return getLocal(world.subtract(anchor));
	}

	public @Nullable CompoundTag getDataLocal(BlockPos local) {
		return blockEntityData.get(local);
	}

	public @Nullable CompoundTag getDataWorld(BlockPos world) {
		return getDataLocal(world.subtract(anchor));
	}

	public @Nullable CompoundTag getSettingsWorld(BlockPos world) {
		CompoundTag settings = blockSettings.get(world.subtract(anchor));
		return settings == null ? null : settings.copy();
	}

	public Map<BlockPos, BlockState> localBlocks() {
		return Collections.unmodifiableMap(blocks);
	}

	/** Entities with positions relative to the anchor. */
	public List<SchematicFile.EntityEntry> entities() {
		return entities;
	}

	public boolean isEmpty() {
		return blocks.isEmpty() && entities.isEmpty();
	}
}
