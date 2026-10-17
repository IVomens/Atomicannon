package dev.schematicannon.client;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.compat.NtmCompat;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.schematic.SchematicExport;
import dev.schematicannon.schematic.SchematicItem;
import dev.schematicannon.schematic.SchematicPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Development-only visual check, enabled with {@code -Dschematicannon.previewTest=true} (the {@code previewTest}
 * Gradle run). In a singleplayer world it builds a source structure (an NTM machine when NTM is present),
 * saves it as a schematic, deploys it beside the original in the player's hand and takes a screenshot of the
 * hologram, then quits.
 */
final class DevPreviewTest {
	static final boolean ENABLED = Boolean.getBoolean("schematicannon.previewTest");
	private static int ticks;
	private static BlockPos lookAt;
	private static BlockPos cannonPos;
	private static BlockPos tablePos;

	private static void openMenu(BlockPos pos) {
		ServerPlayer player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
		if (player.level().getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider provider) {
			player.closeContainer();
			player.openMenu(provider, pos);
		}
	}

	static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getSingleplayerServer() == null)
			return;
		ticks++;
		// the test window is usually unfocused; keep the game running
		mc.options.pauseOnLostFocus = false;
		if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen)
			mc.gui.setScreen(null);
		if (ticks == 40)
			mc.getSingleplayerServer().execute(DevPreviewTest::setUp);
		if (ticks > 45 && lookAt != null) {
			// look at the gap between original and hologram
			var eye = mc.player.getEyePosition();
			double dx = lookAt.getX() + .5 - eye.x, dy = lookAt.getY() + .5 - eye.y, dz = lookAt.getZ() + .5 - eye.z;
			mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
			mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		}
		if (ticks == 120) {
			checkClientSave(mc);
			compareClientSave(mc);
			mc.getSingleplayerServer().execute(DevPreviewTest::checkSettingsPaste);
		}
		if (ticks == 135)
			finishClientSaveComparison(mc);
		if (ticks == 140)
			Screenshot.grab(mc, false);
		if (ticks == 150)
			mc.getSingleplayerServer().execute(() -> openMenu(cannonPos));
		if (ticks == 175)
			Screenshot.grab(mc, false);
		if (ticks == 180)
			mc.getSingleplayerServer().execute(() -> openMenu(tablePos));
		if (ticks == 205)
			Screenshot.grab(mc, false);
		if (ticks == 210) {
			mc.player.closeContainer();
			mc.getSingleplayerServer().execute(DevPreviewTest::fireCannon);
		}
		if (ticks == 222)
			mc.getSingleplayerServer().execute(() -> {
				// press Start once the cannon has loaded the schematic (it waits on Ready)
				ServerPlayer p = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
				if (p.level().getBlockEntity(cannonPos) instanceof dev.schematicannon.cannon.SchematicannonBlockEntity c) {
					c.state = dev.schematicannon.cannon.SchematicannonBlockEntity.State.RUNNING;
					c.statusMsg = "running";
				}
			});
		if (ticks == 245 || ticks == 300)
			Screenshot.grab(mc, false);
		if (ticks == 302)
			mc.getSingleplayerServer().execute(DevPreviewTest::giveClipboard);
		if (ticks == 310)
			ClientHooks.openClipboard(net.minecraft.world.InteractionHand.MAIN_HAND);
		if (ticks == 318)
			Screenshot.grab(mc, false);
		if (ticks == 322) {
			mc.gui.setScreen(null);
			mc.getSingleplayerServer().execute(DevPreviewTest::setUpAdvanced);
		}
		if (ticks == 350)
			Screenshot.grab(mc, false);
		if (ticks == 352 && advancedPos != null)
			mc.getSingleplayerServer().execute(() -> openMenu(advancedPos));
		if (ticks == 375)
			Screenshot.grab(mc, false);
		if (ticks == 380)
			mc.stop();
	}

	/** Loads the deployed schematic into the cannon, presses Start and moves the camera next to the cannon. */
	private static void fireCannon() {
		ServerPlayer player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
		if (!(player.level().getBlockEntity(cannonPos) instanceof dev.schematicannon.cannon.SchematicannonBlockEntity cannon))
			return;
		cannon.inventory.setItem(dev.schematicannon.cannon.SchematicannonBlockEntity.SLOT_SCHEMATIC, player.getMainHandItem().copy());

		player.getInventory().setItem(1, new ItemStack(dev.schematicannon.registry.ModItems.SCHEMATICANNON.get()));
		player.teleportTo(cannonPos.getX() - 2.6, cannonPos.getY() - .4, cannonPos.getZ() + .5);
		lookAt = cannonPos;
		Minecraft.getInstance().execute(() -> {
			var mc = Minecraft.getInstance();
			for (int i = 0; i < 20; i++)
				if (mc.player != null) mc.player.getInventory().setSelectedSlot(1);
		});
	}

	private static BlockPos hydroPos;

	/** Saves the identified hydrotreater from the CLIENT world (like Schematic and Quill "Save") and checks the fluid ID survived. */
	private static void checkClientSave(Minecraft mc) {
		if (hydroPos == null)
			return;
		var result = SchematicExport.saveSchematic(SchematicPaths.SCHEMATICS_DIR, "client_fluid_check", true, mc.level,
			hydroPos.offset(-2, 0, -2), hydroPos.offset(2, 3, 2));
		try {
			var tag = net.minecraft.nbt.NbtIo.readCompressed(result.file(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			Schematicannon.LOGGER.info("[previewTest] client-saved hydrotreater keeps natural gas id: {}",
				tag.toString().contains("hbm:gas"));
		} catch (Exception e) {
			Schematicannon.LOGGER.error("[previewTest] client save check failed", e);
		}
	}

	/** Pastes a copy of the identified hydrotreater through the real placement code and checks the fluid ID stuck. */
	private static void checkSettingsPaste() {
		if (hydroPos == null)
			return;
		ServerPlayer player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		var raw = level.getBlockEntity(hydroPos).saveWithFullMetadata(level.registryAccess());
		BlockState state = level.getBlockState(hydroPos);
		BlockPos copyPos = hydroPos.north(6);
		var data = new net.minecraft.nbt.CompoundTag();
		var settings = NtmCompat.extractSettings(level, copyPos, state, raw);
		if (settings != null)
			data.put(dev.schematicannon.schematic.SchematicPlacement.SETTINGS_KEY, settings);
		dev.schematicannon.schematic.SchematicPlacement.placeSchematicBlock(level, state, copyPos, null, data);
		var after = level.getBlockEntity(copyPos) instanceof com.hbm.interfaces.ICopiable c ? c.getSettings(level, copyPos) : null;
		Schematicannon.LOGGER.info("[previewTest] settings paste keeps natural gas id: {} ({})",
			after != null && after.toString().contains("hbm:gas"), after);
	}

	private static BlockPos rowBase;

	private static Block hbm(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.parse("hbm:" + id));
	}

	/** Configured NTM blocks, for comparing a client-side save (Schematic and Quill "Save") with the server's data. */
	private static void buildSettingsRow(ServerLevel level, BlockPos base) {
		rowBase = base.offset(-13, 0, 18);
		BlockPos r = rowBase;
		java.util.function.BiFunction<String, BlockPos, BlockPos> put = (id, p) -> {
			BlockState s = hbm(id).defaultBlockState();
			if (s.getBlock() instanceof com.hbm.blocks.multiblock.BlockMultiblockCore)
				NtmCompat.assemble(level, p, s.trySetValue(com.hbm.blocks.multiblock.BlockMultiblockCore.FACING, Direction.SOUTH));
			else
				level.setBlockAndUpdate(p, s);
			return p;
		};
		var sender = put.apply("radio_torch_sender", r.offset(0, 0, 5));
		((com.hbm.blockentity.network.BlockEntityRadioTorch) level.getBlockEntity(sender)).channel = "atomica";
		var ejector = put.apply("crane_extractor", r.offset(3, 0, 5));
		var ej = (com.hbm.blockentity.network.BlockEntityCraneExtractor) level.getBlockEntity(ejector);
		ej.isWhitelist = true;
		ej.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
		var ms = put.apply("mass_storage_iron", r.offset(6, 0, 5));
		((com.hbm.blockentity.machine.storage.BlockEntityMassStorage) level.getBlockEntity(ms))
			.setItem(com.hbm.blockentity.machine.storage.BlockEntityMassStorage.SLOT_TYPE, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
		var rbmk = put.apply("rbmk_blank", r.offset(12, 0, 5));
		var display = put.apply("rbmk_display", r.offset(9, 0, 5));
		((com.hbm.blockentity.machine.rbmk.BlockEntityRBMKDisplay) level.getBlockEntity(display)).setTarget(rbmk.getX(), rbmk.getY(), rbmk.getZ());
		var pa = put.apply("red_pylon", r.offset(16, 0, 5));
		var pb = put.apply("red_pylon", r.offset(20, 0, 5));
		com.hbm.api.energymk2.PowerGraph.get(level).addRemoteLink(pa.asLong(), pb.asLong());
		((com.hbm.blockentity.network.BlockEntityPylonBase) level.getBlockEntity(pa)).refreshConnections();
		((com.hbm.blockentity.network.BlockEntityPylonBase) level.getBlockEntity(pb)).refreshConnections();
		var cable = put.apply("red_cable_paintable", r.offset(24, 0, 5));
		((com.hbm.blockentity.network.BlockEntityPipePaintable) level.getBlockEntity(cable)).setCamo(Blocks.BRICKS.defaultBlockState());
		var mixer = put.apply("machine_mixer", r.offset(3, 0, 1));
		((com.hbm.blockentity.machine.BlockEntityMachineMixer) level.getBlockEntity(mixer))
			.tanks[com.hbm.blockentity.machine.BlockEntityMachineMixer.TANK_OUT].setTankType(BuiltInRegistries.FLUID.getValue(Identifier.parse("hbm:solvent")));
		var chem = put.apply("machine_chemical_plant", r.offset(10, 0, 1));
		var plant = (com.hbm.blockentity.machine.BlockEntityMachineChemicalPlant) level.getBlockEntity(chem);
		plant.module.setRecipe(com.hbm.inventory.recipes.ChemicalPlantRecipes.INSTANCE.recipes().get(3).getInternalName());
	}

	/** Saves the row from the client world and from the server world and logs every block whose data differs. */
	private static void compareClientSave(Minecraft mc) {
		if (rowBase == null)
			return;
		BlockPos min = rowBase.offset(-2, 0, -2), max = rowBase.offset(26, 4, 7);
		// exactly what Schematic and Quill "Save" does: save here, then ask the server for the machine settings
		clientRow = SchematicExport.saveSchematic(SchematicPaths.SCHEMATICS_DIR, "client_row", true, mc.level, min, max);
		net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
			new dev.schematicannon.network.ModPackets.ServerDataRequest(clientRow.fileName(), clientRow.origin(), clientRow.bounds()));
	}

	private static SchematicExport.SchematicExportResult clientRow;

	/** A little later (the server's settings have arrived): compare the patched local save with the server's own. */
	private static void finishClientSaveComparison(Minecraft mc) {
		if (clientRow == null)
			return;
		BlockPos min = rowBase.offset(-2, 0, -2), max = rowBase.offset(26, 4, 7);
		// the server world must be read on the server thread (other threads get no block entities)
		mc.getSingleplayerServer().execute(() -> {
			ServerLevel serverLevel = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().level();
			var server = SchematicExport.saveSchematic(SchematicPaths.SCHEMATICS_DIR, "server_row", true, serverLevel, min, max);
			compareFiles(clientRow, server);
		});
	}

	private static void compareFiles(SchematicExport.SchematicExportResult client, SchematicExport.SchematicExportResult server) {
		try {
			var c = net.minecraft.nbt.NbtIo.readCompressed(client.file(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			var s = net.minecraft.nbt.NbtIo.readCompressed(server.file(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			var cBlocks = c.getListOrEmpty("blocks");
			var sBlocks = s.getListOrEmpty("blocks");
			int differ = 0, same = 0;
			for (int i = 0; i < sBlocks.size(); i++) {
				var se = sBlocks.getCompoundOrEmpty(i);
				if (!se.contains("nbt"))
					continue;
				net.minecraft.nbt.CompoundTag ce = null;
				for (int j = 0; j < cBlocks.size(); j++)
					if (cBlocks.getCompoundOrEmpty(j).getListOrEmpty("pos").equals(se.getListOrEmpty("pos")))
						ce = cBlocks.getCompoundOrEmpty(j);
				String sn = se.getCompoundOrEmpty("nbt").toString();
				String cn = ce == null ? "<missing>" : ce.getCompoundOrEmpty("nbt").toString();
				String id = se.getCompoundOrEmpty("nbt").getStringOr("id", "?");
				if (sn.equals(cn)) {
					same++;
					continue;
				}
				differ++;
				Schematicannon.LOGGER.info("[clientsave] {} differs\n  server: {}\n  client: {}", id,
					sn.length() > 700 ? sn.substring(0, 700) + "..." : sn, cn.length() > 700 ? cn.substring(0, 700) + "..." : cn);
			}
			Schematicannon.LOGGER.info("[clientsave] {} block entities identical, {} differ", same, differ);
		} catch (Exception e) {
			Schematicannon.LOGGER.error("[clientsave] comparison failed", e);
		}
	}

	private static BlockPos advancedPos;

	/** An Advanced Atomicannon fed by a charged NTM battery through cables (cables on two sides). */
	private static void setUpAdvanced() {
		if (dev.schematicannon.registry.ModBlocks.ADVANCED_SCHEMATICANNON == null)
			return;
		ServerPlayer player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		advancedPos = cannonPos.offset(-3, 0, -3);
		Block cable = null;
		for (Block block : BuiltInRegistries.BLOCK)
			if (block instanceof com.hbm.blocks.network.CableBlock) {
				cable = block;
				break;
			}
		level.setBlockAndUpdate(advancedPos, dev.schematicannon.registry.ModBlocks.ADVANCED_SCHEMATICANNON.get().defaultBlockState());
		BlockPos batteryPos = advancedPos.west(3);
		level.setBlockAndUpdate(batteryPos, BuiltInRegistries.BLOCK.getValue(Identifier.parse("hbm:machine_battery")).defaultBlockState());
		for (int i = 1; i <= 2; i++)
			level.setBlockAndUpdate(advancedPos.west(i), cable.defaultBlockState());
		for (int i = 1; i <= 2; i++)
			level.setBlockAndUpdate(advancedPos.south(i), cable.defaultBlockState());
		if (level.getBlockEntity(batteryPos) instanceof com.hbm.blockentity.machine.storage.BlockEntityMachineBattery battery) {
			battery.setPower(battery.getMaxPower() / 3);
			battery.setModes(com.hbm.blockentity.machine.storage.BlockEntityMachineBattery.MODE_OUTPUT,
				com.hbm.blockentity.machine.storage.BlockEntityMachineBattery.MODE_OUTPUT);
			battery.refreshMode();
		}
		if (level.getBlockEntity(advancedPos) instanceof dev.schematicannon.cannon.SchematicannonBlockEntity c)
			c.inventory.setItem(dev.schematicannon.cannon.SchematicannonBlockEntity.SLOT_BOOK_IN,
				new ItemStack(net.minecraft.world.item.Items.BOOK, 2));
		player.teleportTo(advancedPos.getX() + 1.9, advancedPos.getY(), advancedPos.getZ() + 2.6);
		lookAt = advancedPos;
	}

	private static void giveClipboard() {
		ServerPlayer player = Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
		var entries = new java.util.ArrayList<dev.schematicannon.clipboard.ClipboardContent.Entry>();
		entries.add(new dev.schematicannon.clipboard.ClipboardContent.Entry("12x Stone Bricks", true,
			new ItemStack(net.minecraft.world.item.Items.STONE_BRICKS), 12));
		entries.add(new dev.schematicannon.clipboard.ClipboardContent.Entry("2x Chemical Plant", false,
			new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("hbm:machine_chemical_plant"))), 2));
		entries.add(new dev.schematicannon.clipboard.ClipboardContent.Entry("5x Regular Conveyor Wand", false,
			new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("hbm:conveyor_wand_regular"))), 5));
		entries.add(new dev.schematicannon.clipboard.ClipboardContent.Entry("3x Glass", false,
			new ItemStack(net.minecraft.world.item.Items.GLASS), 3));
		entries.add(dev.schematicannon.clipboard.ClipboardContent.Entry.text("ask Bob for the desh"));
		ItemStack clipboard = new ItemStack(dev.schematicannon.registry.ModItems.CLIPBOARD.get());
		clipboard.set(dev.schematicannon.registry.ModComponents.CLIPBOARD.get(),
			new dev.schematicannon.clipboard.ClipboardContent(java.util.List.of(entries), 0));
		for (int slot = 0; slot < 9; slot++)
			player.getInventory().setItem(slot, clipboard.copy());
	}

	private static void setUp() {
		Minecraft mc = Minecraft.getInstance();
		ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
		ServerLevel level = player.level();
		BlockPos base = player.blockPosition();

		for (int x = -14; x <= 14; x++)
			for (int z = -2; z <= 27; z++) {
				level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
				for (int y = 0; y < 8; y++)
					level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
			}

		// source build, left of the player
		BlockPos src = base.offset(-9, 0, 9);
		for (int x = 0; x < 7; x++)
			for (int z = 0; z < 5; z++)
				level.setBlockAndUpdate(src.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(src.offset(0, 1, 0), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		level.setBlockAndUpdate(src.offset(6, 1, 0), Blocks.OAK_LOG.defaultBlockState());
		level.setBlockAndUpdate(src.offset(6, 2, 0), Blocks.LANTERN.defaultBlockState());
		Block machine = BuiltInRegistries.BLOCK.getValue(Identifier.parse("hbm:machine_chemical_plant"));
		BlockPos core = src.offset(3, 1, 2);
		if (machine != Blocks.AIR) {
			BlockState coreState = machine.defaultBlockState();
			if (coreState.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
				coreState = coreState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
			else
				coreState = coreState.trySetValue(com.hbm.blocks.multiblock.BlockMultiblockCore.FACING, Direction.SOUTH);
			NtmCompat.assemble(level, core, coreState);
		}

		Block hydro = BuiltInRegistries.BLOCK.getValue(Identifier.parse("hbm:machine_hydrotreater"));
		if (hydro != Blocks.AIR) {
			hydroPos = base.offset(-8, 0, -1);
			BlockState hs = hydro.defaultBlockState().trySetValue(com.hbm.blocks.multiblock.BlockMultiblockCore.FACING, Direction.SOUTH);
			NtmCompat.assemble(level, hydroPos, hs);
			if (level.getBlockEntity(hydroPos) instanceof com.hbm.interfaces.ICopiable c) {
				var s = new net.minecraft.nbt.CompoundTag();
				s.putInt("fluidCount", 1);
				s.putString("fluidID0", "hbm:gas");
				c.pasteSettings(s, 0, level, player, hydroPos);
			}
		}
		buildSettingsRow(level, base);
		BlockPos min = src;
		BlockPos max = src.offset(6, 4, 4);
		var clientCopy = SchematicExport.saveSchematic(SchematicPaths.SCHEMATICS_DIR, "preview_test", true, level, min, max);
		var serverCopy = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve(player.getPlainTextName()),
			"preview_test", true, level, min, max);
		if (clientCopy == null || serverCopy == null) {
			Schematicannon.LOGGER.error("[previewTest] export failed");
			return;
		}

		ItemStack schematic = SchematicItem.create(level, serverCopy.fileName(), player.getPlainTextName());
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), base.offset(3, 0, 9));
		player.setItemInHand(InteractionHand.MAIN_HAND, schematic);
		cannonPos = base.offset(2, 0, 4);
		tablePos = base.offset(-2, 0, 4);
		level.setBlockAndUpdate(cannonPos, dev.schematicannon.registry.ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		level.setBlockAndUpdate(cannonPos.east(), dev.schematicannon.registry.ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
		level.setBlockAndUpdate(tablePos, dev.schematicannon.registry.ModBlocks.SCHEMATIC_TABLE.get().defaultBlockState());
		if (level.getBlockEntity(cannonPos) instanceof dev.schematicannon.cannon.SchematicannonBlockEntity cannon) {
			cannon.inventory.setItem(dev.schematicannon.cannon.SchematicannonBlockEntity.SLOT_GUNPOWDER,
				new ItemStack(net.minecraft.world.item.Items.GUNPOWDER, 12));
			cannon.inventory.setItem(dev.schematicannon.cannon.SchematicannonBlockEntity.SLOT_BOOK_IN,
				new ItemStack(net.minecraft.world.item.Items.BOOK, 3));
		}
		player.teleportTo(base.getX() + .5, base.getY() + 3, base.getZ() - 1.5);
		lookAt = base.offset(0, 0, 8);
		Schematicannon.LOGGER.info("[previewTest] schematic {} deployed, NTM machine: {}", serverCopy.fileName(),
			machine != Blocks.AIR);
	}

	private DevPreviewTest() {}
}
