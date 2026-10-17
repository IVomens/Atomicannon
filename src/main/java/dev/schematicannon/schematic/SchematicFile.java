package dev.schematicannon.schematic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The raw contents of a vanilla structure (.nbt) file, read directly so we do not need
 * {@code StructureTemplate}'s private palette data. Only the first palette is used, like Create.
 */
public record SchematicFile(Vec3i size, List<BlockEntry> blocks, List<EntityEntry> entities, @Nullable BlockPos savedAt) {
	public static final SchematicFile EMPTY = new SchematicFile(Vec3i.ZERO, List.of(), List.of(), null);
	/** Top-level tag: the world position the schematic's corner was saved from (lets linked blocks be re-aimed). */
	public static final String SAVED_AT = "schematicannon:savedAt";

	public record BlockEntry(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {}

	public record EntityEntry(Vec3 pos, BlockPos blockPos, CompoundTag nbt) {}

	public boolean isEmpty() {
		return size.equals(Vec3i.ZERO);
	}

	/** Largest schematic edge accepted; anything bigger is treated as a broken or malicious file. */
	public static final int MAX_DIMENSION = 1024;
	/** Uncompressed NBT limit: a small upload must not be able to inflate into a huge allocation (zip bomb). */
	private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;

	private record Cached(long modified, long size, java.lang.ref.SoftReference<SchematicFile> file) {}

	/**
	 * Recently read files: many cannons on one schematic (and every restart of a print) would otherwise each read and
	 * decode the whole file again. Checked against size and modification time, so a re-upload is always re-read.
	 * Entries are soft references, so the cache gives memory back under pressure. The data is never modified by
	 * users of a SchematicFile (block entity data is copied before use).
	 */
	private static final java.util.Map<Path, Cached> CACHE = new java.util.LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<Path, Cached> eldest) {
			return size() > 8;
		}
	};

	public static SchematicFile read(Path path, HolderGetter<Block> blocks) {
		long modified, size;
		try {
			modified = Files.getLastModifiedTime(path).toMillis();
			size = Files.size(path);
		} catch (IOException e) {
			return EMPTY;
		}
		synchronized (CACHE) {
			Cached cached = CACHE.get(path);
			SchematicFile file = cached == null ? null : cached.file().get();
			if (file != null && cached.modified() == modified && cached.size() == size)
				return file;
		}
		SchematicFile file = readUncached(path, blocks);
		synchronized (CACHE) {
			CACHE.put(path, new Cached(modified, size, new java.lang.ref.SoftReference<>(file)));
		}
		return file;
	}

	private static SchematicFile readUncached(Path path, HolderGetter<Block> blocks) {
		try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
			return parse(NbtIo.readCompressed(in, NbtAccounter.create(MAX_NBT_BYTES)), blocks);
		} catch (IOException | RuntimeException e) {
			// NbtAccounter throws a RuntimeException (NbtAccounterException) past its limit
			Schematicannon.LOGGER.warn("Failed to read schematic {}: {}", path, e.toString());
			return EMPTY;
		}
	}

	public static SchematicFile parse(CompoundTag tag, HolderGetter<Block> blockLookup) {
		ListTag sizeTag = tag.getListOrEmpty("size");
		Vec3i size = new Vec3i(sizeTag.getIntOr(0, 0), sizeTag.getIntOr(1, 0), sizeTag.getIntOr(2, 0));
		if (size.getX() < 0 || size.getY() < 0 || size.getZ() < 0 || size.getX() > MAX_DIMENSION
			|| size.getY() > MAX_DIMENSION || size.getZ() > MAX_DIMENSION) {
			Schematicannon.LOGGER.warn("Rejected schematic with size {} (max {} per side)", size, MAX_DIMENSION);
			return EMPTY;
		}

		ListTag paletteTag = tag.getList("palettes")
			.flatMap(palettes -> palettes.getList(0))
			.orElseGet(() -> tag.getListOrEmpty("palette"));

		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++)
			palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompoundOrEmpty(i)));

		List<BlockEntry> blocks = new ArrayList<>();
		ListTag blocksTag = tag.getListOrEmpty("blocks");
		for (int i = 0; i < blocksTag.size(); i++) {
			CompoundTag entry = blocksTag.getCompoundOrEmpty(i);
			ListTag posTag = entry.getListOrEmpty("pos");
			BlockPos pos = new BlockPos(posTag.getIntOr(0, 0), posTag.getIntOr(1, 0), posTag.getIntOr(2, 0));
			int stateId = entry.getIntOr("state", 0);
			BlockState state = stateId >= 0 && stateId < palette.size() ? palette.get(stateId) : Blocks.AIR.defaultBlockState();
			if (state.is(Blocks.STRUCTURE_VOID))
				continue;
			CompoundTag nbt = entry.getCompound("nbt").orElse(null);
			blocks.add(new BlockEntry(pos, state, nbt));
		}

		List<EntityEntry> entities = new ArrayList<>();
		ListTag entitiesTag = tag.getListOrEmpty("entities");
		for (int i = 0; i < entitiesTag.size(); i++) {
			CompoundTag entry = entitiesTag.getCompoundOrEmpty(i);
			ListTag posTag = entry.getListOrEmpty("pos");
			ListTag blockPosTag = entry.getListOrEmpty("blockPos");
			Vec3 pos = new Vec3(posTag.getDoubleOr(0, 0), posTag.getDoubleOr(1, 0), posTag.getDoubleOr(2, 0));
			BlockPos blockPos = new BlockPos(blockPosTag.getIntOr(0, 0), blockPosTag.getIntOr(1, 0), blockPosTag.getIntOr(2, 0));
			entry.getCompound("nbt").ifPresent(nbt -> entities.add(new EntityEntry(pos, blockPos, nbt)));
		}

		BlockPos savedAt = tag.getIntArray(SAVED_AT).filter(a -> a.length == 3).map(a -> new BlockPos(a[0], a[1], a[2]))
			.orElse(null);
		return new SchematicFile(size, blocks, entities, savedAt);
	}

	/** Turns Structure Void into Air, as Create's Schematic and Quill does. */
	public static void replaceStructureVoidWithAir(CompoundTag nbt) {
		String structureVoid = "minecraft:structure_void";
		ListTag palette = nbt.getListOrEmpty("palette");
		for (int i = 0; i < palette.size(); i++) {
			Tag t = palette.get(i);
			if (t instanceof CompoundTag c && structureVoid.equals(c.getStringOr("Name", "")))
				c.putString("Name", "minecraft:air");
		}
	}
}
