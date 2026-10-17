package dev.schematicannon.gametest;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.schematic.SchematicExport;
import dev.schematicannon.schematic.SchematicItem;
import dev.schematicannon.schematic.SchematicPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Only loaded when WarForge is present. */
final class WarForgeGameTests {

	/**
	 * A faction claims the chunks around two print sites. A cannon started by an outsider must place nothing there
	 * (and report the claim); a cannon started by a faction member must build normally.
	 */
	@SuppressWarnings("unchecked")
	static void claimsBlockOutsiders(GameTestHelper helper) {
		var level = helper.getLevel();
		UUID member = UUID.randomUUID();
		UUID outsider = UUID.randomUUID();

		// source build in the test area
		BlockPos src = helper.absolutePos(new BlockPos(2, 70, 2));
		for (int i = 0; i < 3; i++)
			level.setBlockAndUpdate(src.east(i), Blocks.OAK_PLANKS.defaultBlockState());
		var result = SchematicExport.saveSchematic(SchematicPaths.UPLOADED_SCHEMATICS_DIR.resolve("gametest"),
			"gametest_claims", true, level, src, src.east(2));
		helper.assertTrue(result != null, "schematic export failed");

		BlockPos outsiderTarget = src.south(4);
		BlockPos memberTarget = src.south(8);

		// a real WarForge faction with one member, claiming every chunk around both print sites
		Faction faction = new Faction();
		faction.uuid = UUID.randomUUID();
		faction.name = "GametestFaction";
		faction.addPlayer(member);
		// every real faction has a citadel; put it in a corner chunk of the claim
		ChunkPos citadelChunk = ChunkPos.containing(src.south(6));
		faction.citadelPos = new com.flansmod.warforge.common.util.DimBlockPos(level.dimension(),
			citadelChunk.getMinBlockX() - 32, src.getY(), citadelChunk.getMinBlockZ() - 32);
		try {
			Field factions = WarForgeMod.FACTIONS.getClass().getDeclaredField("mFactions");
			factions.setAccessible(true);
			((Map<UUID, Faction>) factions.get(WarForgeMod.FACTIONS)).put(faction.uuid, faction);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		ChunkPos center = ChunkPos.containing(src.south(6));
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++)
				WarForgeMod.FACTIONS.getClaims().put(new DimChunkPos(level.dimension(), center.x() + dx, center.z() + dz),
					faction.uuid);
		helper.assertTrue(WarForgeMod.FACTIONS.IsPlayerInFaction(member, faction.uuid), "member not in faction");

		SchematicannonBlockEntity outsiderCannon = cannon(helper, src.offset(-3, 0, 3), result.fileName(), outsiderTarget, outsider);
		SchematicannonBlockEntity memberCannon = cannon(helper, src.offset(-3, 0, 9), result.fileName(), memberTarget, member);


		helper.succeedWhen(() -> {
			helper.assertValueEqual(memberCannon.statusMsg, "finished", "member cannon status");
			helper.assertValueEqual(outsiderCannon.statusMsg, "finished", "outsider cannon status");
			for (int i = 0; i < 3; i++) {
				helper.assertTrue(level.getBlockState(memberTarget.east(i)).is(Blocks.OAK_PLANKS),
					"member's block " + i + " was not placed in their own claim");
				helper.assertTrue(level.getBlockState(outsiderTarget.east(i)).isAir(),
					"outsider placed block " + i + " inside a foreign claim");
			}
			helper.assertValueEqual(outsiderCannon.claimSkipped, 3, "blocks the outsider cannon reported as claim protected");
			helper.assertValueEqual(memberCannon.claimSkipped, 0, "blocks the member cannon reported as claim protected");
			// clean up so other tests running nearby are not inside this claim
			WarForgeMod.FACTIONS.getClaims().values().removeIf(faction.uuid::equals);
			removeFaction(faction.uuid);
			Schematicannon.LOGGER.info("[gametest] claims: member built 3/3, outsider blocked 3/3");
		});
	}

	@SuppressWarnings("unchecked")
	private static void removeFaction(UUID id) {
		try {
			Field factions = WarForgeMod.FACTIONS.getClass().getDeclaredField("mFactions");
			factions.setAccessible(true);
			((Map<UUID, Faction>) factions.get(WarForgeMod.FACTIONS)).remove(id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static SchematicannonBlockEntity cannon(GameTestHelper helper, BlockPos pos, String file, BlockPos anchor, UUID owner) {
		var level = helper.getLevel();
		level.setBlockAndUpdate(pos, ModBlocks.SCHEMATICANNON.get().defaultBlockState());
		level.setBlockAndUpdate(pos.west(), ModBlocks.CREATIVE_CRATE.get().defaultBlockState());
		ItemStack schematic = SchematicItem.create(level, file, "gametest");
		schematic.set(ModComponents.SCHEMATIC_DEPLOYED.get(), true);
		schematic.set(ModComponents.SCHEMATIC_ANCHOR.get(), anchor);
		SchematicannonBlockEntity be = (SchematicannonBlockEntity) level.getBlockEntity(pos);
		be.inventory.setItem(SchematicannonBlockEntity.SLOT_SCHEMATIC, schematic);
		be.owner = owner; // as if this player pressed Start
		SchematicannonGameTests.pressStart(helper, be);
		return be;
	}

	private WarForgeGameTests() {}
}
