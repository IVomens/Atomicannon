package dev.schematicannon.schematic;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.config.ServerConfig;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModItems;
import dev.schematicannon.table.SchematicTableBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

/** Port of Create's ServerSchematicLoader: receives schematic files from clients in small chunks. */
public class ServerSchematicLoader {

	private final Map<String, SchematicUploadEntry> activeUploads = new HashMap<>();
	private final List<String> deadEntries = new ArrayList<>();
	private final Map<java.util.UUID, Long> lastInstantSave = new HashMap<>();

	public static class SchematicUploadEntry {
		public final Level world;
		public final BlockPos tablePos;
		public final OutputStream stream;
		public long bytesUploaded;
		public final long totalBytes;
		public int idleTime;

		public SchematicUploadEntry(OutputStream stream, long totalBytes, Level world, BlockPos tablePos) {
			this.stream = stream;
			this.totalBytes = totalBytes;
			this.tablePos = tablePos;
			this.world = world;
		}
	}

	public void tick() {
		if (activeUploads.isEmpty())
			return;
		// Detect Timed out Uploads
		int timeout = ServerConfig.SCHEMATIC_IDLE_TIMEOUT.get();
		for (var entry : activeUploads.entrySet()) {
			if (entry.getValue().idleTime++ > timeout) {
				Schematicannon.LOGGER.warn("Schematic Upload timed out: {}", entry.getKey());
				deadEntries.add(entry.getKey());
			}
		}

		// Remove Timed out Uploads
		deadEntries.forEach(this::cancelUpload);
		deadEntries.clear();
	}

	public void shutdown() {
		// Close open streams
		new HashSet<>(activeUploads.keySet()).forEach(this::cancelUpload);
	}

	private static @Nullable Path resolvePlayerPath(String playerName, String schematic) {
		Path baseDir = SchematicPaths.UPLOADED_SCHEMATICS_DIR;
		Path playerPath = baseDir.resolve(playerName).normalize();
		Path uploadPath;
		try {
			uploadPath = playerPath.resolve(schematic).normalize();
		} catch (RuntimeException e) {
			return null;
		}
		if (!playerPath.startsWith(baseDir) || !uploadPath.startsWith(playerPath) || uploadPath.equals(playerPath))
			return null;
		return uploadPath;
	}

	public void handleNewUpload(ServerPlayer player, String schematic, long size, BlockPos pos) {
		String playerName = player.getPlainTextName();
		String playerSchematicId = playerName + "/" + schematic;
		Path uploadPath = resolvePlayerPath(playerName, schematic);

		if (uploadPath == null) {
			Schematicannon.LOGGER.warn("Attempted Schematic Upload with path traversal: {}", playerSchematicId);
			return;
		}
		Path playerPath = uploadPath.getParent();
		SchematicPaths.createFolderIfMissing(playerPath);

		// Unsupported Format
		if (!schematic.endsWith(".nbt")) {
			Schematicannon.LOGGER.warn("Attempted Schematic Upload with non-supported Format: {}", playerSchematicId);
			return;
		}

		// Too big
		if (!validateSchematicSizeOnServer(player, size))
			return;

		// Skip existing Uploads
		if (activeUploads.containsKey(playerSchematicId))
			return;

		try {
			// Validate Referenced Block
			SchematicTableBlockEntity table = getTable(player.level(), pos);
			if (table == null || !table.canStartUpload())
				return;

			// Delete schematic with same name
			Files.deleteIfExists(uploadPath);

			// Too many Schematics
			if (!tryDeleteOldestSchematic(playerPath))
				return;

			// Open Stream
			OutputStream writer = Files.newOutputStream(uploadPath);
			activeUploads.put(playerSchematicId, new SchematicUploadEntry(writer, size, player.level(), pos));

			// Notify Block Entity
			table.startUpload(schematic);
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Exception Thrown when starting Upload: {}", playerSchematicId, e);
		}
	}

	protected boolean validateSchematicSizeOnServer(ServerPlayer player, long size) {
		long maxFileSize = ServerConfig.MAX_TOTAL_SCHEMATIC_SIZE.get();
		if (size > maxFileSize * 1000) {
			player.sendSystemMessage(Component.translatable("schematicannon.schematics.uploadTooLarge")
				.append(Component.literal(" (" + size / 1000 + " KB).")));
			player.sendSystemMessage(Component.translatable("schematicannon.schematics.maxAllowedSize")
				.append(Component.literal(" " + maxFileSize + " KB")));
			return false;
		}
		return true;
	}

