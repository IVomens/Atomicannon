package dev.schematicannon.schematic;

import java.util.LinkedList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.cannon.MaterialChecklist;
import dev.schematicannon.compat.NtmCompat;
import dev.schematicannon.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

/** Port of Create's SchematicPrinter: walks the schematic block by block, then deferred blocks, then entities. */
public class SchematicPrinter {

	public enum PrintStage {
		BLOCKS, DEFERRED_BLOCKS, ENTITIES
	}

	private boolean schematicLoaded;
	private boolean isErrored;
	private @Nullable PlacedSchematic schematic;
	private @Nullable BlockPos schematicAnchor;
	private Rotation rotation = Rotation.NONE;
	private Mirror mirror = Mirror.NONE;

	/** Player responsible for this print; checked against land claims (WarForge) for every block. */
	private java.util.@Nullable UUID owner;
	private boolean lastDeniedByClaim;

	/** Relative to the anchor. */
	private @Nullable BlockPos currentPos;
	private int printingEntityIndex;
	private PrintStage printStage;
	private final List<BlockPos> deferredBlocks;

	public SchematicPrinter() {
		printingEntityIndex = -1;
		printStage = PrintStage.BLOCKS;
		deferredBlocks = new LinkedList<>();
	}

	public void fromTag(CompoundTag compound, boolean clientPacket) {
		currentPos = compound.read("CurrentPos", BlockPos.CODEC).orElse(null);
		if (clientPacket) {
			schematicLoaded = false;
			schematicAnchor = compound.read("Anchor", BlockPos.CODEC).orElse(null);
			if (schematicAnchor != null)
				schematicLoaded = true;
		}
		printingEntityIndex = compound.getIntOr("EntityProgress", -1);
		try {
			printStage = PrintStage.valueOf(compound.getStringOr("PrintStage", PrintStage.BLOCKS.name()));
		} catch (IllegalArgumentException e) {
			printStage = PrintStage.BLOCKS;
		}
		deferredBlocks.clear();
		ListTag list = compound.getListOrEmpty("DeferredBlocks");
		for (int i = 0; i < list.size(); i++)
			list.getCompoundOrEmpty(i).read("Pos", BlockPos.CODEC).ifPresent(deferredBlocks::add);
	}

	public void write(CompoundTag compound) {
		if (currentPos != null)
			compound.store("CurrentPos", BlockPos.CODEC, currentPos);
		if (schematicAnchor != null)
			compound.store("Anchor", BlockPos.CODEC, schematicAnchor);
		compound.putInt("EntityProgress", printingEntityIndex);
		compound.putString("PrintStage", printStage.name());
		ListTag tagDeferredBlocks = new ListTag();
		for (BlockPos p : deferredBlocks) {
			CompoundTag tag = new CompoundTag();
			tag.store("Pos", BlockPos.CODEC, p);
			tagDeferredBlocks.add(tag);
		}
		compound.put("DeferredBlocks", tagDeferredBlocks);
	}

	public void loadSchematic(ItemStack blueprint, Level originalWorld) {
		if (!blueprint.has(ModComponents.SCHEMATIC_ANCHOR.get()) || !blueprint.has(ModComponents.SCHEMATIC_DEPLOYED.get()))
			return;

		SchematicFile file = SchematicItem.loadSchematic(originalWorld, blueprint);
		StructurePlaceSettings settings = SchematicItem.getSettings(blueprint);
		schematicAnchor = blueprint.get(ModComponents.SCHEMATIC_ANCHOR.get());
		rotation = settings.getRotation();
		mirror = settings.getMirror();

		try {
			schematic = new PlacedSchematic(file, schematicAnchor, settings);
		} catch (Exception e) {
			dev.schematicannon.Schematicannon.LOGGER.error("Failed to load Schematic for Printing", e);
			schematicLoaded = true;
			isErrored = true;
			return;
		}

		printingEntityIndex = -1;
		printStage = PrintStage.BLOCKS;
		deferredBlocks.clear();
		requirementCache.clear();
		BoundingBox bounds = schematic.bounds();
		// Start one before the first position (advance moves east first). Blocks that are already
		// correct are skipped cheaply, so restarting after a reload simply re-scans.
		currentPos = new BlockPos(bounds.minX() - 1, bounds.minY(), bounds.minZ());
		schematicLoaded = true;
	}

	public void resetSchematic() {
		requirementCache.clear();
		countIterator = null;
		schematicLoaded = false;
		schematicAnchor = null;
		isErrored = false;
		currentPos = null;
		schematic = null;
		printingEntityIndex = -1;
		printStage = PrintStage.BLOCKS;
		deferredBlocks.clear();
	}

	public boolean isLoaded() {
		return schematicLoaded;
	}

	public boolean isErrored() {
		return isErrored;
	}

