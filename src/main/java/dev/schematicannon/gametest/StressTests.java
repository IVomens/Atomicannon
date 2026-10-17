package dev.schematicannon.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.schematic.InstantPrints;
import dev.schematicannon.schematic.SchematicExport;
import dev.schematicannon.schematic.SchematicItem;
import dev.schematicannon.schematic.SchematicPaths;
import dev.schematicannon.schematic.SchematicPrinter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server load test (only with -Pstress): measures real server tick times while
 * 1) 16 cannons start on and scan an already-built 48x12x48 schematic (the heaviest phase: every position checked),
 * 2) 8 cannons fire flat out onto empty ground,
 * 3) a creative instant print places the whole schematic.
 */
final class StressTests {
	private static final int W = 48, H = 12;

	private static final class Window {
		final String name;
		long max, total;
		int ticks;

		Window(String name) {
			this.name = name;
		}

		void add(long nanos) {
			max = Math.max(max, nanos);
			total += nanos;
			ticks++;
		}

		String report() {
			return String.format("%s: %d ticks, avg %.2f ms, max %.2f ms", name, ticks, ticks == 0 ? 0 : total / 1e6 / ticks,
				max / 1e6);
		}
	}

	static void serverLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		BlockPos base = TestChunks.isolated(helper, 20, new BlockPos(2, 1, 2));
		var forced = TestChunks.force(level, base.offset(-32, 0, -32), base.offset(8 * 64 + 64, 0, 64));

		Window[] current = { null };
		List<Window> windows = new ArrayList<>();
		helper.onEachTick(() -> {
			if (current[0] != null)
				current[0].add(server.getTickTimesNanos()[Math.floorMod(server.getTickCount() - 1, 100)]);
		});
		java.util.function.Consumer<String> phase = name -> {
			current[0] = name == null ? null : new Window(name);
			if (current[0] != null)
				windows.add(current[0]);
		};

		BlockState[] palette = { Blocks.STONE_BRICKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
			Blocks.GLASS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.AIR.defaultBlockState() };
		List<SchematicannonBlockEntity> scanners = new ArrayList<>();
		List<SchematicannonBlockEntity> firers = new ArrayList<>();
		UUID printer = UUID.randomUUID();
		ItemStack[] schematic = { ItemStack.EMPTY };

		helper.startSequence()
			.thenExecute(() -> {
				// a 48x12x48 build, ~80% solid
				for (int x = 0; x < W; x++)
					for (int y = 0; y < H; y++)
						for (int z = 0; z < W; z++)
							level.setBlock(base.offset(x, y, z), palette[(x * 7 + y * 13 + z * 3) % palette.length], 2);
				var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
					"gametest_stress", true, level, base, base.offset(W - 1, H - 1, W - 1));
				helper.assertTrue(result != null, "export failed");
				schematic[0] = SchematicItem.create(level, result.fileName(), "gametest");
				schematic[0].set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
			})
			.thenIdle(40)
			.thenExecute(() -> {
				// phase 1: 16 cannons over the build itself, so every one of ~27k positions is checked and skipped
				phase.accept("16 cannons scanning a built 27k-position schematic");
				for (int i = 0; i < 16; i++) {
					BlockPos pos = base.offset(i * 3, 0, -8);
					scanners.add(cannon(level, pos, base));
				}
				scanners.forEach(c -> SchematicannonGameTests.pressStart(helper, c));
			})
			.thenWaitUntil(() -> {
				for (var c : scanners)
					helper.assertValueEqual(c.statusMsg, "finished", "scanner");
			})
			.thenExecute(() -> {
				// phase 2: 8 cannons firing onto empty ground. Placing them (test setup, which also generates the
				// chunks out there) is not measured; their schematic loading and firing is.
				phase.accept(null);
				for (int i = 0; i < 8; i++) {
					BlockPos area = base.offset(64 + i * 64, 0, 0);
					firers.add(cannon(level, area.offset(-4, 0, -4), area));
				}
			})
			.thenIdle(1)
			.thenExecute(() -> {
				phase.accept("8 cannons firing");
				firers.forEach(c -> SchematicannonGameTests.pressStart(helper, c));
			})
			.thenIdle(400)
			.thenExecute(() -> {
				firers.forEach(c -> c.state = SchematicannonBlockEntity.State.STOPPED);
				// phase 3: creative instant print of the whole schematic onto empty ground
				phase.accept("instant print of the 48x12x48 schematic");
				ItemStack copy = schematic[0].copy();
				copy.set(ModComponents.SCHEMATIC_ANCHOR.get(), base.offset(0, 0, 60));
				SchematicPrinter p = new SchematicPrinter();
				p.loadSchematic(copy, level);
				InstantPrints.start(printer, level, p, false);
			})
			.thenWaitUntil(() -> helper.assertTrue(!InstantPrints.isRunning(printer), "instant print still running"))
			.thenExecute(() -> {
				phase.accept(null);
				helper.assertTrue(level.getBlockState(base.offset(0, 0, 60)).is(palette[0].getBlock()), "instant print incomplete");
				for (Window w : windows)
					Schematicannon.LOGGER.info("[stress] {}", w.report());
				for (Window w : windows)
					helper.assertTrue(w.max < 250_000_000L, "server hitch over 250 ms during: " + w.report());
			})
			.thenExecute(() -> TestChunks.release(level, forced))
			.thenSucceed();
	}

	private static SchematicannonBlockEntity cannon(ServerLevel level, BlockPos pos, BlockPos anchor) {
		var block = ModBlocks.ADVANCED_SCHEMATICANNON != null ? ModBlocks.ADVANCED_SCHEMATICANNON.get() : ModBlocks.SCHEMATICANNON.get();
		level.setBlockAndUpdate(pos, block.defaultBlockState());
		level.setBlockAndUpdate(pos.west(), ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
		var cannon = (SchematicannonBlockEntity) level.getBlockEntity(pos);
		ItemStack item = SchematicItem.create(level, "gametest_stress.nbt", "gametest");
		item.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		item.set(ModComponents.SCHEMATIC_ANCHOR.get(), anchor);
		cannon.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, item);
		return cannon;
	}

	private StressTests() {}
}
