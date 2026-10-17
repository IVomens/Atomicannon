package dev.schematicannon.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.BooleanValue CREATIVE_PRINT_INCLUDES_AIR;
	public static final ModConfigSpec.IntValue INSTANT_PRINT_TICK_BUDGET_MS;
	public static final ModConfigSpec.IntValue MAX_INSTANT_SAVE_VOLUME;
	public static final ModConfigSpec.IntValue MAX_SCHEMATICS;
	public static final ModConfigSpec.IntValue MAX_TOTAL_SCHEMATIC_SIZE;
	public static final ModConfigSpec.IntValue MAX_SCHEMATIC_PACKET_SIZE;
	public static final ModConfigSpec.IntValue SCHEMATIC_IDLE_TIMEOUT;
	public static final ModConfigSpec.IntValue SCHEMATICANNON_DELAY;
	public static final ModConfigSpec.IntValue SCHEMATICANNON_SHOTS_PER_GUNPOWDER;
	public static final ModConfigSpec.IntValue CANNON_SCAN_BUDGET_MS;
	public static final ModConfigSpec.IntValue ADVANCED_DELAY;
	public static final ModConfigSpec.IntValue ADVANCED_ENERGY_PER_SHOT;
	public static final ModConfigSpec.IntValue ADVANCED_ENERGY_CAPACITY;
	public static final ModConfigSpec.BooleanValue ASSEMBLE_NTM_MULTIBLOCKS;
	public static final ModConfigSpec.BooleanValue COPY_NTM_MACHINE_SETTINGS;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("schematics");
		CREATIVE_PRINT_INCLUDES_AIR = b
			.comment("Whether placing a Schematic directly in Creative Mode should replace world blocks with Air")
			.define("creativePrintIncludesAir", false);
		INSTANT_PRINT_TICK_BUDGET_MS = b
			.comment("[in milliseconds per tick]", "Creative instant prints are spread over several ticks; this is how much",
				"time per tick all of them together may use (lighting and block updates add roughly as much again).",
				"Lower = smoother server, slower prints.")
			.defineInRange("instantPrintTickBudgetMs", 3, 1, 40);
		MAX_INSTANT_SAVE_VOLUME = b
			.comment("[in blocks]", "Largest area a Schematic and Quill may save straight on the server (it is copied in one tick).",
				"Bigger saves still work: they stay on the player's computer and are uploaded at a Schematic Table.")
			.defineInRange("maxInstantSaveVolume", 1_000_000, 1, 134_217_728);
		MAX_SCHEMATICS = b
			.comment("The amount of Schematics a player can upload until previous ones are overwritten.")
			.defineInRange("maxSchematics", 10, 1, Integer.MAX_VALUE);
		MAX_TOTAL_SCHEMATIC_SIZE = b
			.comment("[in KiloBytes]", "The maximum allowed file size of uploaded Schematics.")
			.defineInRange("maxTotalSchematicSize", 256, 16, Integer.MAX_VALUE);
		MAX_SCHEMATIC_PACKET_SIZE = b
			.comment("[in Bytes]", "The maximum packet size uploaded Schematics are split into.")
			.defineInRange("maxSchematicPacketSize", 1024, 256, 32767);
		SCHEMATIC_IDLE_TIMEOUT = b
			.comment("Amount of game ticks without new packets arriving until an active schematic upload process is discarded.")
			.defineInRange("schematicIdleTimeout", 600, 100, Integer.MAX_VALUE);

		b.push("schematicannon");
		SCHEMATICANNON_DELAY = b
			.comment("Amount of game ticks between shots of the cannon. Higher => Slower")
			.defineInRange("schematicannonDelay", 10, 1, Integer.MAX_VALUE);
		SCHEMATICANNON_SHOTS_PER_GUNPOWDER = b
			.comment("Amount of blocks a Schematicannon can print per Gunpowder item provided.")
			.defineInRange("schematicannonShotsPerGunpowder", 400, 1, Integer.MAX_VALUE);
		CANNON_SCAN_BUDGET_MS = b
			.comment("[in milliseconds per tick]", "Time all cannons together may spend per tick looking for the next block",
				"to place (skipping blocks that are already right). Each cannon always gets a little time.")
			.defineInRange("cannonScanBudgetMs", 8, 1, 40);
		b.pop();

		b.comment("The Advanced Atomicannon runs on HBM's Nuclear Tech Mod power (HE) instead of gunpowder.",
			"It only exists when NTM is installed.").push("advancedSchematicannon");
		ADVANCED_DELAY = b
			.comment("Amount of game ticks between shots of the Advanced Atomicannon. Higher => Slower")
			.defineInRange("advancedDelay", 3, 1, Integer.MAX_VALUE);
		ADVANCED_ENERGY_PER_SHOT = b
			.comment("HE used per block the Advanced Atomicannon places.")
			.defineInRange("advancedEnergyPerShot", 500, 0, Integer.MAX_VALUE);
		ADVANCED_ENERGY_CAPACITY = b
			.comment("How much HE the Advanced Atomicannon can store.")
			.defineInRange("advancedEnergyCapacity", 100_000, 1, Integer.MAX_VALUE);
		b.pop();

		b.push("compat");
		ASSEMBLE_NTM_MULTIBLOCKS = b
			.comment("When HBM's Nuclear Tech Mod is installed, place only the core block of NTM multiblock machines",
				"(charging one machine item) and let NTM build the rest of the structure around it.")
			.define("assembleNtmMultiblocks", true);
		COPY_NTM_MACHINE_SETTINGS = b
			.comment("Paste NTM blocks with their settings (recipes, filters, modes, radio channels, mass storage item type,",
				"pylon and pipe anchor links...). Their items, fluids, power and stored stock are always emptied first.")
			.define("copyNtmMachineSettings", true);
		b.pop();

		b.pop();
		SPEC = b.build();
	}

	private ServerConfig() {}
}