	public void handleWriteRequest(ServerPlayer player, String schematic, byte[] data) {
		String playerSchematicId = player.getPlainTextName() + "/" + schematic;
		SchematicUploadEntry entry = activeUploads.get(playerSchematicId);
		if (entry == null)
			return;

		entry.bytesUploaded += data.length;

		// Size Validations
		if (data.length > ServerConfig.MAX_SCHEMATIC_PACKET_SIZE.get()) {
			Schematicannon.LOGGER.warn("Oversized Upload Packet received: {}", playerSchematicId);
			cancelUpload(playerSchematicId);
			return;
		}

		if (entry.bytesUploaded > entry.totalBytes) {
			Schematicannon.LOGGER.warn("Received more data than Expected: {}", playerSchematicId);
			cancelUpload(playerSchematicId);
			return;
		}

		try {
			entry.stream.write(data);
			entry.idleTime = 0;

			SchematicTableBlockEntity table = getTable(entry.world, entry.tablePos);
			if (table == null)
				return;
			table.uploadingProgress = (float) ((double) entry.bytesUploaded / entry.totalBytes);
			table.sendUpdate = true;
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Exception Thrown when uploading Schematic: {}", playerSchematicId, e);
			cancelUpload(playerSchematicId);
		}
	}

	protected void cancelUpload(String playerSchematicId) {
		SchematicUploadEntry entry = activeUploads.remove(playerSchematicId);
		if (entry == null)
			return;

		try {
			entry.stream.close();
			Files.deleteIfExists(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve(playerSchematicId));
			Schematicannon.LOGGER.warn("Cancelled Schematic Upload: {}", playerSchematicId);
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Exception Thrown when cancelling Upload: {}", playerSchematicId, e);
		}

		SchematicTableBlockEntity table = getTable(entry.world, entry.tablePos);
		if (table != null)
			table.cancelUpload();
	}

	public @Nullable SchematicTableBlockEntity getTable(Level world, BlockPos pos) {
		if (!world.isLoaded(pos))
			return null;
		return world.getBlockEntity(pos) instanceof SchematicTableBlockEntity table ? table : null;
	}

	public void handleFinishedUpload(ServerPlayer player, String schematic) {
		String playerSchematicId = player.getPlainTextName() + "/" + schematic;
		SchematicUploadEntry removed = activeUploads.remove(playerSchematicId);
		if (removed == null)
			return;

		try {
			removed.stream.close();
			Schematicannon.LOGGER.info("New Schematic Uploaded: {}", playerSchematicId);

			Level world = removed.world;
			if (!world.getBlockState(removed.tablePos).is(ModBlocks.SCHEMATIC_TABLE.get()))
				return;

			SchematicTableBlockEntity table = getTable(world, removed.tablePos);
			if (table == null)
				return;
			table.finishUpload();
			table.inventory.setItem(1, SchematicItem.create(world, schematic, player.getPlainTextName()));
		} catch (IOException e) {
			Schematicannon.LOGGER.error("Exception Thrown when finishing Upload: {}", playerSchematicId, e);
		}
	}

