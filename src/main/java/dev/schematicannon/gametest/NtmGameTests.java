package dev.schematicannon.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.multiblock.BlockMultiblockCore;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.compat.NtmCompat;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.schematic.ItemRequirement;
import dev.schematicannon.schematic.SchematicExport;
import dev.schematicannon.schematic.SchematicItem;
import dev.schematicannon.schematic.SchematicPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Only loaded when NTM is present. */
final class NtmGameTests {
	/** Copy offset; small so everything stays in the test's ticking chunks. */
	private static final int SHIFT = 10;

	/**
	 * Builds a real NTM multiblock machine, captures it in a schematic, and has the cannon rebuild it. Passes when
	 * the copy has its core plus exactly as many cells as the original, and the cannon reports "finished".
	 */
	static void cannonPrintsNtmMultiblock(GameTestHelper helper) {
		var level = helper.getLevel();

		// Prefer a big production machine; fall back to the first fixed-footprint core NTM registers.
		String override = System.getProperty("schematicannon.gametest.ntmMachine", "");
		List<String> preferred = List.of(override, "hbm:machine_chemical_plant", "hbm:machine_assembly_machine",
			"hbm:machine_assembler", "hbm:machine_refinery", "hbm:machine_turbofan");
		List<BlockMultiblockCore> candidates = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK)
			if (block instanceof BlockMultiblockCore c && !c.hasVariableFootprint() && c.asItem() != Items.AIR)
				candidates.add(c);
		Schematicannon.LOGGER.info("[gametest] {} NTM multiblock cores: {}", candidates.size(),
			candidates.stream().map(c -> BuiltInRegistries.BLOCK.getKey(c).toString()).toList());
		BlockMultiblockCore core = candidates.isEmpty() ? null : candidates.get(0);
		for (String id : preferred)
			for (BlockMultiblockCore c : candidates)
				if (BuiltInRegistries.BLOCK.getKey(c).toString().equals(id) && core == candidates.get(0))
					core = c;
		helper.assertTrue(core != null, "no NTM multiblock core found");
		Schematicannon.LOGGER.info("[gametest] using NTM machine {}", BuiltInRegistries.BLOCK.getKey(core));

		BlockState coreState = core.defaultBlockState().setValue(BlockMultiblockCore.FACING, Direction.NORTH);
		BlockPos srcCore = helper.absolutePos(new BlockPos(4, 24, 4));
		NtmCompat.assemble(level, srcCore, coreState);
		helper.assertTrue(level.getBlockState(srcCore).is(core), "source machine did not assemble");

