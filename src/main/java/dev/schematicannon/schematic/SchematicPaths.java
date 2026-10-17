package dev.schematicannon.schematic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.schematicannon.Schematicannon;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Same folder layout as Create, so existing Create schematics can be dropped straight in:
 * {@code .minecraft/schematics/} on the client, {@code <server>/schematics/uploaded/<player>/} on the server.
 */
public final class SchematicPaths {
	public static final Path SCHEMATICS_DIR = FMLPaths.GAMEDIR.get().resolve("schematics").toAbsolutePath().normalize();
	public static final Path UPLOADED_SCHEMATICS_DIR = SCHEMATICS_DIR.resolve("uploaded").toAbsolutePath().normalize();

	public static void createFolderIfMissing(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException e) {
			Schematicannon.LOGGER.warn("Could not create folder: {}", path, e);
		}
	}

	public static String findFirstValidFilename(String name, Path folder, String extension) {
		int index = 0;
		String filename;
		Path file;
		do {
			filename = slug(name) + (index == 0 ? "" : "_" + index) + "." + extension;
			file = folder.resolve(filename);
			index++;
		} while (Files.exists(file));
		return filename;
	}

	public static String slug(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
	}

	private SchematicPaths() {}
}