	public @Nullable BlockPos getCurrentTarget() {
		if (!isLoaded() || isErrored() || schematicAnchor == null || currentPos == null)
			return null;
		return schematicAnchor.offset(currentPos);
	}

	public PrintStage getPrintStage() {
		return printStage;
	}

	public @Nullable BlockPos getAnchor() {
		return schematicAnchor;
	}

	public boolean isWorldEmpty() {
		return schematic == null || schematic.isEmpty();
	}

	@FunctionalInterface
	public interface BlockTargetHandler {
		void handle(BlockPos target, BlockState blockState, @Nullable CompoundTag data);
	}

	@FunctionalInterface
	public interface EntityTargetHandler {
		void handle(BlockPos target, Entity entity);
	}

	public void handleCurrentTarget(Level level, BlockTargetHandler blockHandler, EntityTargetHandler entityHandler) {
		BlockPos target = getCurrentTarget();
		if (target == null || schematic == null)
			return;

		if (printStage == PrintStage.ENTITIES) {
			SchematicFile.EntityEntry entry = schematic.entities().get(printingEntityIndex);
			Vec3 worldPos = entry.pos().add(schematicAnchor.getX(), schematicAnchor.getY(), schematicAnchor.getZ());
			Entity entity = SchematicPlacement.createEntity(level, entry.nbt(), worldPos, rotation, mirror);
			if (entity != null)
				entityHandler.handle(target, entity);
		} else {
			BlockState blockState = SchematicPlacement.setZeroAge(schematic.getWorld(target));
			CompoundTag raw = schematic.getDataWorld(target);
			CompoundTag data = NbtSafety.prepare(blockState, raw);
			// NTM blocks: their own data with every content emptied, which already holds all of their settings
			CompoundTag config = data == null ? NtmCompat.configData(level, target, blockState, raw, schematic::savedWorldToWorld)
				: null;
			if (config != null)
				data = config;
			// otherwise what NTM's settings tool would copy (fluid identifier types...); never contents. Not on top of
			// the full data: the tool's paste can differ (the mixer would put its first input on its output).
			CompoundTag settings = config != null ? null : NtmCompat.extractSettings(level, target, blockState, raw);
			// settings recorded at save time (NTM fluid duct types), on plain blocks or next to block entity data
			CompoundTag recorded = schematic.getSettingsWorld(target);
			if (recorded == null && raw != null && raw.contains(SchematicPlacement.SETTINGS_KEY))
				recorded = raw.getCompoundOrEmpty(SchematicPlacement.SETTINGS_KEY).copy();
			if (recorded != null)
				settings = settings == null ? recorded : settings.copy().merge(recorded);
			if (settings != null)
				NtmCompat.placeLinks(settings, schematic::fileToWorld); // pylon / pipe anchor links
			if (settings != null) {
				data = data == null ? new CompoundTag() : data;
				data.put(SchematicPlacement.SETTINGS_KEY, settings);
			}
			blockHandler.handle(target, blockState, data);
		}
	}

	@FunctionalInterface
	public interface PlacementPredicate {
		boolean shouldPlace(BlockPos target, BlockState blockState, BlockState toReplace, @Nullable BlockState toReplaceOther,
			boolean isNormalCube);
	}

	public boolean shouldPlaceCurrent(Level world) {
		return shouldPlaceCurrent(world, (a, b, c, d, e) -> true);
	}

	public boolean shouldPlaceCurrent(Level world, PlacementPredicate predicate) {
		if (world == null)
			return false;
		if (printStage == PrintStage.ENTITIES)
			return true;
		BlockPos target = getCurrentTarget();
		return target != null && shouldPlaceBlock(world, predicate, target);
	}

	public boolean shouldPlaceBlock(Level world, PlacementPredicate predicate, BlockPos pos) {
		if (schematic == null)
			return false;
		BlockState state = SchematicPlacement.setZeroAge(schematic.getWorld(pos));

		if (!world.isLoaded(pos))
			return false;
		if (!world.getWorldBorder().isWithinBounds(pos))
			return false;

		// NTM: never print the filler cells of a multiblock; the core rebuilds them.
		if (NtmCompat.isMultiblockCell(state))
			return false;

		BlockState toReplace = world.getBlockState(pos);
		BlockState toReplaceOther = null;

		if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
			&& state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT)
			toReplaceOther = world.getBlockState(pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER)
			toReplaceOther = world.getBlockState(pos.above());

		if (toReplace == state)
			return false;
		if (toReplace.getDestroySpeed(world, pos) == -1
			|| (toReplaceOther != null && toReplaceOther.getDestroySpeed(world, pos) == -1))
			return false;
		// Don't knock out an existing NTM machine that already sits here.
		if (NtmCompat.isMultiblockCell(toReplace) && !state.isAir())
			return false;