	public void handleInstantSchematic(ServerPlayer player, String schematic, Level world, BlockPos pos, BlockPos bounds) {
		String playerName = player.getPlainTextName();
		String playerSchematicId = playerName + "/" + schematic;
		Path uploadPath = resolvePlayerPath(playerName, schematic);

		if (uploadPath == null) {
			Schematicannon.LOGGER.warn("Attempted Schematic Upload with path traversal: {}", playerSchematicId);
			return;
		}
		Path playerPath = uploadPath.getParent();
		SchematicPaths.createFolderIfMissing(playerPath);

		// Unsupported Format
		if (!schematic.endsWith(".nbt")) {
			Schematicannon.LOGGER.warn("Attempted Schematic Upload with non-supported Format: {}", playerSchematicId);
			return;
		}

		// Not holding S&Q
		if (!player.getMainHandItem().is(ModItems.SCHEMATIC_AND_QUILL.get()))
			return;

		// Reject absurd selections and selections far away from the player
		if (bounds.getX() <= 0 || bounds.getY() <= 0 || bounds.getZ() <= 0 || bounds.getX() > 512 || bounds.getY() > 512
			|| bounds.getZ() > 512 || !pos.closerThan(player.blockPosition(), 512))
			return;

		// The copy runs in one tick on the server: keep it small, only over loaded chunks, and not spammable.
		long volume = (long) bounds.getX() * bounds.getY() * bounds.getZ();
		if (volume > ServerConfig.MAX_INSTANT_SAVE_VOLUME.get()) {
			player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.instant_tooBig")
				.withStyle(ChatFormatting.RED));
			return;
		}
		long now = world.getGameTime();
		Long last = lastInstantSave.get(player.getUUID());
		if (last != null && now - last < 40)
			return;
		BlockPos far = pos.offset(bounds).offset(-1, -1, -1);
		for (int cx = Math.min(pos.getX(), far.getX()) >> 4; cx <= Math.max(pos.getX(), far.getX()) >> 4; cx++)
			for (int cz = Math.min(pos.getZ(), far.getZ()) >> 4; cz <= Math.max(pos.getZ(), far.getZ()) >> 4; cz++)
				if (!world.hasChunk(cx, cz)) {
					player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.instant_notLoaded")
						.withStyle(ChatFormatting.RED));
					return;
				}
		lastInstantSave.put(player.getUUID(), now);

		// if there's too many schematics, delete oldest
		if (!tryDeleteOldestSchematic(playerPath))
			return;

		SchematicExport.SchematicExportResult result = SchematicExport.saveSchematic(playerPath, schematic, true, world, pos,
			pos.offset(bounds).offset(-1, -1, -1));
		if (result != null)
			player.setItemInHand(InteractionHand.MAIN_HAND, SchematicItem.create(world, result.fileName(), playerName));
		else
			player.sendOverlayMessage(Component.translatable("schematicannon.schematicAndQuill.instant_failed")
				.withStyle(ChatFormatting.RED));
	}

	private final Map<java.util.UUID, Long> lastDataRequest = new HashMap<>();

	/**
	 * A Schematic and Quill save made on the player's computer only holds what the server sent that client. This sends
	 * back the full settings of NTM machines in the area (contents emptied, like for pasting), only for blocks the player
	 * may build on (land claims), with the same size, distance, loaded-chunk and cooldown limits as an instant save.
	 */
	public void handleServerDataRequest(ServerPlayer player, String fileName, BlockPos pos, BlockPos bounds) {
		var level = player.level();
		if (bounds.getX() <= 0 || bounds.getY() <= 0 || bounds.getZ() <= 0 || bounds.getX() > 512 || bounds.getY() > 512
			|| bounds.getZ() > 512 || !pos.closerThan(player.blockPosition(), 512)
			|| (long) bounds.getX() * bounds.getY() * bounds.getZ() > ServerConfig.MAX_INSTANT_SAVE_VOLUME.get())
			return;
		long now = level.getGameTime();
		Long last = lastDataRequest.get(player.getUUID());
		if (last != null && now - last < 20)
			return;
		lastDataRequest.put(player.getUUID(), now);

		BlockPos far = pos.offset(bounds).offset(-1, -1, -1);
		var box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(pos, far);
		var positions = new ArrayList<BlockPos>();
		var data = new ArrayList<net.minecraft.nbt.CompoundTag>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				if (!level.hasChunk(cx, cz))
					return; // never load chunks for this
				for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
					if (!box.isInside(be.getBlockPos()) || positions.size() >= ModPacketsLimits.MAX_ENTRIES)
						continue;
					var settings = dev.schematicannon.compat.NtmCompat.shareableSettings(level, be, player.getUUID());
					if (settings != null) {
						positions.add(be.getBlockPos().subtract(pos));
						data.add(settings);
					}
				}
			}
		if (!positions.isEmpty())
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
				new dev.schematicannon.network.ModPackets.ServerData(fileName, positions, data));
	}

	private static final class ModPacketsLimits {
		static final int MAX_ENTRIES = dev.schematicannon.network.ModPackets.ServerData.MAX_ENTRIES;
	}

	private boolean tryDeleteOldestSchematic(Path dir) {
		try (Stream<Path> stream = Files.list(dir)) {
			List<Path> files = stream.filter(f -> !Files.isDirectory(f)).toList();
			if (files.size() < ServerConfig.MAX_SCHEMATICS.get())
				return true;
			Optional<Path> oldest = files.stream().min(Comparator.comparingLong(ServerSchematicLoader::getLastModifiedTime));
			if (oldest.isPresent())
				Files.delete(oldest.get());
			return true;
		} catch (IOException | IllegalStateException e) {
			Schematicannon.LOGGER.error("Error deleting oldest schematic", e);
			return false;
		}
	}

	private static long getLastModifiedTime(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
