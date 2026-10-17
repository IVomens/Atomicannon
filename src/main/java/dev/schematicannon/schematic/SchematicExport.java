package dev.schematicannon.schematic;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class SchematicExport {

	/**
	 * Save a schematic to a file from a world.
	 *
	 * @return the result, or null if an error occurred.
	 */
	public static @Nullable SchematicExportResult saveSchematic(Path dir, String fileName, boolean overwrite, Level level,
		BlockPos first, BlockPos second) {
		BoundingBox bb = BoundingBox.fromCorners(first, second);
		BlockPos origin = new BlockPos(bb.minX(), bb.minY(), bb.minZ());
		BlockPos bounds = new BlockPos(bb.getXSpan(), bb.getYSpan(), bb.getZSpan());

		StructureTemplate structure = new StructureTemplate();
		structure.fillFromWorld(level, origin, new Vec3i(bounds.getX(), bounds.getY(), bounds.getZ()), true, List.of(Blocks.AIR));
		CompoundTag data = structure.save(new CompoundTag());
		SchematicFile.replaceStructureVoidWithAir(data);
		dev.schematicannon.compat.NtmCompat.recordBlockSettings(level, origin, bounds, data);
		data.putIntArray(SchematicFile.SAVED_AT, new int[] { origin.getX(), origin.getY(), origin.getZ() });

		if (fileName.isEmpty())
			fileName = Component.translatable("schematicannon.schematicAndQuill.fallbackName").getString();
		if (fileName.endsWith(".nbt"))
			fileName = fileName.substring(0, fileName.length() - 4);
		if (!overwrite)
			fileName = SchematicPaths.findFirstValidFilename(fileName, dir, "nbt");
		else
			fileName = SchematicPaths.slug(fileName) + ".nbt";

		Path file = dir.resolve(fileName).toAbsolutePath().normalize();
		if (!file.startsWith(dir.toAbsolutePath().normalize()))
			return null;

		try {
			Files.createDirectories(dir);
			boolean overwritten = Files.deleteIfExists(file);
			try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE)) {
				NbtIo.writeCompressed(data, out);
			}
			return new SchematicExportResult(file, dir, fileName, overwritten, origin, bounds);
		} catch (IOException e) {
			Schematicannon.LOGGER.error("An error occurred while saving schematic [{}]", fileName, e);
			return null;
		}
	}

	public record SchematicExportResult(Path file, Path dir, String fileName, boolean overwritten, BlockPos origin,
		BlockPos bounds) {}

	private SchematicExport() {}
}
