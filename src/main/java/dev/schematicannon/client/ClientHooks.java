package dev.schematicannon.client;

/**
 * Small bridge from common code into client-only code. Only ever invoked on the logical client,
 * so the client classes it touches are never loaded on a dedicated server.
 */
public final class ClientHooks {
	public static void openSchematicEditScreen() {
		net.minecraft.client.Minecraft.getInstance().gui.setScreen(new dev.schematicannon.client.screen.SchematicEditScreen());
	}

	public static void openClipboard(net.minecraft.world.InteractionHand hand) {
		net.minecraft.client.Minecraft.getInstance().gui.setScreen(new dev.schematicannon.client.screen.ClipboardScreen(hand));
	}

	/** Puts the server's machine settings into a schematic just saved on this computer (see ModPackets.ServerData). */
	public static void applyServerData(String fileName, java.util.List<net.minecraft.core.BlockPos> positions,
		java.util.List<net.minecraft.nbt.CompoundTag> data) {
		var dir = dev.schematicannon.schematic.SchematicPaths.SCHEMATICS_DIR;
		java.nio.file.Path path;
		try {
			path = dir.resolve(fileName).normalize();
		} catch (RuntimeException e) {
			return;
		}
		if (!path.startsWith(dir) || !java.nio.file.Files.isRegularFile(path) || positions.size() != data.size())
			return;
		try {
			var tag = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.create(64L * 1024 * 1024));
			var blocks = tag.getListOrEmpty("blocks");
			var byPos = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.nbt.CompoundTag>();
			for (int i = 0; i < blocks.size(); i++) {
				var entry = blocks.getCompoundOrEmpty(i);
				var p = entry.getListOrEmpty("pos");
				byPos.put(new net.minecraft.core.BlockPos(p.getIntOr(0, 0), p.getIntOr(1, 0), p.getIntOr(2, 0)), entry);
			}
			String key = dev.schematicannon.schematic.SchematicPlacement.SETTINGS_KEY;
			int updated = 0;
			for (int i = 0; i < positions.size(); i++) {
				var entry = byPos.get(positions.get(i));
				if (entry == null)
					continue;
				var nbt = data.get(i).copy();
				// keep what the save itself recorded next to the block data (duct fluids, links)
				entry.getCompound("nbt").flatMap(old -> old.getCompound(key)).ifPresent(s -> nbt.put(key, s));
				entry.put("nbt", nbt);
				updated++;
			}
			if (updated == 0)
				return;
			net.minecraft.nbt.NbtIo.writeCompressed(tag, path);
			var player = net.minecraft.client.Minecraft.getInstance().player;
			if (player != null)
				player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
					"schematicannon.schematicAndQuill.serverSettings", updated));
		} catch (java.io.IOException | RuntimeException e) {
			dev.schematicannon.Schematicannon.LOGGER.warn("Could not add server machine settings to {}", path, e);
		}
	}

	private ClientHooks() {}
}