		// capture everything the machine occupies
		BoundingBox box = BoundingBox.fromCorners(srcCore, srcCore);
		List<BlockPos> srcCells = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(srcCore.offset(-4, -4, -4), srcCore.offset(4, 4, 4))) {
			BlockState s = level.getBlockState(p);
			if (NtmCompat.isMultiblockCell(s) || p.equals(srcCore)) {
				box = box.encapsulate(p);
				if (!p.equals(srcCore))
					srcCells.add(p.immutable());
			}
		}
		helper.assertTrue(!srcCells.isEmpty(), "machine has no cells");
		Schematicannon.LOGGER.info("[gametest] source machine has 1 core + {} cells, schematic box {}", srcCells.size(), box);
		helper.assertTrue(ItemRequirement.of(level.getBlockState(srcCells.get(0)), null).isEmpty(),
			"cells must not cost items");
		helper.assertTrue(!ItemRequirement.of(coreState, null).isInvalid(), "core must cost its machine item");

		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		BlockPos max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_ntm", true, level, min, max);
		helper.assertTrue(result != null, "schematic export failed");

		// print it 20 blocks east
		BlockPos dstAnchor = min.east(SHIFT);
		BlockPos dstCore = srcCore.east(SHIFT);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), dstAnchor);

		BlockPos cannonPos = helper.absolutePos(new BlockPos(9, 24, 12));
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		level.setBlockAndUpdate(cannonPos.west(), ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		SchematicannonGameTests.pressStart(helper, cannon);

		BlockMultiblockCore expectedCore = core;
		int expectedCells = srcCells.size();
		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
			helper.assertTrue(level.getBlockState(dstCore).is(expectedCore), "core missing at destination");
			int cells = 0;
			for (BlockPos p : srcCells)
				if (NtmCompat.isMultiblockCell(level.getBlockState(p.east(SHIFT))))
					cells++;
			helper.assertValueEqual(cells, expectedCells, "cells rebuilt by NTM");
		});
	}

	/**
	 * A line of NTM conveyors printed by a cannon that pulls conveyor wands from an adjacent desh crate (or a chest
	 * if this NTM has no desh crate). Passes when every conveyor is placed and one wand was used per conveyor.
	 */
	static void cannonPrintsNtmConveyors(GameTestHelper helper) {
		var level = helper.getLevel();
		Block conveyor = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:conveyor"));
		var wand = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("hbm:conveyor_wand_regular"));
		helper.assertTrue(conveyor != net.minecraft.world.level.block.Blocks.AIR, "no hbm:conveyor block");
		helper.assertTrue(wand != Items.AIR, "no hbm:conveyor_wand_regular item");

		BlockPos src = helper.absolutePos(new BlockPos(2, 50, 2)); // above the other tests, still in ticking chunks
		BlockState belt = conveyor.defaultBlockState();
		if (belt.hasProperty(com.hbm.blocks.network.ConveyorBlockBase.FACING))
			belt = belt.setValue(com.hbm.blocks.network.ConveyorBlockBase.FACING, Direction.EAST);
		int length = 5;
		for (int i = 0; i < length; i++) {
			level.setBlockAndUpdate(src.offset(i, -1, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(src.offset(i, 0, 0), belt);
		}
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_conveyors", true, level, src.offset(0, -1, 0), src.offset(length - 1, 0, 0));
		helper.assertTrue(result != null, "schematic export failed");

		BlockPos dst = src.offset(0, -1, 6);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), dst);

		BlockPos cannonPos = src.offset(2, 0, 12);
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		Block crateBlock = BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.parse("hbm:crate_desh"))
			.orElse(net.minecraft.world.level.block.Blocks.CHEST);
		BlockPos cratePos = cannonPos.west();
		level.setBlockAndUpdate(cratePos, crateBlock.defaultBlockState());
		Schematicannon.LOGGER.info("[gametest] conveyor source crate: {}", BuiltInRegistries.BLOCK.getKey(crateBlock));
		var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, cratePos, Direction.EAST);
		helper.assertTrue(handler != null, "crate exposes no item handler");
		var stone = net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STONE);
		var wands = net.neoforged.neoforge.transfer.item.ItemResource.of(wand);
		try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
			handler.insert(wands, 16, tx);
			handler.insert(stone, 16, tx);
			tx.commit();
		}

		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_GUNPOWDER, new ItemStack(Items.GUNPOWDER, 4));
		cannon.state = SchematicannonBlockEntity.State.RUNNING;
		cannon.statusMsg = "running";
		// the cannon loads into "Ready" and waits for Start, like Create - press it
		helper.runAfterDelay(10, () -> {
			cannon.state = SchematicannonBlockEntity.State.RUNNING;
			cannon.statusMsg = "running";
		});

		Block expected = conveyor;
		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status (missing: " + cannon.missingItem + ")");
			for (int i = 0; i < length; i++)
				helper.assertTrue(level.getBlockState(dst.offset(i, 1, 0)).is(expected), "conveyor " + i + " missing");
			int left = 0;
			for (int s = 0; s < handler.size(); s++)
				if (handler.getResource(s).equals(wands))
					left += handler.getAmountAsInt(s);
			helper.assertValueEqual(left, 16 - length, "wands left in crate");
		});
	}

	/**
	 * A fluid-identified hydrotreater keeps its identified fluid when printed from a schematic.
	 */
	static void cannonKeepsFluidIdentifier(GameTestHelper helper) {
		var level = helper.getLevel();
		Block hydrotreater = null;
		for (Block block : BuiltInRegistries.BLOCK)
			if (block instanceof com.hbm.blocks.machine.MachineHydrotreater)
				hydrotreater = block;
		helper.assertTrue(hydrotreater != null, "no NTM hydrotreater");

		// an NTM fluid other than the hydrotreater's default
		// natural gas, as identified in game; not one of the hydrotreater's default tank types
		String fluidId = "hbm:gas";
		helper.assertTrue(BuiltInRegistries.FLUID.containsKey(net.minecraft.resources.Identifier.parse(fluidId)), "no hbm:gas fluid");

		BlockPos srcCore = helper.absolutePos(new BlockPos(5, 90, 5));
		BlockState coreState = hydrotreater.defaultBlockState().setValue(BlockMultiblockCore.FACING, Direction.NORTH);
		NtmCompat.assemble(level, srcCore, coreState);
		var srcBe = (com.hbm.interfaces.ICopiable) level.getBlockEntity(srcCore);
		var settings = new net.minecraft.nbt.CompoundTag();
		settings.putInt("fluidCount", 1);
		settings.putString("fluidID0", fluidId);
		srcBe.pasteSettings(settings, 0, level, net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level), srcCore);
		var before = srcBe.getSettings(level, srcCore);
		helper.assertTrue(before != null && before.toString().contains(fluidId), "could not identify the source machine: " + before);

		BoundingBox box = BoundingBox.fromCorners(srcCore, srcCore);
		for (BlockPos p : BlockPos.betweenClosed(srcCore.offset(-4, -4, -4), srcCore.offset(4, 4, 4)))
			if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
				box = box.encapsulate(p);
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_fluid_id", true, level, min, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
		helper.assertTrue(result != null, "schematic export failed");

		BlockPos dstCore = srcCore.east(SHIFT);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), min.east(SHIFT));
		BlockPos cannonPos = helper.absolutePos(new BlockPos(10, 90, 14));
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		level.setBlockAndUpdate(cannonPos.west(), ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		SchematicannonGameTests.pressStart(helper, cannon);

		Block expected = hydrotreater;
		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
			helper.assertTrue(level.getBlockState(dstCore).is(expected), "hydrotreater missing at destination");
			var after = ((com.hbm.interfaces.ICopiable) level.getBlockEntity(dstCore)).getSettings(level, dstCore);
			helper.assertTrue(after != null && after.toString().contains(fluidId),
				"printed hydrotreater lost its fluid identifier (" + fluidId + "): " + after);
			Schematicannon.LOGGER.info("[gametest] fluid id kept: {} -> {}", before, after);
		});
	}

	/**
	 * Lays a run of NTM fluid ducts set to natural gas, saves it, and has the cannon rebuild it. Duct fluid types live
	 * in NTM's pipe network rather than a block entity; the copies must come out as natural gas too.
	 */
	static void cannonKeepsDuctFluid(GameTestHelper helper) {
		var level = helper.getLevel();
		Block duct = null;
		for (Block block : BuiltInRegistries.BLOCK)
			if (block instanceof com.hbm.blocks.network.FluidPipeBlock) {
				duct = block;
				break;
			}
		helper.assertTrue(duct != null, "no NTM fluid duct");
		var gas = BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse("hbm:gas"));

		BlockPos src = helper.absolutePos(new BlockPos(2, 1, 2));
		for (int i = 0; i < 4; i++)
			level.setBlockAndUpdate(src.east(i), duct.defaultBlockState());
		com.hbm.blocks.network.FluidPipeBlock.retypeRun(level, src.asLong(), gas);
		for (int i = 0; i < 4; i++)
			helper.assertValueEqual(com.hbm.blocks.network.FluidPipeBlock.pipeFluidAt(level, src.east(i)), gas,
				"source duct fluid");

		BlockPos end = src.east(3);
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_ducts", true, level, src, end);
		helper.assertTrue(result != null, "schematic export failed");

		BlockPos dst = src.south(4);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), dst);
		BlockPos cannonPos = helper.absolutePos(new BlockPos(4, 1, 10));
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		level.setBlockAndUpdate(cannonPos.west(), ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		SchematicannonGameTests.pressStart(helper, cannon);

		Block expected = duct;
		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(level.getBlockState(dst.east(i)).is(expected), "duct missing at destination");
				helper.assertValueEqual(com.hbm.blocks.network.FluidPipeBlock.pipeFluidAt(level, dst.east(i)), gas,
					"printed duct fluid");
			}
		});
	}

	/**
	 * An Advanced Atomicannon fed by an NTM battery through NTM cables: the cable must connect to the cannon, the cannon
	 * must charge, refuse gunpowder, print a schematic out of a chest using power, and spend HE doing it.
	 */
	static void advancedCannonRunsOnPower(GameTestHelper helper) {
		var level = helper.getLevel();
		helper.assertTrue(ModBlocks.ADVANCED_SCHEMATICANNON != null, "advanced cannon not registered");
		Block cable = null;
		for (Block block : BuiltInRegistries.BLOCK)
			if (block instanceof com.hbm.blocks.network.CableBlock) {
				cable = block;
				break;
			}
		helper.assertTrue(cable != null, "no NTM cable");
		Block battery = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:machine_battery"));

		// battery -> cable -> cable -> cannon, chest of stone beside the cannon
		BlockPos batteryPos = new BlockPos(1, 1, 2);
		BlockPos cannonPos = new BlockPos(4, 1, 2);
		helper.setBlock(batteryPos, battery);
		helper.setBlock(new BlockPos(2, 1, 2), cable);
		helper.setBlock(new BlockPos(3, 1, 2), cable);
		helper.setBlock(cannonPos, ModBlocks.ADVANCED_SCHEMATICANNON.get());
		helper.setBlock(cannonPos.south(), net.minecraft.world.level.block.Blocks.CHEST);
		var chest = helper.getBlockEntity(cannonPos.south(), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.STONE, 16));

		var batteryBe = helper.getBlockEntity(batteryPos, com.hbm.blockentity.machine.storage.BlockEntityMachineBattery.class);
		batteryBe.setPower(batteryBe.getMaxPower());
		batteryBe.setModes(com.hbm.blockentity.machine.storage.BlockEntityMachineBattery.MODE_OUTPUT,
			com.hbm.blockentity.machine.storage.BlockEntityMachineBattery.MODE_OUTPUT);
		batteryBe.refreshMode();

		SchematicannonBlockEntity cannon = helper.getBlockEntity(cannonPos, SchematicannonBlockEntity.class);
		helper.assertTrue(cannon.isElectric(), "advanced cannon is not electric");
		helper.assertTrue(!cannon.inventory.canPlaceItem(SchematicannonBlockEntity.SLOT_GUNPOWDER, new ItemStack(Items.GUNPOWDER)),
			"advanced cannon accepts gunpowder");

		// a second cannon with no cables, charged only from a battery in its battery slot
		BlockPos batteryCannonPos = new BlockPos(8, 1, 2);
		helper.setBlock(batteryCannonPos, ModBlocks.ADVANCED_SCHEMATICANNON.get());
		SchematicannonBlockEntity batteryCannon = helper.getBlockEntity(batteryCannonPos, SchematicannonBlockEntity.class);
		ItemStack creativeBattery = new ItemStack(BuiltInRegistries.ITEM.getValue(
			net.minecraft.resources.Identifier.parse("hbm:battery_creative")));
		helper.assertTrue(batteryCannon.inventory.canPlaceItem(SchematicannonBlockEntity.SLOT_GUNPOWDER, creativeBattery),
			"battery slot refuses an NTM battery");
		batteryCannon.inventory.setItem(SchematicannonBlockEntity.SLOT_GUNPOWDER, creativeBattery);

		// source: a 4-block row of stone, rebuilt 4 blocks further north
		BlockPos src = helper.absolutePos(new BlockPos(1, 1, 8));
		for (int i = 0; i < 4; i++)
			level.setBlockAndUpdate(src.east(i), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_powered", true, level, src, src.east(3));
		helper.assertTrue(result != null, "schematic export failed");
		BlockPos dst = src.south(3);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), dst);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);

		long[] charged = { -1 };
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(cannon.power > 0, "cannon never received power from the cable");
				helper.assertValueEqual(batteryCannon.power, batteryCannon.getMaxPower(), "power drained from the battery slot");
			})
			.thenExecute(() -> {
				var cableState = helper.getBlockState(new BlockPos(3, 1, 2));
				var east = cableState.getProperties().stream().filter(p -> p.getName().equals("east")).findFirst();
				helper.assertTrue(east.isPresent() && Boolean.TRUE.equals(cableState.getValue(east.get())),
					"cable does not connect to the cannon's power port");
				charged[0] = cannon.power;
				Schematicannon.LOGGER.info("[gametest] advanced cannon charged to {} HE", cannon.power);
				cannon.state = SchematicannonBlockEntity.State.RUNNING;
				cannon.statusMsg = "running";
			})
			.thenWaitUntil(() -> {
				helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
				for (int i = 0; i < 4; i++)
					helper.assertTrue(level.getBlockState(dst.east(i)).is(net.minecraft.world.level.block.Blocks.STONE),
						"stone missing at destination");
				helper.assertValueEqual(chest.getItem(0).getCount(), 12, "stone left in the chest");
			})
			.thenSucceed();
	}

	/**
	 * A finished Watz has no survival item: breaking it gives back its parts. Its requirement must be those parts (not
	 * "1x Watz"), and a cannon fed only with those parts must rebuild the finished reactor.
	 */
	static void cannonBuildsWatzFromParts(GameTestHelper helper) {
		var level = helper.getLevel();
		Block watz = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:watz"));
		helper.assertTrue(watz instanceof BlockMultiblockCore, "no hbm:watz core");
		BlockState coreState = watz.defaultBlockState();

		var requirement = ItemRequirement.of(coreState, null);
		helper.assertTrue(!requirement.isInvalid() && !requirement.isEmpty(), "watz requirement invalid");
		var stacks = requirement.getRequiredItems().stream().map(r -> r.stack).toList();
		helper.assertTrue(stacks.stream().noneMatch(s -> s.is(watz.asItem())), "watz still charges the unobtainable core item");
		helper.assertTrue(stacks.stream().anyMatch(s -> s.is(BuiltInRegistries.ITEM.getValue(
			net.minecraft.resources.Identifier.parse("hbm:watz_element")))), "watz requirement lacks watz elements: " + stacks);
		Schematicannon.LOGGER.info("[gametest] watz costs {}", stacks);

		// build a finished Watz, save it, and print it from chests holding exactly those parts
		// (in its own loaded area, away from the other tests' machines)
		BlockPos srcCore = TestChunks.isolated(helper, 13, new BlockPos(4, 2, 4));
		var forced = TestChunks.force(level, srcCore.offset(-16, 0, -16), srcCore.offset(32, 0, 32));
		NtmCompat.assemble(level, srcCore, coreState);
		BoundingBox box = BoundingBox.fromCorners(srcCore, srcCore);
		for (BlockPos p : BlockPos.betweenClosed(srcCore.offset(-4, -4, -4), srcCore.offset(4, 6, 4)))
			if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
				box = box.encapsulate(p);
		int cells = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
			if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
				cells++;
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_watz", true, level, min, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
		helper.assertTrue(result != null, "schematic export failed");

		BlockPos dstCore = srcCore.east(SHIFT);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), min.east(SHIFT));
		BlockPos cannonPos = srcCore.offset(5, -1, 10);
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_GUNPOWDER, new ItemStack(Items.GUNPOWDER, 4));
		// chests on three sides, filled with the parts
		var chests = new ArrayList<net.minecraft.world.level.block.entity.ChestBlockEntity>();
		for (Direction d : new Direction[] { Direction.WEST, Direction.EAST, Direction.SOUTH }) {
			level.setBlockAndUpdate(cannonPos.relative(d), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
			chests.add((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(cannonPos.relative(d)));
		}
		int slot = 0;
		for (ItemStack s : stacks) {
			chests.get(slot / 27).setItem(slot % 27, s.copy());
			slot++;
		}
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		SchematicannonGameTests.pressStart(helper, cannon);

		int expectedCells = cells;
		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
			helper.assertTrue(level.getBlockState(dstCore).is(watz), "watz core missing at destination");
			int copyCells = 0;
			for (BlockPos p : BlockPos.betweenClosed(dstCore.offset(-4, -4, -4), dstCore.offset(4, 6, 4)))
				if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
					copyCells++;
			helper.assertValueEqual(copyCells, expectedCells, "watz cells");
			for (var chest : chests)
				helper.assertTrue(chest.isEmpty(), "parts left over in a chest");
			TestChunks.release(level, forced);
		});
	}

	private static BlockState paintCamo(net.minecraft.world.level.block.entity.BlockEntity be) {
		if (be instanceof com.hbm.blockentity.network.BlockEntityPipePaintable p)
			return p.getCamo();
		if (be instanceof com.hbm.blockentity.network.pneumatic.BlockEntityPneumoTubePaintable p)
			return p.getCamo();
		return null;
	}

	private static void paint(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState camo) {
		var be = level.getBlockEntity(pos);
		if (be instanceof com.hbm.blockentity.network.BlockEntityPipePaintable p)
			p.setCamo(camo);
		else if (be instanceof com.hbm.blockentity.network.pneumatic.BlockEntityPneumoTubePaintable p)
			p.setCamo(camo);
		var state = level.getBlockState(pos);
		var painted = ((com.hbm.blocks.network.PaintableCamoBlock) state.getBlock()).paintedProperty();
		level.setBlock(pos, state.setValue(painted, true), 3);
	}

	/**
	 * Painted NTM cable, fluid duct (also set to natural gas) and pneumatic tube: the copies must keep their paint (and
	 * the duct its fluid), and the cannon must use up the paint blocks as well as the cable/duct/tube items.
	 */
	static void cannonKeepsPaint(GameTestHelper helper) {
		var level = helper.getLevel();
		String[] ids = { "red_cable_paintable", "fluid_duct_paintable", "pneumatic_tube_paintable" };
		BlockState[] paints = { net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState(),
			net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),
			net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() };
		var gas = BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse("hbm:gas"));

		// spaced out so the three never connect to each other; in its own loaded area, away from other tests' blocks
		BlockPos src = TestChunks.isolated(helper, 14, new BlockPos(1, 1, 1));
		var forced = TestChunks.force(level, src.offset(-16, 0, -16), src.offset(32, 0, 32));
		for (int i = 0; i < ids.length; i++) {
			Block block = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:" + ids[i]));
			helper.assertTrue(block instanceof com.hbm.blocks.network.PaintableCamoBlock, ids[i] + " is not paintable");
			level.setBlockAndUpdate(src.east(i * 2), block.defaultBlockState());
			paint(level, src.east(i * 2), paints[i]);
		}
		com.hbm.blocks.network.FluidPipeBlock.retypeRun(level, src.east(2).asLong(), gas);
		helper.assertValueEqual(com.hbm.blocks.network.FluidPipeBlock.pipeFluidAt(level, src.east(2)), gas, "source duct fluid");

		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_paint", true, level, src, src.east(4));
		helper.assertTrue(result != null, "schematic export failed");

		BlockPos dst = src.south(4);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), dst);
		BlockPos cannonPos = src.offset(1, 0, 8);
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_GUNPOWDER, new ItemStack(Items.GUNPOWDER));
		level.setBlockAndUpdate(cannonPos.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
		var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(cannonPos.south());
		for (int i = 0; i < ids.length; i++) {
			chest.setItem(i, new ItemStack(BuiltInRegistries.BLOCK.getValue(
				net.minecraft.resources.Identifier.parse("hbm:" + ids[i])).asItem()));
			chest.setItem(3 + i, new ItemStack(paints[i].getBlock().asItem()));
		}
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		SchematicannonGameTests.pressStart(helper, cannon);

		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
			for (int i = 0; i < ids.length; i++) {
				BlockPos p = dst.east(i * 2);
				var state = level.getBlockState(p);
				helper.assertTrue(state.getBlock() instanceof com.hbm.blocks.network.PaintableCamoBlock, ids[i] + " missing");
				helper.assertValueEqual(paintCamo(level.getBlockEntity(p)), paints[i], ids[i] + " paint");
				helper.assertTrue(state.getValue(((com.hbm.blocks.network.PaintableCamoBlock) state.getBlock()).paintedProperty()),
					ids[i] + " not shown as painted");
			}
			helper.assertValueEqual(com.hbm.blocks.network.FluidPipeBlock.pipeFluidAt(level, dst.east(2)), gas,
				"painted duct fluid");
			helper.assertTrue(chest.isEmpty(), "paint blocks / items were not all used: "
				+ java.util.stream.IntStream.range(0, 6).mapToObj(chest::getItem).toList());
			TestChunks.release(level, forced);
		});
	}

	private static String pickRecipe(com.hbm.modules.machine.ModuleMachineBase module) {
		return pickRecipe(module, false).getInternalName();
	}

	/** A recipe of this machine; {@code pooled}: one that needs a blueprint. */
	private static com.hbm.inventory.recipes.loader.GenericRecipe pickRecipe(com.hbm.modules.machine.ModuleMachineBase module,
		boolean pooled) {
		try {
			var f = com.hbm.modules.machine.ModuleMachineBase.class.getDeclaredField("recipeSet");
			f.setAccessible(true);
			var recipes = ((com.hbm.inventory.recipes.loader.GenericRecipes<?, ?>) f.get(module)).recipes();
			return recipes.stream().filter(r -> r.isPooled() == pooled).skip(2).findFirst().orElseThrow();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/** Sets every non-final String field and every String array element (and those of nested NTM unit objects). */
	private static void fillStrings(Object obj, String value, int depth) {
		for (Class<?> c = obj.getClass(); c != null && c != Object.class
			&& c != net.minecraft.world.level.block.entity.BlockEntity.class; c = c.getSuperclass()) {
			for (var f : c.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().contains("$"))
					continue;
				try {
					f.setAccessible(true);
					Object v = f.get(obj);
					if (f.getType() == String.class && !java.lang.reflect.Modifier.isFinal(f.getModifiers()))
						f.set(obj, value);
					else if (v instanceof String[] arr)
						java.util.Arrays.fill(arr, value);
					else if (depth > 0 && v instanceof Object[] arr && f.getType().getComponentType().getName().startsWith("com.hbm."))
						for (Object o : arr)
							if (o != null)
								fillStrings(o, value, depth - 1);
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
	}

	/** Reads an int triple {@code <name>X/Y/Z} (private on some NTM blocks). */
	private static BlockPos intTriple(Object be, String name) {
		try {
			int[] v = new int[3];
			String[] axis = { "X", "Y", "Z" };
			for (int i = 0; i < 3; i++) {
				java.lang.reflect.Field f = null;
				for (Class<?> c = be.getClass(); f == null && c != null; c = c.getSuperclass())
					try {
						f = c.getDeclaredField(name + axis[i]);
					} catch (NoSuchFieldException ignored) {
					}
				f.setAccessible(true);
				v[i] = f.getInt(be);
			}
			return new BlockPos(v[0], v[1], v[2]);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static Block hbm(String id) {
		return BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:" + id));
	}

	/**
	 * Every NTM setting players asked about, in one build: radio torch channels, mass storage item type, funnel mode,
	 * ejector whitelist + filter, inserter "destroy overflow", the recipes of the chemical plant / factory and assembly
	 * machine / factory, wired pylons and linked pipe anchors. The copies must keep all of it, and none of the contents
	 * (buffer items, mass storage stock, power, fluids) may come along.
	 */
	static void cannonKeepsNtmSettings(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos base = TestChunks.isolated(helper, 12, new BlockPos(2, 2, 2));
		var forced = TestChunks.force(level, base.offset(-16, 0, -16), base.offset(120, 0, 64));
		var checks = new ArrayList<java.util.function.Consumer<BlockPos>>(); // run against each copy position
		var positions = new ArrayList<BlockPos>();
		int[] x = { 0 };
		java.util.function.BiFunction<BlockState, Integer, BlockPos> put = (state, width) -> {
			BlockPos p = base.east(x[0] + width / 2);
			x[0] += width + 2;
			level.setBlockAndUpdate(p.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
			if (state.getBlock() instanceof BlockMultiblockCore)
				NtmCompat.assemble(level, p, state.trySetValue(BlockMultiblockCore.FACING, Direction.NORTH));
			else
				level.setBlockAndUpdate(p, state);
			positions.add(p);
			return p;
		};

		// radio torches
		for (String id : new String[] { "radio_torch_sender", "radio_torch_receiver" }) {
			BlockPos p = put.apply(hbm(id).defaultBlockState(), 1);
			((com.hbm.blockentity.network.BlockEntityRadioTorch) level.getBlockEntity(p)).channel = "atomica_" + id;
			level.getBlockEntity(p).setChanged();
			checks.add(c -> helper.assertValueEqual(((com.hbm.blockentity.network.BlockEntityRadioTorch) level.getBlockEntity(c)).channel,
				"atomica_" + id, id + " channel"));
		}
		// mass storage: type kept, stock not
		{
			BlockPos p = put.apply(hbm("mass_storage_iron").defaultBlockState(), 1);
			var ms = (com.hbm.blockentity.machine.storage.BlockEntityMassStorage) level.getBlockEntity(p);
			ms.setItem(com.hbm.blockentity.machine.storage.BlockEntityMassStorage.SLOT_TYPE, new ItemStack(Items.COBBLESTONE));
			ms.setStockpile(500);
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.storage.BlockEntityMassStorage) level.getBlockEntity(c);
				helper.assertTrue(copy.getItem(com.hbm.blockentity.machine.storage.BlockEntityMassStorage.SLOT_TYPE).is(Items.COBBLESTONE),
					"mass storage type");
				helper.assertValueEqual(copy.getStockpile(), 0, "mass storage stock (must not be copied)");
			});
		}
		// funnel mode
		{
			BlockPos p = put.apply(hbm("machine_funnel").defaultBlockState(), 1);
			var funnel = (com.hbm.blockentity.machine.BlockEntityMachineFunnel) level.getBlockEntity(p);
			funnel.mode = com.hbm.blockentity.machine.BlockEntityMachineFunnel.MODE_2X2;
			funnel.setItem(0, new ItemStack(Items.IRON_INGOT, 9));
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.BlockEntityMachineFunnel) level.getBlockEntity(c);
				helper.assertValueEqual(copy.mode, com.hbm.blockentity.machine.BlockEntityMachineFunnel.MODE_2X2, "funnel mode");
				helper.assertTrue(copy.getItem(0).isEmpty(), "funnel contents copied");
			});
		}
		// conveyor ejector: whitelist + filter kept, buffer not
		{
			BlockPos p = put.apply(hbm("crane_extractor").defaultBlockState(), 1);
			var ej = (com.hbm.blockentity.network.BlockEntityCraneExtractor) level.getBlockEntity(p);
			ej.isWhitelist = true;
			ej.setItem(0, new ItemStack(Items.DIAMOND));
			ej.setItem(com.hbm.blockentity.network.BlockEntityCraneExtractor.SLOT_BUFFER, new ItemStack(Items.GOLD_INGOT, 5));
			checks.add(c -> {
				var copy = (com.hbm.blockentity.network.BlockEntityCraneExtractor) level.getBlockEntity(c);
				helper.assertTrue(copy.isWhitelist, "ejector whitelist mode");
				helper.assertTrue(copy.getItem(0).is(Items.DIAMOND), "ejector filter");
				helper.assertTrue(copy.getItem(com.hbm.blockentity.network.BlockEntityCraneExtractor.SLOT_BUFFER).isEmpty(),
					"ejector buffer copied");
			});
		}
		// inserter destroy overflow
		{
			BlockPos p = put.apply(hbm("crane_inserter").defaultBlockState(), 1);
			((com.hbm.blockentity.network.BlockEntityCraneInserter) level.getBlockEntity(p)).destroyer = true;
			checks.add(c -> helper.assertTrue(((com.hbm.blockentity.network.BlockEntityCraneInserter) level.getBlockEntity(c)).destroyer,
				"inserter destroy overflow"));
		}
		// recipe machines
		{
			BlockPos p = put.apply(hbm("machine_chemical_plant").defaultBlockState(), 5);
			var m = (com.hbm.blockentity.machine.BlockEntityMachineChemicalPlant) level.getBlockEntity(p);
			String r = pickRecipe(m.module);
			m.module.setRecipe(r);
			m.power = 50_000;
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.BlockEntityMachineChemicalPlant) level.getBlockEntity(c);
				helper.assertValueEqual(copy.module.getRecipeName(), r, "chemical plant recipe");
				helper.assertValueEqual(copy.power, 0L, "chemical plant power copied");
			});
		}
		{
			BlockPos p = put.apply(hbm("machine_chemical_factory").defaultBlockState(), 7);
			var m = (com.hbm.blockentity.machine.BlockEntityMachineChemicalFactory) level.getBlockEntity(p);
			String r = pickRecipe(m.module[0]);
			m.module[0].setRecipe(r);
			checks.add(c -> helper.assertValueEqual(((com.hbm.blockentity.machine.BlockEntityMachineChemicalFactory) level.getBlockEntity(c))
				.module[0].getRecipeName(), r, "chemical factory recipe"));
		}
		{
			BlockPos p = put.apply(hbm("machine_assembly_machine").defaultBlockState(), 5);
			var m = (com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine) level.getBlockEntity(p);
			String r = pickRecipe(m.recipeModule);
			m.recipeModule.setRecipe(r);
			checks.add(c -> helper.assertValueEqual(((com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine) level.getBlockEntity(c))
				.recipeModule.getRecipeName(), r, "assembly machine recipe"));
		}
		{
			// a recipe that needs a blueprint: the blueprint pastes with it, and the cannon charges an identical one
			BlockPos p = put.apply(hbm("machine_assembly_machine").defaultBlockState(), 5);
			var m = (com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine) level.getBlockEntity(p);
			var recipe = pickRecipe(m.recipeModule, true);
			ItemStack blueprint = com.hbm.items.machine.ItemBlueprints.make(recipe.getPools()[0]);
			m.setItem(com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine.SLOT_BLUEPRINT, blueprint.copy());
			m.recipeModule.setRecipe(recipe.getInternalName());
			var raw = m.saveWithFullMetadata(level.registryAccess());
			var cost = ItemRequirement.of(level.getBlockState(p), raw).getRequiredItems();
			helper.assertTrue(cost.stream().anyMatch(r -> r.exact && ItemStack.isSameItemSameComponents(r.stack, blueprint)),
				"assembly machine with a blueprint does not charge that blueprint: " + cost.stream().map(r -> r.stack).toList());
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine) level.getBlockEntity(c);
				helper.assertValueEqual(copy.recipeModule.getRecipeName(), recipe.getInternalName(), "blueprint recipe");
				helper.assertTrue(ItemStack.isSameItemSameComponents(
					copy.getItem(com.hbm.blockentity.machine.BlockEntityMachineAssemblyMachine.SLOT_BLUEPRINT), blueprint),
					"blueprint not pasted");
			});
		}
		{
			BlockPos p = put.apply(hbm("machine_assembly_factory").defaultBlockState(), 7);
			var m = (com.hbm.blockentity.machine.BlockEntityMachineAssemblyFactory) level.getBlockEntity(p);
			String r = pickRecipe(m.module[0]);
			m.module[0].setRecipe(r);
			checks.add(c -> helper.assertValueEqual(((com.hbm.blockentity.machine.BlockEntityMachineAssemblyFactory) level.getBlockEntity(c))
				.module[0].getRecipeName(), r, "assembly factory recipe"));
		}
		// industrial mixer making solvent: the output (and so the recipe) must not become its first input
		{
			BlockPos p = put.apply(hbm("machine_mixer").defaultBlockState(), 1);
			var mixer = (com.hbm.blockentity.machine.BlockEntityMachineMixer) level.getBlockEntity(p);
			var solvent = BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse("hbm:solvent"));
			mixer.tanks[com.hbm.blockentity.machine.BlockEntityMachineMixer.TANK_OUT].setTankType(solvent);
			mixer.recipeIndex = 0;
			checks.add(c -> {
				var src = (com.hbm.blockentity.machine.BlockEntityMachineMixer) level.getBlockEntity(p);
				var copy = (com.hbm.blockentity.machine.BlockEntityMachineMixer) level.getBlockEntity(c);
				for (int i = 0; i < src.tanks.length; i++)
					helper.assertValueEqual(BuiltInRegistries.FLUID.getKey(copy.tanks[i].getTankType()),
						BuiltInRegistries.FLUID.getKey(src.tanks[i].getTankType()), "mixer tank " + i);
				helper.assertValueEqual(BuiltInRegistries.FLUID.getKey(copy.tanks[com.hbm.blockentity.machine.BlockEntityMachineMixer.TANK_OUT]
					.getTankType()), BuiltInRegistries.FLUID.getKey(solvent), "mixer output (recipe)");
				helper.assertValueEqual(copy.recipeIndex, src.recipeIndex, "mixer recipe index");
			});
		}
		// crucible: recipe kept, molten metal not
		{
			BlockPos p = put.apply(hbm("machine_crucible").defaultBlockState(), 3);
			var crucible = (com.hbm.blockentity.machine.BlockEntityCrucible) level.getBlockEntity(p);
			var recipes = com.hbm.inventory.recipes.CrucibleRecipes.INSTANCE.recipes();
			String r = recipes.get(Math.min(2, recipes.size() - 1)).getInternalName();
			crucible.recipe = r;
			crucible.recipeStack.add(new com.hbm.inventory.material.Mats.MaterialStack(com.hbm.inventory.material.Mats.MAT_IRON, 500));
			crucible.wasteStack.add(new com.hbm.inventory.material.Mats.MaterialStack(com.hbm.inventory.material.Mats.MAT_IRON, 200));
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.BlockEntityCrucible) level.getBlockEntity(c);
				helper.assertValueEqual(copy.recipe, r, "crucible recipe");
				helper.assertTrue(copy.recipeStack.isEmpty() && copy.wasteStack.isEmpty(), "crucible metal copied");
			});
		}
		// radar options
		BlockPos radarPos;
		{
			BlockPos p = put.apply(hbm("machine_radar").defaultBlockState(), 1);
			radarPos = p;
			var radar = (com.hbm.blockentity.machine.BlockEntityMachineRadar) level.getBlockEntity(p);
			boolean players = !radar.scanPlayers, smart = !radar.smartMode, red = !radar.redMode, map = !radar.showMap;
			radar.scanPlayers = players;
			radar.smartMode = smart;
			radar.redMode = red;
			radar.showMap = map;
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.BlockEntityMachineRadar) level.getBlockEntity(c);
				helper.assertTrue(copy.scanPlayers == players && copy.smartMode == smart && copy.redMode == red
					&& copy.showMap == map, "radar settings");
			});
		}
		// conveyor grabber: whitelist + filter
		{
			BlockPos p = put.apply(hbm("crane_grabber").defaultBlockState(), 1);
			var grabber = (com.hbm.blockentity.network.BlockEntityCraneGrabber) level.getBlockEntity(p);
			grabber.isWhitelist = true;
			grabber.setItem(0, new ItemStack(Items.EMERALD));
			checks.add(c -> {
				var copy = (com.hbm.blockentity.network.BlockEntityCraneGrabber) level.getBlockEntity(c);
				helper.assertTrue(copy.isWhitelist, "grabber whitelist mode");
				helper.assertTrue(copy.getItem(0).is(Items.EMERALD), "grabber filter");
			});
		}
		// conveyor sorter: side modes + filters
		{
			BlockPos p = put.apply(hbm("crane_router").defaultBlockState(), 1);
			var router = (com.hbm.blockentity.network.BlockEntityCraneRouter) level.getBlockEntity(p);
			router.modes[2] = com.hbm.blockentity.network.BlockEntityCraneRouter.MODE_WHITELIST;
			router.setItem(2 * com.hbm.blockentity.network.BlockEntityCraneRouter.FILTERS_PER_SIDE, new ItemStack(Items.REDSTONE));
			checks.add(c -> {
				var copy = (com.hbm.blockentity.network.BlockEntityCraneRouter) level.getBlockEntity(c);
				helper.assertValueEqual(copy.modes[2], com.hbm.blockentity.network.BlockEntityCraneRouter.MODE_WHITELIST, "sorter side mode");
				helper.assertTrue(copy.getItem(2 * com.hbm.blockentity.network.BlockEntityCraneRouter.FILTERS_PER_SIDE).is(Items.REDSTONE),
					"sorter filter");
			});
		}
		// solar mirror aimed at a solar boiler: the copy must aim at the copied boiler
		BlockPos boiler = put.apply(hbm("machine_solar_boiler").defaultBlockState(), 3);
		checks.add(c -> {}); // (checks pair up with positions by index)
		{
			BlockPos p = put.apply(hbm("solar_mirror").defaultBlockState(), 1);
			var mirror = (com.hbm.blockentity.machine.BlockEntitySolarMirror) level.getBlockEntity(p);
			mirror.setTarget(boiler.getX(), boiler.getY() + 2, boiler.getZ());
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.BlockEntitySolarMirror) level.getBlockEntity(c);
				BlockPos expected = boiler.south(14).above(2);
				helper.assertValueEqual(new BlockPos(copy.tX, copy.tY, copy.tZ), expected, "solar mirror aim");
			});
		}
		// displays linked by position: RBMK console + display to an RBMK column, radar screen to the radar, crane console
		BlockPos rbmk = put.apply(hbm("rbmk_blank").defaultBlockState(), 3);
		checks.add(c -> {});
		BlockPos rbmkCopy = rbmk.south(14);
		{
			BlockPos p = put.apply(hbm("rbmk_console").defaultBlockState(), 5);
			((com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole) level.getBlockEntity(p)).setTarget(rbmk.getX(), rbmk.getY(),
				rbmk.getZ());
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole) level.getBlockEntity(c);
				helper.assertValueEqual(new BlockPos(copy.targetX(), copy.targetY(), copy.targetZ()), rbmkCopy, "RBMK console target");
			});
		}
		{
			BlockPos p = put.apply(hbm("rbmk_display").defaultBlockState(), 1);
			((com.hbm.blockentity.machine.rbmk.BlockEntityRBMKDisplay) level.getBlockEntity(p)).setTarget(rbmk.getX(), rbmk.getY(),
				rbmk.getZ());
			checks.add(c -> helper.assertValueEqual(intTriple(level.getBlockEntity(c), "target"), rbmkCopy, "RBMK display target"));
		}
		{
			// radar screens are linked with a Radar Linker in the radar's linker slot, aimed at the screen
			BlockPos p = put.apply(hbm("radar_screen").defaultBlockState(), 3);
			var radar = (com.hbm.blockentity.machine.BlockEntityMachineRadar) level.getBlockEntity(radarPos);
			ItemStack linker = new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("hbm:radar_linker")));
			linker.set(com.hbm.items.ModDataComponents.RADAR_LINKER_TARGET.get(), p.asLong());
			radar.setItem(com.hbm.blockentity.machine.BlockEntityMachineRadar.SLOT_LINKER, linker);
			var cost = ItemRequirement.of(level.getBlockState(radarPos), radar.saveWithFullMetadata(level.registryAccess()))
				.getRequiredItems().stream().map(r -> r.stack).toList();
			helper.assertTrue(cost.stream().anyMatch(s -> s.is(linker.getItem())), "radar does not charge its linker: " + cost);
			checks.add(c -> {
				var copyRadar = (com.hbm.blockentity.machine.BlockEntityMachineRadar) level.getBlockEntity(radarPos.south(14));
				var copyLinker = copyRadar.getItem(com.hbm.blockentity.machine.BlockEntityMachineRadar.SLOT_LINKER);
				helper.assertTrue(copyLinker.is(linker.getItem()), "radar linker not pasted");
				helper.assertValueEqual(com.hbm.items.tool.ItemRadarLinker.getPosition(copyLinker), c, "radar linker aim (screen)");
			});
		}
		{
			BlockPos p = put.apply(hbm("rbmk_crane_console").defaultBlockState(), 3);
			((com.hbm.blockentity.machine.rbmk.BlockEntityCraneConsole) level.getBlockEntity(p)).setTarget(rbmk.getX(), rbmk.getY(),
				rbmk.getZ());
			BlockPos center = intTriple(level.getBlockEntity(p), "center");
			checks.add(c -> {
				var copy = (com.hbm.blockentity.machine.rbmk.BlockEntityCraneConsole) level.getBlockEntity(c);
				helper.assertValueEqual(new BlockPos(copy.centerX, copy.centerY, copy.centerZ), center.south(14), "crane console center");
			});
		}
		// redstone-over-radio and RBMK displays: channels, labels, scripts, gauge/indicator/lever units. Every String
		// they hold (directly or in their units) gets a value; the copy's saved data must then equal the original's.
		for (String id : new String[] { "radio_torch_reader", "radio_torch_controller", "radio_torch_logic", "radio_telex",
			"radio_autocal", "rbmk_gauge", "rbmk_indicator", "rbmk_lever" }) {
			BlockPos p = put.apply(hbm(id).defaultBlockState(), 1);
			var be = level.getBlockEntity(p);
			helper.assertTrue(be != null, id + " has no block entity");
			fillStrings(be, "atomica_" + id, 2);
			if (be instanceof com.hbm.blockentity.network.BlockEntityRadioAUTOCAL autocal) {
				autocal.script = new String[] { "atomica_" + id, "second line" };
				autocal.ignoreError = true;
				autocal.autoReboot = true;
			}
			be.setChanged();
			checks.add(c -> {
				var a = level.getBlockEntity(p).saveCustomOnly(level.registryAccess());
				var b = level.getBlockEntity(c).saveCustomOnly(level.registryAccess());
				// run state of a running AUTOCAL script, not settings
				for (String runtime : new String[] { "isOn", "current", "buffer", "stack", "variables" }) {
					a.remove(runtime);
					b.remove(runtime);
				}
				helper.assertTrue(a.toString().contains("atomica_" + id), id + " test values were not saved by NTM");
				helper.assertValueEqual(b.toString(), a.toString(), id + " settings");
			});
		}
		// wired pylons
		BlockPos pylonA = put.apply(hbm("red_pylon").defaultBlockState(), 1);
		BlockPos pylonB = put.apply(hbm("red_pylon").defaultBlockState(), 3);
		com.hbm.api.energymk2.PowerGraph.get(level).addRemoteLink(pylonA.asLong(), pylonB.asLong());
		((com.hbm.blockentity.network.BlockEntityPylonBase) level.getBlockEntity(pylonA)).refreshConnections();
		((com.hbm.blockentity.network.BlockEntityPylonBase) level.getBlockEntity(pylonB)).refreshConnections();
		// linked pipe anchors (natural gas)
		var gas = BuiltInRegistries.FLUID.getValue(net.minecraft.resources.Identifier.parse("hbm:gas"));
		BlockPos anchorA = put.apply(hbm("pipe_anchor").defaultBlockState(), 1);
		BlockPos anchorB = put.apply(hbm("pipe_anchor").defaultBlockState(), 3);
		com.hbm.blocks.network.FluidPipeBlock.retypeRun(level, anchorA.asLong(), gas);
		com.hbm.blocks.network.FluidPipeBlock.retypeRun(level, anchorB.asLong(), gas);
		com.hbm.api.fluidmk2.FluidPipeGraph.get(level, gas).addRemoteLink(anchorA.asLong(), anchorB.asLong());

		int shiftZ = 14;
		var copyChecks = new ArrayList<Runnable>();
		helper.startSequence()
			// pipe anchors join NTM's pipe network a little after placement: (re)type and link until it shows
			.thenWaitUntil(() -> {
				var a = com.hbm.api.fluidmk2.FluidPipeGraph.dataAt(level, anchorA.asLong());
				var b = com.hbm.api.fluidmk2.FluidPipeGraph.dataAt(level, anchorB.asLong());
				// retype only what is not natural gas yet (retyping resets a node), link once both are
				if (a != null && a.fluid() != gas)
					com.hbm.blocks.network.FluidPipeBlock.retypeRun(level, anchorA.asLong(), gas);
				if (b != null && b.fluid() != gas)
					com.hbm.blocks.network.FluidPipeBlock.retypeRun(level, anchorB.asLong(), gas);
				if (a != null && b != null && a.fluid() == gas && b.fluid() == gas
					&& ((com.hbm.blockentity.network.BlockEntityPipeAnchor) level.getBlockEntity(anchorA)).connections.length == 0)
					com.hbm.api.fluidmk2.FluidPipeGraph.get(level, gas).addRemoteLink(anchorA.asLong(), anchorB.asLong());
				helper.assertTrue(((com.hbm.blockentity.network.BlockEntityPipeAnchor) level.getBlockEntity(anchorA)).connections.length > 0,
					"source pipe anchors not linked yet");
			})
			.thenExecute(() -> {
				BoundingBox box = BoundingBox.fromCorners(base.below(), base.below());
				for (BlockPos p : positions)
					for (BlockPos q : BlockPos.betweenClosed(p.offset(-4, -1, -4), p.offset(4, 6, 4)))
						if (!level.getBlockState(q).isAir())
							box = box.encapsulate(q);
				BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
				var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
					"gametest_ntm_settings", true, level, min, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
				helper.assertTrue(result != null, "schematic export failed");
				ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
				schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
				schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), min.south(shiftZ));
				BlockPos cannonPos = new BlockPos(base.getX() + 20, min.getY(), box.maxZ() + shiftZ + 4);
				level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
				level.setBlockAndUpdate(cannonPos.south(), ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
				var cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
				cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
				SchematicannonGameTests.pressStart(helper, cannon);
				copyChecks.add(() -> {
					helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
					helper.assertTrue(cannon.flyingBlocks.isEmpty(), "blocks still in flight");
				});
			})
			.thenWaitUntil(() -> copyChecks.forEach(Runnable::run))
			// retried every tick until everything holds: blocks such as pipe anchors refresh their links on their own
			// schedule after landing
			.thenWaitUntil(() -> {
				for (int i = 0; i < checks.size(); i++) {
					BlockPos copyPos = positions.get(i).south(shiftZ);
					try {
						checks.get(i).accept(copyPos);
					} catch (NullPointerException | ClassCastException e) {
						// block (entity) not there (yet): a test failure to retry, never a server crash
						helper.assertTrue(false, "copy at " + copyPos + " is " + level.getBlockState(copyPos) + " / "
							+ level.getBlockEntity(copyPos) + " (source " + level.getBlockState(positions.get(i)) + "): " + e);
					}
				}
				var a = (com.hbm.blockentity.network.BlockEntityPylonBase) level.getBlockEntity(pylonA.south(shiftZ));
				helper.assertTrue(a != null && java.util.Arrays.stream(a.connections).anyMatch(l -> l == pylonB.south(shiftZ).asLong()),
					"pylon copies are not wired together");
				var anchor = (com.hbm.blockentity.network.BlockEntityPipeAnchor) level.getBlockEntity(anchorA.south(shiftZ));
				helper.assertTrue(anchor != null && java.util.Arrays.stream(anchor.connections).anyMatch(l -> l == anchorB.south(shiftZ).asLong()),
					"pipe anchor copies are not linked");
			})
			.thenExecute(() -> Schematicannon.LOGGER.info("[gametest] NTM settings all kept ({} blocks checked + pylons + pipe anchors)",
				checks.size()))
			.thenExecute(() -> TestChunks.release(level, forced))
			.thenSucceed();
	}

	/**
	 * A battery socket saved with a fully charged battery: the copy gets that battery type, drained, and the cannon uses
	 * up a battery of that type from its chest, the emptiest first (a full one in the same chest stays).
	 */
	static void cannonPastesSocketBattery(GameTestHelper helper) {
		var level = helper.getLevel();
		Block socket = hbm("machine_battery_socket");
		helper.assertTrue(socket instanceof BlockMultiblockCore, "no battery socket core");
		net.minecraft.world.item.Item batteryItem = null;
		for (var item : BuiltInRegistries.ITEM)
			if (com.hbm.api.energymk2.IBatteryItem.isBattery(new ItemStack(item))
				&& ((com.hbm.api.energymk2.IBatteryItem) item).getMaxCharge(new ItemStack(item)) > 0) {
				batteryItem = item;
				break;
			}
		helper.assertTrue(batteryItem != null, "no NTM battery item");
		var battery = (com.hbm.api.energymk2.IBatteryItem) batteryItem;
		ItemStack full = new ItemStack(batteryItem);
		battery.setCharge(full, battery.getMaxCharge(full));
		ItemStack empty = new ItemStack(batteryItem);
		battery.setCharge(empty, 0);

		BlockPos srcCore = TestChunks.isolated(helper, 15, new BlockPos(2, 2, 2));
		var forced = TestChunks.force(level, srcCore.offset(-16, 0, -16), srcCore.offset(32, 0, 32));
		NtmCompat.assemble(level, srcCore, socket.defaultBlockState().trySetValue(BlockMultiblockCore.FACING, Direction.NORTH));
		var src = (com.hbm.blockentity.machine.storage.BlockEntityBatterySocket) level.getBlockEntity(srcCore);
		src.setItem(com.hbm.blockentity.machine.storage.BlockEntityBatterySocket.SLOT_BATTERY, full.copy());

		BoundingBox box = BoundingBox.fromCorners(srcCore, srcCore);
		for (BlockPos p : BlockPos.betweenClosed(srcCore.offset(-4, -2, -4), srcCore.offset(4, 6, 4)))
			if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
				box = box.encapsulate(p);
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_socket", true, level, min, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
		helper.assertTrue(result != null, "schematic export failed");

		BlockPos dstCore = srcCore.east(SHIFT);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), min.east(SHIFT));
		BlockPos cannonPos = srcCore.offset(4, -1, 8);
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		var cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_GUNPOWDER, new ItemStack(Items.GUNPOWDER));
		level.setBlockAndUpdate(cannonPos.south(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
		var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(cannonPos.south());
		chest.setItem(0, new ItemStack(socket.asItem()));
		// the battery provided: partly charged; it must end up in the socket exactly like this
		ItemStack provided = new ItemStack(batteryItem);
		long providedCharge = Math.max(1, battery.getMaxCharge(provided) / 3);
		battery.setCharge(provided, providedCharge);
		chest.setItem(1, provided.copy());
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		SchematicannonGameTests.pressStart(helper, cannon);

		var expectedItem = batteryItem;
		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
			var copy = (com.hbm.blockentity.machine.storage.BlockEntityBatterySocket) level.getBlockEntity(dstCore);
			helper.assertTrue(copy != null, "socket missing at destination");
			ItemStack pasted = copy.getItem(com.hbm.blockentity.machine.storage.BlockEntityBatterySocket.SLOT_BATTERY);
			helper.assertTrue(pasted.is(expectedItem), "socket battery not pasted: " + pasted);
			helper.assertValueEqual(battery.getCharge(pasted), providedCharge, "pasted battery keeps the provided battery's charge");
			helper.assertTrue(chest.getItem(0).isEmpty(), "socket item not used");
			helper.assertTrue(chest.getItem(1).isEmpty(), "provided battery not used");
			TestChunks.release(level, forced);
		});
	}

	/** Finished machines NTM assembles from parts around a structure core. */
	static final String[] PART_BUILT = { "watz", "icf", "fusion_torus", "soyuz_launcher", "compact_launcher", "launch_table" };
	/** Turbines whose steam density is switched with a lever. */
	static final String[] LEVER_TURBINES = { "machine_chungus", "machine_industrial_turbine" };

	/**
	 * A finished part-built machine: it must cost its parts (never its own unobtainable item) and the cannon must
	 * rebuild the whole finished machine.
	 */
	static void cannonRebuildsPartBuilt(GameTestHelper helper, String id) {
		Block block = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:" + id));
		helper.assertTrue(block instanceof BlockMultiblockCore, "hbm:" + id + " is not a multiblock core");
		var stacks = ItemRequirement.of(block.defaultBlockState(), null).getRequiredItems().stream().map(r -> r.stack).toList();
		helper.assertTrue(!stacks.isEmpty(), id + " has no requirement");
		var entry = com.hbm.handler.ConstructionCatalog.entries().stream().filter(e -> e.assembled().is(block.asItem())).findFirst();
		if (entry.isPresent()) {
			// built from a structure core + parts: costs those (as NTM's construction catalog lists them), never itself
			helper.assertTrue(stacks.stream().noneMatch(s -> s.is(block.asItem())), id + " charges the finished machine: " + stacks);
			helper.assertTrue(stacks.stream().anyMatch(s -> s.is(entry.get().seed().getItem())), id + " lacks its structure core: " + stacks);
			long parts = entry.get().pile().stream()
				.filter(o -> o.stream().filter(s -> !s.isEmpty()).findFirst().map(s -> s.getMaxStackSize() > 1).orElse(false)).count();
			helper.assertValueEqual((long) stacks.size(), 1 + parts, id + " parts (tools are not charged)");
			helper.assertTrue(stacks.stream().allMatch(s -> s.getMaxStackSize() > 1), id + " charges a tool: " + stacks);
		} else {
			// otherwise: exactly what breaking the finished machine gives back
			var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, ItemStack.EMPTY);
			helper.assertValueEqual(stacks.toString(), block.defaultBlockState().getDrops(params).toString(),
				id + " requirement vs what it breaks into");
		}
		Schematicannon.LOGGER.info("[gametest] {} costs {}", id, stacks);
		rebuildAndCompare(helper, block.defaultBlockState(), be -> {}, (src, dst) -> {});
	}

	/** A lever turbine set to super dense steam must come out of the cannon on super dense steam (both tanks). */
	static void cannonKeepsTurbineSteam(GameTestHelper helper, String id) {
		Block block = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:" + id));
		BlockState state = block.defaultBlockState().trySetValue(BlockMultiblockCore.FACING, Direction.NORTH);
		var types = new ArrayList<String>();
		var sizes = new ArrayList<Integer>();
		rebuildAndCompare(helper, state, be -> {
			var turbine = (com.hbm.blockentity.machine.BlockEntityTurbineBase) be;
			turbine.onLeverPull(); // dense
			turbine.onLeverPull(); // super dense
			for (var tank : turbine.tanks) {
				types.add(BuiltInRegistries.FLUID.getKey(tank.getTankType()).toString());
				sizes.add(tank.getMaxFill());
			}
		}, (src, dst) -> {
			helper.assertTrue(dst instanceof com.hbm.blockentity.machine.BlockEntityTurbineBase, "no turbine at destination");
			var b = (com.hbm.blockentity.machine.BlockEntityTurbineBase) dst;
			for (int i = 0; i < types.size(); i++) {
				helper.assertValueEqual(BuiltInRegistries.FLUID.getKey(b.tanks[i].getTankType()).toString(), types.get(i),
					id + " tank " + i + " steam");
				helper.assertValueEqual(b.tanks[i].getMaxFill(), sizes.get(i), id + " tank " + i + " size");
			}
			Schematicannon.LOGGER.info("[gametest] {} kept steam {} (tanks {} / {})", id, types.get(0), types, sizes);
		});
	}

	/**
	 * Builds the machine, lets {@code configure} set it up, saves it, has a creative-crate cannon rebuild it right
	 * next to the original, then checks core + cell count and runs {@code compare(original, copy)}.
	 */
	private static void rebuildAndCompare(GameTestHelper helper, BlockState coreState,
		java.util.function.Consumer<net.minecraft.world.level.block.entity.BlockEntity> configure,
		java.util.function.BiConsumer<net.minecraft.world.level.block.entity.BlockEntity, net.minecraft.world.level.block.entity.BlockEntity> compare) {
		var level = helper.getLevel();
		// Big machines spill far outside a test plot: give each its own area well away from the other tests, and
		// keep the original, the copy and the cannon loaded and ticking.
		var all = new ArrayList<String>(List.of(PART_BUILT));
		all.addAll(List.of(LEVER_TURBINES));
		int slot = all.indexOf(BuiltInRegistries.BLOCK.getKey(coreState.getBlock()).getPath());
		BlockPos srcCore = TestChunks.isolated(helper, slot, new BlockPos(2, 3, 2));
		var forced = TestChunks.force(level, srcCore.offset(-32, 0, -32), srcCore.offset(112, 0, 48));
		NtmCompat.assemble(level, srcCore, coreState);
		helper.assertTrue(level.getBlockState(srcCore).is(coreState.getBlock()), "could not build " + coreState);
		configure.accept(level.getBlockEntity(srcCore));

		BoundingBox box = BoundingBox.fromCorners(srcCore, srcCore);
		for (BlockPos p : BlockPos.betweenClosed(srcCore.offset(-24, -3, -24), srcCore.offset(24, 30, 24)))
			if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
				box = box.encapsulate(p);
		int cells = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
			if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
				cells++;
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_" + BuiltInRegistries.BLOCK.getKey(coreState.getBlock()).getPath(), true, level, min,
			new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
		helper.assertTrue(result != null, "schematic export failed");

		Schematicannon.LOGGER.info("[gametest] {}: core + {} cells in {}", coreState.getBlock(), cells, box);
		int shift = box.getXSpan() + 3;
		BlockPos dstCore = srcCore.east(shift);
		ItemStack schematic = SchematicItem.create(level, result.fileName(), "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), min.east(shift));
		BlockPos cannonPos = new BlockPos(srcCore.getX() + shift / 2, min.getY(), box.maxZ() + 4);
		level.setBlockAndUpdate(cannonPos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		level.setBlockAndUpdate(cannonPos.south(), ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
		SchematicannonBlockEntity cannon = (SchematicannonBlockEntity) level.getBlockEntity(cannonPos);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		SchematicannonGameTests.pressStart(helper, cannon);

		int expectedCells = cells;
		BlockPos copyMin = min.east(shift);
		BlockPos copyMax = copyMin.offset(box.getXSpan() - 1, box.getYSpan() - 1, box.getZSpan() - 1);
		helper.succeedWhen(() -> {
			helper.assertValueEqual(cannon.statusMsg, "finished", "cannon status");
			helper.assertTrue(level.getBlockState(dstCore).is(coreState.getBlock()), "core missing at destination");
			int copyCells = 0;
			for (BlockPos p : BlockPos.betweenClosed(copyMin, copyMax))
				if (NtmCompat.isMultiblockCell(level.getBlockState(p)))
					copyCells++;
			helper.assertValueEqual(copyCells, expectedCells, "cells of the rebuilt machine");
			compare.accept(level.getBlockEntity(srcCore), level.getBlockEntity(dstCore));
			TestChunks.release(level, forced);
		});
	}

	private NtmGameTests() {}
}