		boolean isNormalCube = state.isRedstoneConductor(world, pos);
		if (!predicate.shouldPlace(pos, state, toReplace, toReplaceOther, isNormalCube))
			return false;
		if (!claimAllows(world, pos, state, toReplace)) {
			lastDeniedByClaim = true;
			return false;
		}
		return true;
	}

	/** Land-claim check for the owner, including the cells an NTM machine core will build around itself. */
	private boolean claimAllows(Level world, BlockPos pos, BlockState state, BlockState toReplace) {
		if (!dev.schematicannon.compat.ClaimCompat.canModify(world, owner, pos, state, toReplace))
			return false;
		if (NtmCompat.isMultiblockCore(state) && schematic != null && schematicAnchor != null) {
			BlockPos local = pos.subtract(schematicAnchor);
			for (BlockPos cell : BlockPos.betweenClosed(local.offset(-8, -8, -8), local.offset(8, 8, 8))) {
				BlockState cellState = schematic.getLocal(cell);
				if (!NtmCompat.isMultiblockCell(cellState))
					continue;
				BlockPos worldCell = cell.offset(schematicAnchor);
				if (!dev.schematicannon.compat.ClaimCompat.canModify(world, owner, worldCell, cellState, world.getBlockState(worldCell)))
					return false;
			}
		}
		return true;
	}

	public void setOwner(java.util.@Nullable UUID owner) {
		this.owner = owner;
	}

	/** True if the last block checked was skipped because of a land claim. Reading it clears it. */
	public boolean consumeClaimDenied() {
		boolean denied = lastDeniedByClaim;
		lastDeniedByClaim = false;
		return denied;
	}

	public ItemRequirement getCurrentRequirement() {
		if (schematic == null)
			return ItemRequirement.INVALID;
		if (printStage == PrintStage.ENTITIES) {
			CompoundTag nbt = schematic.entities().get(printingEntityIndex).nbt();
			return EntityType.by(TagValueInput.create(ProblemReporter.DISCARDING,
					net.minecraft.core.RegistryAccess.EMPTY, nbt))
				.map(ItemRequirement::ofEntity)
				.orElse(ItemRequirement.INVALID);
		}
		BlockPos target = getCurrentTarget();
		if (target == null)
			return ItemRequirement.INVALID;
		return requirementAt(target.subtract(schematicAnchor));
	}

	/**
	 * What a schematic position costs never changes during a print. Blocks with saved data (machines: blueprints,
	 * paint...) are expensive to price, so theirs is worked out once; plain blocks are cheap and not stored, which keeps
	 * memory flat on huge schematics.
	 */
	private final java.util.Map<BlockPos, ItemRequirement> requirementCache = new java.util.HashMap<>();

	private ItemRequirement requirementAt(BlockPos local) {
		CompoundTag data = schematic.getDataLocal(local);
		BlockState state = SchematicPlacement.setZeroAge(schematic.getLocal(local));
		if (data == null)
			return ItemRequirement.of(state, null);
		ItemRequirement cached = requirementCache.get(local);
		if (cached == null) {
			cached = ItemRequirement.of(state, data);
			requirementCache.put(local.immutable(), cached);
		}
		return cached;
	}

	public int markAllBlockRequirements(MaterialChecklist checklist, Level world, PlacementPredicate predicate) {
		if (schematic == null || schematicAnchor == null)
			return 0;
		int blocksToPlace = 0;
		for (var entry : schematic.localBlocks().entrySet()) {
			BlockPos worldPos = entry.getKey().offset(schematicAnchor);
			BlockState required = SchematicPlacement.setZeroAge(entry.getValue());

			if (!world.isLoaded(worldPos)) {
				checklist.warnBlockNotLoaded();
				continue;
			}
			if (!shouldPlaceBlock(world, predicate, worldPos))
				continue;
			if (shouldIgnoreForChecklist(required))
				continue;
			ItemRequirement requirement = requirementAt(entry.getKey());
			if (requirement.isEmpty() || requirement.isInvalid())
				continue;
			checklist.require(requirement);
			blocksToPlace++;
		}
		return blocksToPlace;
	}

	/** Resumable version of {@link #markAllBlockRequirements}: big schematics are counted over several ticks. */
	private java.util.@Nullable Iterator<java.util.Map.Entry<BlockPos, BlockState>> countIterator;

	public void startCounting() {
		countIterator = schematic == null ? null : schematic.localBlocks().entrySet().iterator();
	}

	public boolean isCounting() {
		return countIterator != null;
	}

	/** Counts up to {@code max} more blocks; @return how many of them still need placing. */
	public int countBlockRequirements(MaterialChecklist checklist, Level world, PlacementPredicate predicate, int max) {
		if (countIterator == null || schematic == null || schematicAnchor == null) {
			countIterator = null;
			return 0;
		}
		int blocksToPlace = 0;
		for (int i = 0; i < max && countIterator.hasNext(); i++) {
			var entry = countIterator.next();
			BlockPos worldPos = entry.getKey().offset(schematicAnchor);
			BlockState required = SchematicPlacement.setZeroAge(entry.getValue());
			if (!world.isLoaded(worldPos)) {
				checklist.warnBlockNotLoaded();
				continue;
			}
			if (!shouldPlaceBlock(world, predicate, worldPos) || shouldIgnoreForChecklist(required))
				continue;
			ItemRequirement requirement = requirementAt(entry.getKey());
			if (requirement.isEmpty() || requirement.isInvalid())
				continue;
			checklist.require(requirement);
			blocksToPlace++;
		}
		if (!countIterator.hasNext())
			countIterator = null;
		return blocksToPlace;
	}

	private static boolean shouldIgnoreForChecklist(BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
			return true;
		return state.hasProperty(BlockStateProperties.BED_PART)
			&& state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
	}

	public void markAllEntityRequirements(MaterialChecklist checklist) {
		if (schematic == null)
			return;
		for (SchematicFile.EntityEntry entity : schematic.entities()) {
			ItemRequirement requirement = EntityType.by(TagValueInput.create(ProblemReporter.DISCARDING,
					net.minecraft.core.RegistryAccess.EMPTY, entity.nbt()))
				.map(ItemRequirement::ofEntity)
				.orElse(ItemRequirement.INVALID);
			if (requirement.isEmpty() || requirement.isInvalid())
				continue;
			checklist.require(requirement);
		}
	}

	public boolean advanceCurrentPos() {
		if (schematic == null || schematicAnchor == null || currentPos == null)
			return false;
		List<SchematicFile.EntityEntry> entities = schematic.entities();
		BoundingBox bounds = schematic.bounds();

		do {
			if (printStage == PrintStage.BLOCKS) {
				while (tryAdvanceCurrentPos(bounds))
					deferredBlocks.add(currentPos);
			}

			if (printStage == PrintStage.DEFERRED_BLOCKS) {
				if (deferredBlocks.isEmpty())
					printStage = PrintStage.ENTITIES;
				else
					currentPos = deferredBlocks.remove(0);
			}

			if (printStage == PrintStage.ENTITIES) {
				if (printingEntityIndex + 1 < entities.size()) {
					printingEntityIndex++;
					currentPos = entities.get(printingEntityIndex).blockPos();
				} else {
					// Reached end of printing
					return false;
				}
			}
		} while (!bounds.isInside(currentPos));

		// More things available to print
		return true;
	}

	/** @return true if the new position holds a block that must be deferred. */
	private boolean tryAdvanceCurrentPos(BoundingBox bounds) {
		currentPos = currentPos.east();
		if (currentPos.getX() > bounds.maxX())
			currentPos = new BlockPos(bounds.minX(), currentPos.getY(), currentPos.getZ() + 1);
		if (currentPos.getZ() > bounds.maxZ())
			currentPos = new BlockPos(currentPos.getX(), currentPos.getY() + 1, bounds.minZ());

		// End of blocks reached
		if (currentPos.getY() > bounds.maxY()) {
			printStage = PrintStage.DEFERRED_BLOCKS;
			return false;
		}

		return SchematicPlacement.shouldDefer(schematic.getLocal(currentPos));
	}

	public void sendBlockUpdates(Level level) {
		if (schematic == null || schematicAnchor == null)
			return;
		// only the one-block shell around the build (the six faces), never the whole volume
		BoundingBox b = schematic.bounds().inflatedBy(1);
		java.util.function.Consumer<BlockPos> touch = local -> {
			BlockPos pos = local.offset(schematicAnchor);
			if (level.isLoaded(pos) && level.getFluidState(pos).is(Fluids.WATER))
				level.scheduleTick(pos.immutable(), Fluids.WATER, Fluids.WATER.getTickDelay(level));
		};
		for (int x = b.minX(); x <= b.maxX(); x++)
			for (int z = b.minZ(); z <= b.maxZ(); z++) {
				touch.accept(new BlockPos(x, b.minY(), z));
				touch.accept(new BlockPos(x, b.maxY(), z));
			}
		for (int y = b.minY() + 1; y < b.maxY(); y++) {
			for (int x = b.minX(); x <= b.maxX(); x++) {
				touch.accept(new BlockPos(x, y, b.minZ()));
				touch.accept(new BlockPos(x, y, b.maxZ()));
			}
			for (int z = b.minZ() + 1; z < b.maxZ(); z++) {
				touch.accept(new BlockPos(b.minX(), y, z));
				touch.accept(new BlockPos(b.maxX(), y, z));
			}
		}
	}
}
