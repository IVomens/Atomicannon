package dev.schematicannon.gametest;

import java.util.function.Consumer;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.schematic.SchematicExport;
import dev.schematicannon.schematic.SchematicItem;
import dev.schematicannon.schematic.SchematicPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Development-only game tests (registered only outside production). Run with {@code gradlew runGameTestServer}.
 */
public final class SchematicannonGameTests {
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
		DeferredRegister.create(Registries.TEST_FUNCTION, Schematicannon.MOD_ID);

	private static final boolean NTM = net.neoforged.fml.ModList.get().isLoaded("hbm");
	private static final boolean WARFORGE = net.neoforged.fml.ModList.get().isLoaded("warforge");
	/** Server load test, only with -Pstress (it takes a while). */
	private static final boolean STRESS = Boolean.getBoolean("schematicannon.stress");

	static {
		FUNCTIONS.register("cannon_prints_schematic", () -> SchematicannonGameTests::cannonPrintsSchematic);
		FUNCTIONS.register("table_uses_one_empty_schematic", () -> SchematicannonGameTests::tableUsesOneEmptySchematic);
		FUNCTIONS.register("clipboard_checklist_updates", () -> SchematicannonGameTests::clipboardChecklistUpdates);
		if (NTM)
			FUNCTIONS.register("cannon_prints_ntm_multiblock", () -> NtmGameTests::cannonPrintsNtmMultiblock);
		if (NTM)
			FUNCTIONS.register("cannon_prints_ntm_conveyors", () -> NtmGameTests::cannonPrintsNtmConveyors);
		if (NTM)
			FUNCTIONS.register("cannon_keeps_fluid_identifier", () -> NtmGameTests::cannonKeepsFluidIdentifier);
		if (NTM)
			FUNCTIONS.register("cannon_keeps_duct_fluid", () -> NtmGameTests::cannonKeepsDuctFluid);
		if (NTM)
			FUNCTIONS.register("advanced_cannon_runs_on_power", () -> NtmGameTests::advancedCannonRunsOnPower);
		if (NTM)
			FUNCTIONS.register("cannon_builds_watz_from_parts", () -> NtmGameTests::cannonBuildsWatzFromParts);
		if (STRESS)
			FUNCTIONS.register("server_load", () -> StressTests::serverLoad);
		if (NTM)
			FUNCTIONS.register("cannon_keeps_paint", () -> NtmGameTests::cannonKeepsPaint);
		if (NTM)
			FUNCTIONS.register("cannon_keeps_ntm_settings", () -> NtmGameTests::cannonKeepsNtmSettings);
		if (NTM)
			FUNCTIONS.register("cannon_pastes_socket_battery", () -> NtmGameTests::cannonPastesSocketBattery);
		if (NTM) {
			for (String id : NtmGameTests.PART_BUILT)
				FUNCTIONS.register("rebuilds_" + id, () -> helper -> NtmGameTests.cannonRebuildsPartBuilt(helper, id));
			for (String id : NtmGameTests.LEVER_TURBINES)
				FUNCTIONS.register("turbine_steam_" + id, () -> helper -> NtmGameTests.cannonKeepsTurbineSteam(helper, id));
		}
		if (WARFORGE)
			FUNCTIONS.register("claims_block_outsiders", () -> WarForgeGameTests::claimsBlockOutsiders);
	}

	public static void register(IEventBus modBus) {
		FUNCTIONS.register(modBus);
		modBus.addListener(SchematicannonGameTests::registerTests);
	}

