package dev.schematicannon.network;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.ConfigureSchematicannonOption;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.cannon.SchematicannonMenu;
import dev.schematicannon.config.ServerConfig;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.registry.ModItems;
import dev.schematicannon.schematic.SchematicPlacement;
import dev.schematicannon.schematic.SchematicPrinter;
import dev.schematicannon.table.SchematicTableMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPackets {

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToServer(SchematicUpload.TYPE, SchematicUpload.STREAM_CODEC, ModPackets::handleUpload);
		registrar.playToServer(SchematicSync.TYPE, SchematicSync.STREAM_CODEC, ModPackets::handleSync);
		registrar.playToServer(SchematicPlace.TYPE, SchematicPlace.STREAM_CODEC, ModPackets::handlePlace);
		registrar.playToServer(InstantSchematic.TYPE, InstantSchematic.STREAM_CODEC, ModPackets::handleInstant);
		registrar.playToServer(ConfigureSchematicannon.TYPE, ConfigureSchematicannon.STREAM_CODEC, ModPackets::handleConfigure);
		registrar.playToServer(EditClipboard.TYPE, EditClipboard.STREAM_CODEC, ModPackets::handleEditClipboard);
		registrar.playToServer(ServerDataRequest.TYPE, ServerDataRequest.STREAM_CODEC, ModPackets::handleServerDataRequest);
		registrar.playToClient(ServerData.TYPE, ServerData.STREAM_CODEC, ModPackets::handleServerData);
	}

	// ------------------------------------------------------------------ server machine settings for a local save

	/**
	 * After a Schematic and Quill save on the player's computer: asks the server for the machine settings in that area.
	 * The client only knows what the server sent it (no filter items, links, ...), the server knows everything.
	 */
	public record ServerDataRequest(String fileName, BlockPos origin, BlockPos bounds) implements CustomPacketPayload {
		public static final Type<ServerDataRequest> TYPE = new Type<>(Schematicannon.id("server_data_request"));
		public static final StreamCodec<ByteBuf, ServerDataRequest> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(256), ServerDataRequest::fileName,
			BlockPos.STREAM_CODEC, ServerDataRequest::origin,
			BlockPos.STREAM_CODEC, ServerDataRequest::bounds,
			ServerDataRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Machine settings (contents already emptied) by position relative to the saved area's corner. */
	public record ServerData(String fileName, java.util.List<BlockPos> positions, java.util.List<net.minecraft.nbt.CompoundTag> data)
		implements CustomPacketPayload {
		public static final int MAX_ENTRIES = 4096;
		public static final Type<ServerData> TYPE = new Type<>(Schematicannon.id("server_data"));
		public static final StreamCodec<ByteBuf, ServerData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(256), ServerData::fileName,
			BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), ServerData::positions,
			ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list(MAX_ENTRIES)), ServerData::data,
			ServerData::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void handleServerDataRequest(ServerDataRequest packet, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			Schematicannon.SCHEMATIC_RECEIVER.handleServerDataRequest(player, packet.fileName(), packet.origin(), packet.bounds());
	}

	private static void handleServerData(ServerData packet, IPayloadContext context) {
		dev.schematicannon.client.ClientHooks.applyServerData(packet.fileName(), packet.positions(), packet.data());
	}

	// ------------------------------------------------------------------ upload

	public record SchematicUpload(int code, long size, String schematic, byte[] data) implements CustomPacketPayload {
		public static final int BEGIN = 0;
		public static final int WRITE = 1;
		public static final int FINISH = 2;

		public static final Type<SchematicUpload> TYPE = new Type<>(Schematicannon.id("upload_schematic"));
		public static final StreamCodec<ByteBuf, SchematicUpload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SchematicUpload::code,
			ByteBufCodecs.VAR_LONG, SchematicUpload::size,
			ByteBufCodecs.stringUtf8(256), SchematicUpload::schematic,
			ByteBufCodecs.byteArray(32767), SchematicUpload::data,
			SchematicUpload::new);

		public static SchematicUpload begin(String schematic, long size) {
			return new SchematicUpload(BEGIN, size, schematic, new byte[0]);
		}

		public static SchematicUpload write(String schematic, byte[] data) {
			return new SchematicUpload(WRITE, 0, schematic, data);
		}

		public static SchematicUpload finish(String schematic) {
			return new SchematicUpload(FINISH, 0, schematic, new byte[0]);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void handleUpload(SchematicUpload packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		switch (packet.code()) {
			case SchematicUpload.BEGIN -> {
				if (player.containerMenu instanceof SchematicTableMenu menu && menu.contentHolder != null)
					Schematicannon.SCHEMATIC_RECEIVER.handleNewUpload(player, packet.schematic(), packet.size(),
						menu.contentHolder.getBlockPos());
			}
			case SchematicUpload.WRITE -> Schematicannon.SCHEMATIC_RECEIVER.handleWriteRequest(player, packet.schematic(), packet.data());
			case SchematicUpload.FINISH -> Schematicannon.SCHEMATIC_RECEIVER.handleFinishedUpload(player, packet.schematic());
			default -> {}
		}
	}

	// ------------------------------------------------------------------ sync (deploy / move / rotate / flip)

	public record SchematicSync(int slot, boolean deployed, BlockPos anchor, Rotation rotation, Mirror mirror)
		implements CustomPacketPayload {
		public static final Type<SchematicSync> TYPE = new Type<>(Schematicannon.id("sync_schematic"));
		public static final StreamCodec<ByteBuf, SchematicSync> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SchematicSync::slot,
			ByteBufCodecs.BOOL, SchematicSync::deployed,
			BlockPos.STREAM_CODEC, SchematicSync::anchor,
			ModComponents.ROTATION_STREAM_CODEC, SchematicSync::rotation,
			ModComponents.MIRROR_STREAM_CODEC, SchematicSync::mirror,
			SchematicSync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void handleSync(SchematicSync packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		ItemStack stack;
		if (packet.slot() == -1)
			stack = player.getMainHandItem();
		else if (packet.slot() >= 0 && packet.slot() < 9)
			stack = player.getInventory().getItem(packet.slot());
		else
			return;
		if (!stack.is(ModItems.SCHEMATIC.get()))
			return;
		stack.set(ModComponents.SCHEMATIC_DEPLOYED.get(), packet.deployed());
		stack.set(ModComponents.SCHEMATIC_ANCHOR.get(), packet.anchor());
		stack.set(ModComponents.SCHEMATIC_ROTATION.get(), packet.rotation());
		stack.set(ModComponents.SCHEMATIC_MIRROR.get(), packet.mirror());
	}

	// ------------------------------------------------------------------ creative instant print

	public record SchematicPlace(ItemStack stack) implements CustomPacketPayload {
		public static final Type<SchematicPlace> TYPE = new Type<>(Schematicannon.id("place_schematic"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SchematicPlace> STREAM_CODEC =
			ItemStack.STREAM_CODEC.map(SchematicPlace::new, SchematicPlace::stack);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void handlePlace(SchematicPlace packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player) || !player.isCreative())
			return;
		// Only trust the file reference from a schematic the player really holds.
		ItemStack held = findHeldSchematic(player, packet.stack());
		if (held == null)
			return;

		var anchor = packet.stack().get(ModComponents.SCHEMATIC_ANCHOR.get());
		if (anchor == null || !anchor.closerThan(player.blockPosition(), SchematicannonBlockEntity.MAX_ANCHOR_DISTANCE))
			return; // only near the player: no printing (and chunk loading) far away

		SchematicPrinter printer = new SchematicPrinter();
		printer.setOwner(player.getUUID());
		printer.loadSchematic(packet.stack(), player.level());
		if (!printer.isLoaded() || printer.isErrored())
			return;
		// placed over the next ticks within a small time budget, never all in one tick
		dev.schematicannon.schematic.InstantPrints.start(player.getUUID(), player.level(), printer,
			ServerConfig.CREATIVE_PRINT_INCLUDES_AIR.get());
	}

	private static @Nullable ItemStack findHeldSchematic(ServerPlayer player, ItemStack sent) {
		String file = sent.get(ModComponents.SCHEMATIC_FILE.get());
		String owner = sent.get(ModComponents.SCHEMATIC_OWNER.get());
		if (file == null || owner == null)
			return null;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.SCHEMATIC.get()) && file.equals(stack.get(ModComponents.SCHEMATIC_FILE.get()))
				&& owner.equals(stack.get(ModComponents.SCHEMATIC_OWNER.get())))
				return stack;
		}
		return null;
	}

	// ------------------------------------------------------------------ schematic and quill instant upload

	public record InstantSchematic(String name, BlockPos origin, BlockPos bounds) implements CustomPacketPayload {
		public static final Type<InstantSchematic> TYPE = new Type<>(Schematicannon.id("instant_schematic"));
		public static final StreamCodec<ByteBuf, InstantSchematic> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(256), InstantSchematic::name,
			BlockPos.STREAM_CODEC, InstantSchematic::origin,
			BlockPos.STREAM_CODEC, InstantSchematic::bounds,
			InstantSchematic::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void handleInstant(InstantSchematic packet, IPayloadContext context) {
		if (context.player() instanceof ServerPlayer player)
			Schematicannon.SCHEMATIC_RECEIVER.handleInstantSchematic(player, packet.name(), player.level(), packet.origin(),
				packet.bounds());
	}

	// ------------------------------------------------------------------ cannon settings

	public record ConfigureSchematicannon(ConfigureSchematicannonOption option, boolean set) implements CustomPacketPayload {
		public static final Type<ConfigureSchematicannon> TYPE = new Type<>(Schematicannon.id("configure_schematicannon"));
		public static final StreamCodec<ByteBuf, ConfigureSchematicannon> STREAM_CODEC = StreamCodec.composite(
			ConfigureSchematicannonOption.STREAM_CODEC, ConfigureSchematicannon::option,
			ByteBufCodecs.BOOL, ConfigureSchematicannon::set,
			ConfigureSchematicannon::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void handleConfigure(ConfigureSchematicannon packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof SchematicannonMenu menu))
			return;
		SchematicannonBlockEntity be = menu.contentHolder;
		if (be == null)
			return;
		switch (packet.option()) {
			case DONT_REPLACE, REPLACE_ANY, REPLACE_EMPTY, REPLACE_SOLID -> be.replaceMode = packet.option().ordinal();
			case SKIP_MISSING -> be.skipMissing = packet.set();
			case SKIP_BLOCK_ENTITIES -> be.replaceBlockEntities = packet.set();
			case PLAY -> {
				// whoever starts the cannon is responsible for what it builds (land claims)
				be.owner = player.getUUID();
				be.state = SchematicannonBlockEntity.State.RUNNING;
				be.statusMsg = "running";
			}
			case PAUSE -> {
				be.state = SchematicannonBlockEntity.State.PAUSED;
				be.statusMsg = "paused";
			}
			case STOP -> {
				be.state = SchematicannonBlockEntity.State.STOPPED;
				be.statusMsg = "stopped";
			}
		}
		be.sendUpdate = true;
	}

	// ------------------------------------------------------------------ clipboard edits

	public record EditClipboard(boolean offhand, dev.schematicannon.clipboard.ClipboardContent content) implements CustomPacketPayload {
		public static final Type<EditClipboard> TYPE = new Type<>(Schematicannon.id("edit_clipboard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EditClipboard> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, EditClipboard::offhand,
			dev.schematicannon.clipboard.ClipboardContent.STREAM_CODEC, EditClipboard::content,
			EditClipboard::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static void handleEditClipboard(EditClipboard packet, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player))
			return;
		ItemStack stack = player.getItemInHand(packet.offhand() ? net.minecraft.world.InteractionHand.OFF_HAND
			: net.minecraft.world.InteractionHand.MAIN_HAND);
		if (stack.is(ModItems.CLIPBOARD.get()))
			dev.schematicannon.clipboard.ClipboardItem.applyEdit(stack, packet.content());
	}

	private ModPackets() {}
}
