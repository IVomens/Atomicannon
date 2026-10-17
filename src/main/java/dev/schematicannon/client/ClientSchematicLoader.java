package dev.schematicannon.client;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.config.ServerConfig;
import dev.schematicannon.network.ModPackets;
import dev.schematicannon.schematic.SchematicPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Port of Create's ClientSchematicLoader: lists local schematics and streams one to the server. */
public class ClientSchematicLoader {
	public static final int PACKET_DELAY = 10;

	private final List<Component> availableSchematics = new ArrayList<>();
	private final Map<String, InputStream> activeUploads = new HashMap<>();
	private int packetCycle;

	public ClientSchematicLoader() {
		refresh();
	}

	public void tick() {
		if (activeUploads.isEmpty())
			return;
		if (packetCycle-- > 0)
			return;
		packetCycle = PACKET_DELAY;
		for (String schematic : new HashSet<>(activeUploads.keySet()))
			continueUpload(schematic);
	}

	public void startNewUpload(String schematic) {
		Path path = SchematicPaths.SCHEMATICS_DIR.resolve(schematic).normalize();
		if (!path.startsWith(SchematicPaths.SCHEMATICS_DIR) || !Files.exists(path)) {
			Schematicannon.LOGGER.error("Missing Schematic file: {}", path);
			return;
		}

		try {
			long size = Files.size(path);
			// Too big
			if (!validateSizeLimitation(size))
				return;
			// Validate if the file is encoded in a GZIP compatible format
			if (!isGZIPEncoded(path)) {
				LocalPlayer player = Minecraft.getInstance().player;
				if (player != null)
					player.sendSystemMessage(Component.translatable("schematicannon.schematics.wrongFormat"));
				return;
			}
			InputStream in = Files.newInputStream(path, StandardOpenOption.READ);
			activeUploads.put(schematic, in);
			ClientPacketDistributor.sendToServer(ModPackets.SchematicUpload.begin(schematic, size));
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Encountered an error while starting schematic upload", e);
		}
	}

	public static boolean validateSizeLimitation(long size) {
		if (Minecraft.getInstance().hasSingleplayerServer())
			return true;
		int maxSize = ServerConfig.SPEC.isLoaded() ? ServerConfig.MAX_TOTAL_SCHEMATIC_SIZE.get() : 256;
		if (size > maxSize * 1000L) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				player.sendSystemMessage(Component.translatable("schematicannon.schematics.uploadTooLarge")
					.append(" (" + size / 1000 + " KB)."));
				player.sendSystemMessage(Component.translatable("schematicannon.schematics.maxAllowedSize")
					.append(" " + maxSize + " KB"));
			}
			return false;
		}
		return true;
	}

	/** GZIP files start with the magic number 0x1F 0x8B. */
	public static boolean isGZIPEncoded(Path file) {
		try (FileInputStream fis = new FileInputStream(file.toFile())) {
			byte[] bytes = new byte[2];
			if (fis.read(bytes) != 2)
				return false;
			return (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
		} catch (IOException exception) {
			return false;
		}
	}

	private void continueUpload(String schematic) {
		InputStream in = activeUploads.get(schematic);
		if (in == null)
			return;
		// Client copy of the server config is synced; fall back to Create's default.
		int maxPacketSize = ServerConfig.SPEC.isLoaded() ? ServerConfig.MAX_SCHEMATIC_PACKET_SIZE.get() : 1024;
		byte[] data = new byte[maxPacketSize];
		try {
			int status = in.read(data);
			if (status != -1) {
				if (status < maxPacketSize)
					data = Arrays.copyOf(data, status);
				if (Minecraft.getInstance().level != null)
					ClientPacketDistributor.sendToServer(ModPackets.SchematicUpload.write(schematic, data));
				else {
					closeQuietly(activeUploads.remove(schematic));
					return;
				}
			}
			if (status < maxPacketSize)
				finishUpload(schematic);
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Encountered a error while uploading schematic", e);
			closeQuietly(activeUploads.remove(schematic));
		}
	}

	private void finishUpload(String schematic) {
		InputStream in = activeUploads.remove(schematic);
		if (in != null) {
			ClientPacketDistributor.sendToServer(ModPackets.SchematicUpload.finish(schematic));
			closeQuietly(in);
		}
	}

	private static void closeQuietly(InputStream in) {
		if (in == null)
			return;
		try {
			in.close();
		} catch (IOException ignored) {
		}
	}

	public void refresh() {
		SchematicPaths.createFolderIfMissing(SchematicPaths.SCHEMATICS_DIR);
		availableSchematics.clear();

		try (Stream<Path> paths = Files.list(SchematicPaths.SCHEMATICS_DIR)) {
			paths.filter(f -> !Files.isDirectory(f) && f.getFileName().toString().endsWith(".nbt"))
				.forEach(path -> availableSchematics.add(Component.literal(path.getFileName().toString())));
		} catch (NoSuchFileException ignored) {
			// No Schematics created yet
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Failed to refresh schematics", e);
		}

		availableSchematics.sort((a, b) -> naturalCompare(a.getString(), b.getString()));
	}

	/** Sorts "house2" before "house10", like Create. */
	private static int naturalCompare(String a, String b) {
		int i = 0, j = 0;
		while (i < a.length() && j < b.length()) {
			char ca = a.charAt(i), cb = b.charAt(j);
			if (Character.isDigit(ca) && Character.isDigit(cb)) {
				int si = i, sj = j;
				while (i < a.length() && Character.isDigit(a.charAt(i)))
					i++;
				while (j < b.length() && Character.isDigit(b.charAt(j)))
					j++;
				String na = a.substring(si, i).replaceFirst("^0+(?!$)", "");
				String nb = b.substring(sj, j).replaceFirst("^0+(?!$)", "");
				if (na.length() != nb.length())
					return na.length() - nb.length();
				int cmp = na.compareTo(nb);
				if (cmp != 0)
					return cmp;
			} else {
				if (ca != cb)
					return ca - cb;
				i++;
				j++;
			}
		}
		return (a.length() - i) - (b.length() - j);
	}

	public List<Component> getAvailableSchematics() {
		return availableSchematics;
	}
}