	private static void registerTests(RegisterGameTestsEvent event) {
		var environment = event.registerEnvironment(Schematicannon.id("default"));
		if (STRESS) {
			var stressEnvironment = event.registerEnvironment(Schematicannon.id("stress"));
			event.registerTest(Schematicannon.id("server_load"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("server_load")),
				new TestData<>(stressEnvironment, Identifier.withDefaultNamespace("empty"), 20000, 1, true)));
		}
		event.registerTest(Schematicannon.id("cannon_prints_schematic"), new FunctionGameTestInstance(
			ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("cannon_prints_schematic")),
			new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 900, 1, true)));
		var clipboardEnvironment = event.registerEnvironment(Schematicannon.id("clipboard"));
		event.registerTest(Schematicannon.id("clipboard_checklist_updates"), new FunctionGameTestInstance(
			ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("clipboard_checklist_updates")),
			new TestData<>(clipboardEnvironment, Identifier.withDefaultNamespace("empty"), 400, 1, true)));
		var tableEnvironment = event.registerEnvironment(Schematicannon.id("table"));
		event.registerTest(Schematicannon.id("table_uses_one_empty_schematic"), new FunctionGameTestInstance(
			ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("table_uses_one_empty_schematic")),
			new TestData<>(tableEnvironment, Identifier.withDefaultNamespace("empty"), 100, 1, true)));
		if (NTM) {
			// separate environment = separate batch, so the two builds never overlap
			var ntmEnvironment = event.registerEnvironment(Schematicannon.id("ntm"));
			event.registerTest(Schematicannon.id("cannon_prints_ntm_multiblock"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("cannon_prints_ntm_multiblock")),
				new TestData<>(ntmEnvironment, Identifier.withDefaultNamespace("empty"), 2400, 1, true)));
		}
		if (WARFORGE) {
			var claimEnvironment = event.registerEnvironment(Schematicannon.id("warforge"));
			event.registerTest(Schematicannon.id("claims_block_outsiders"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("claims_block_outsiders")),
				new TestData<>(claimEnvironment, Identifier.withDefaultNamespace("empty"), 1200, 1, true)));
		}
		if (NTM) {
			var fluidEnvironment = event.registerEnvironment(Schematicannon.id("ntm_fluid_id"));
			event.registerTest(Schematicannon.id("cannon_keeps_fluid_identifier"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("cannon_keeps_fluid_identifier")),
				new TestData<>(fluidEnvironment, Identifier.withDefaultNamespace("empty"), 2400, 1, true)));
			var conveyorEnvironment = event.registerEnvironment(Schematicannon.id("ntm_conveyors"));
			event.registerTest(Schematicannon.id("cannon_prints_ntm_conveyors"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("cannon_prints_ntm_conveyors")),
				new TestData<>(conveyorEnvironment, Identifier.withDefaultNamespace("empty"), 2400, 1, true)));
			var ductEnvironment = event.registerEnvironment(Schematicannon.id("ntm_ducts"));
			event.registerTest(Schematicannon.id("cannon_keeps_duct_fluid"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("cannon_keeps_duct_fluid")),
				new TestData<>(ductEnvironment, Identifier.withDefaultNamespace("empty"), 1200, 1, true)));
			var powerEnvironment = event.registerEnvironment(Schematicannon.id("ntm_power"));
			event.registerTest(Schematicannon.id("advanced_cannon_runs_on_power"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("advanced_cannon_runs_on_power")),
				new TestData<>(powerEnvironment, Identifier.withDefaultNamespace("empty"), 1200, 1, true)));
			var watzEnvironment = event.registerEnvironment(Schematicannon.id("ntm_watz"));
			event.registerTest(Schematicannon.id("cannon_builds_watz_from_parts"), new FunctionGameTestInstance(
				ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id("cannon_builds_watz_from_parts")),
				new TestData<>(watzEnvironment, Identifier.withDefaultNamespace("empty"), 3000, 1, true)));
			java.util.List<String> names = new java.util.ArrayList<>(java.util.List.of("cannon_keeps_paint", "cannon_keeps_ntm_settings",
				"cannon_pastes_socket_battery"));
			for (String id : NtmGameTests.PART_BUILT)
				names.add("rebuilds_" + id);
			for (String id : NtmGameTests.LEVER_TURBINES)
				names.add("turbine_steam_" + id);
			for (String name : names) {
				// one environment each: separate batches, so the big machines never overlap
				var env = event.registerEnvironment(Schematicannon.id("env_" + name));
				event.registerTest(Schematicannon.id(name), new FunctionGameTestInstance(
					ResourceKey.create(Registries.TEST_FUNCTION, Schematicannon.id(name)),
					new TestData<>(env, Identifier.withDefaultNamespace("empty"), 4000, 1, true)));
			}
		}
	}

	/**
	 * Builds a small structure, saves it as a schematic exactly like a Schematic and Quill upload, then lets a
	 * Schematicannon backed by a Creative Crate rebuild it elsewhere. Checks that every block arrives, that the
	 * deferred torch is placed, that double slabs survive, and that the chest arrives EMPTY.
	 */
	private static void cannonPrintsSchematic(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		// source build at relative (0..2, 1..2, 0..2)
		BlockPos src = new BlockPos(0, 1, 0);
		for (int x = 0; x < 3; x++)
			for (int z = 0; z < 3; z++)
				helper.setBlock(src.offset(x, 0, z), Blocks.STONE_BRICKS);
		helper.setBlock(src.offset(0, 1, 0), Blocks.OAK_PLANKS);
		helper.setBlock(src.offset(2, 1, 2), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
		helper.setBlock(src.offset(1, 1, 1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		helper.setBlock(src.offset(0, 1, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
		ChestBlockEntity chest = helper.getBlockEntity(src.offset(1, 1, 1), ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 64));

		BlockPos absSrc = helper.absolutePos(src);
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_build", true, level, absSrc, absSrc.offset(2, 1, 2));
		helper.assertTrue(result != null, "schematic export failed");

		// target anchor 5 blocks east
		BlockPos dst = new BlockPos(6, 1, 0);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), helper.absolutePos(dst));

		BlockPos cannonPos = new BlockPos(4, 1, 4);
		helper.setBlock(cannonPos, ModBlocks.SCHEMATICANNON.get());
		helper.setBlock(cannonPos.west(), ModBlocks.CREATIVE_CRATE.get());
		SchematicannonBlockEntity cannon = helper.getBlockEntity(cannonPos, SchematicannonBlockEntity.class);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		// Like a player: the cannon must sit on "Ready" (even with a creative crate) until Start is pressed.
		helper.runAfterDelay(30, () -> {
			helper.assertValueEqual(cannon.statusMsg, "ready", "cannon status before Start");
			helper.assertValueEqual(cannon.blocksPlaced, 0, "blocks placed before Start");
			pressStart(helper, cannon);
		});

		helper.succeedWhen(() -> {
			helper.assertBlockState(dst.offset(1, 0, 1), Blocks.STONE_BRICKS.defaultBlockState());
			helper.assertBlockState(dst.offset(0, 1, 0), Blocks.OAK_PLANKS.defaultBlockState());
			helper.assertBlockState(dst.offset(2, 1, 2),
				Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
			helper.assertTrue(helper.getBlockState(dst.offset(0, 1, 1)).is(Blocks.WALL_TORCH), "torch not placed");
			helper.assertTrue(helper.getBlockState(dst.offset(1, 1, 1)).is(Blocks.CHEST), "chest not placed");
			ChestBlockEntity copy = helper.getBlockEntity(dst.offset(1, 1, 1), ChestBlockEntity.class);
			helper.assertTrue(copy.isEmpty(), "chest contents were copied - schematic NBT must be stripped");
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
		});
	}

	/**
	 * A stack of Empty Schematics in a Schematic Table: one upload (through the real upload receiver) must use exactly
	 * one of them, an upload must be refused while the output slot is full, and a cancelled upload refunds its item.
	 */
	private static void tableUsesOneEmptySchematic(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// not added to the level, so other mods' join hooks (e.g. WarForge sync packets) don't fire
		var player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);

		BlockPos tablePos = new BlockPos(1, 1, 1);
		helper.setBlock(tablePos, dev.schematicannon.registry.ModBlocks.SCHEMATIC_TABLE.get());
		var table = helper.getBlockEntity(tablePos, dev.schematicannon.table.SchematicTableBlockEntity.class);
		table.inventory.setItem(0, new ItemStack(dev.schematicannon.registry.ModItems.EMPTY_SCHEMATIC.get(), 5));

		// a real schematic file to upload
		BlockPos src = helper.absolutePos(new BlockPos(1, 3, 1));
		level.setBlockAndUpdate(src, Blocks.STONE_BRICKS.defaultBlockState());
		var export = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest_src"),
			"gametest_table", true, level, src, src);
		byte[] bytes;
		try {
			bytes = java.nio.file.Files.readAllBytes(export.file());
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}

		var receiver = Schematicannon.SCHEMATIC_RECEIVER;
		String name = "gametest_table.nbt";
		BlockPos absTable = helper.absolutePos(tablePos);
		receiver.handleNewUpload(player, name, bytes.length, absTable);
		for (int i = 0; i < bytes.length; i += 1024)
			receiver.handleWriteRequest(player, name, java.util.Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + 1024)));
		receiver.handleFinishedUpload(player, name);

		helper.assertValueEqual(table.inventory.getItem(0).getCount(), 4, "empty schematics left after one upload");
		helper.assertTrue(table.inventory.getItem(1).is(dev.schematicannon.registry.ModItems.SCHEMATIC.get()),
			"upload did not produce a schematic");

		// output slot occupied: a second upload must be refused and use nothing
		receiver.handleNewUpload(player, "gametest_table2.nbt", bytes.length, absTable);
		helper.assertTrue(!table.isUploading, "upload started while the output slot was full");
		helper.assertValueEqual(table.inventory.getItem(0).getCount(), 4, "empty schematics after refused upload");

		// cancelled upload gives its empty schematic back
		table.inventory.setItem(1, ItemStack.EMPTY);
		table.startUpload("gametest_table3.nbt");
		helper.assertValueEqual(table.inventory.getItem(0).getCount(), 3, "empty schematics while uploading");
		table.cancelUpload();
		helper.assertValueEqual(table.inventory.getItem(0).getCount(), 4, "empty schematics after cancelled upload");
		helper.succeed();
	}

	/**
	 * Clipboard checklist: lists only what the attached inventories don't already provide; after the player ticks a
	 * line and writes a note, running it back through drops what has since been supplied and keeps tick + note.
	 */
	private static void clipboardChecklistUpdates(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos src = helper.absolutePos(new BlockPos(1, 40, 1));
		for (int i = 0; i < 4; i++)
			level.setBlockAndUpdate(src.east(i), Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(src.above(), Blocks.OAK_PLANKS.defaultBlockState());
		level.setBlockAndUpdate(src.above().east(), Blocks.OAK_PLANKS.defaultBlockState());
		level.setBlockAndUpdate(src.above().east(2), Blocks.GLASS.defaultBlockState());
		var export = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_clipboard", true, level, src, src.offset(3, 1, 0));

		ItemStack schematic = SchematicItem.create(level, export.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), src.south(5));

		BlockPos cannonPos = helper.absolutePos(new BlockPos(6, 40, 2));
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		level.setBlockAndUpdate(cannonPos.west(), Blocks.CHEST.defaultBlockState());
		var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(cannonPos.west());
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 2));

		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_BOOK_IN, new ItemStack(dev.schematicannon.registry.ModItems.CLIPBOARD.get()));

		boolean[] secondRound = { false };
		helper.onEachTick(() -> {
			ItemStack out = cannon.inventory.getItem(SchematicannonBlockEntity.SLOT_BOOK_OUT);
			if (out.isEmpty() || secondRound[0])
				return;
			// first list: 2 stone bricks (2 of 4 are in the chest), 2 planks, 1 glass
			var first = dev.schematicannon.clipboard.ClipboardItem.content(out);
			helper.assertValueEqual(amount(first, net.minecraft.world.item.Items.STONE_BRICKS), 2, "stone bricks still needed");
			helper.assertValueEqual(amount(first, net.minecraft.world.item.Items.OAK_PLANKS), 2, "planks needed");
			helper.assertValueEqual(amount(first, net.minecraft.world.item.Items.GLASS), 1, "glass needed");

			// the player ticks the planks line and adds a note (through the same path as the edit packet)
			var pages = first.mutablePages();
			for (int i = 0; i < pages.get(0).size(); i++)
				if (pages.get(0).get(i).icon().is(net.minecraft.world.item.Items.OAK_PLANKS))
					pages.get(0).set(i, pages.get(0).get(i).withChecked(true));
			pages.get(0).add(dev.schematicannon.clipboard.ClipboardContent.Entry.text("ask Bob for glass"));
			ItemStack edited = out.copy();
			dev.schematicannon.clipboard.ClipboardItem.applyEdit(edited, new dev.schematicannon.clipboard.ClipboardContent(pages, 0));

			// more bricks arrive, then the clipboard goes back through the cannon
			chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 2));
			cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_BOOK_OUT, ItemStack.EMPTY);
			cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_BOOK_IN, edited);
			secondRound[0] = true;
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(secondRound[0], "first clipboard not written yet");
			ItemStack out = cannon.inventory.getItem(SchematicannonBlockEntity.SLOT_BOOK_OUT);
			helper.assertTrue(!out.isEmpty(), "updated clipboard not written yet");
			var updated = dev.schematicannon.clipboard.ClipboardItem.content(out);
			helper.assertValueEqual(amount(updated, net.minecraft.world.item.Items.STONE_BRICKS), 0, "stone bricks after all 4 supplied");
			helper.assertTrue(checked(updated, net.minecraft.world.item.Items.OAK_PLANKS), "player's tick on planks was lost");
			helper.assertTrue(!checked(updated, net.minecraft.world.item.Items.GLASS), "glass should still be unticked");
			helper.assertTrue(updated.toString().contains("ask Bob for glass"), "player's note was lost");
			Schematicannon.LOGGER.info("[gametest] clipboard updated: {}", updated.pages());
		});
	}

	private static int amount(dev.schematicannon.clipboard.ClipboardContent c, net.minecraft.world.item.Item item) {
		for (var page : c.pages())
			for (var e : page)
				if (e.icon().is(item))
					return e.amount();
		return 0;
	}

	private static boolean checked(dev.schematicannon.clipboard.ClipboardContent c, net.minecraft.world.item.Item item) {
		for (var page : c.pages())
			for (var e : page)
				if (e.icon().is(item))
					return e.checked();
		return false;
	}

	/** Presses Start a moment later, after the cannon has loaded its schematic (it then waits on "Ready"). */
	/** Like a player: waits until the cannon shows "Ready" (schematic loaded), then presses Start. */
	static void pressStart(GameTestHelper helper, SchematicannonBlockEntity cannon) {
		pressStart(helper, cannon, 0);
	}

	// Gives up waiting after a while (and presses anyway): scheduled actions keep a game test from timing out.
	private static void pressStart(GameTestHelper helper, SchematicannonBlockEntity cannon, int tries) {
		helper.runAfterDelay(5, () -> {
			if (!cannon.statusMsg.equals("ready") && tries < 120) {
				pressStart(helper, cannon, tries + 1);
				return;
			}
			cannon.state = SchematicannonBlockEntity.State.RUNNING;
			cannon.statusMsg = "running";
		});
	}

	private SchematicannonGameTests() {}
}
