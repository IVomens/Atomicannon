package dev.schematicannon;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.schematicannon.compat.NtmCompat;
import dev.schematicannon.config.ServerConfig;
import dev.schematicannon.network.ModPackets;
import dev.schematicannon.registry.ModBlockEntities;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.registry.ModCreativeTab;
import dev.schematicannon.registry.ModItems;
import dev.schematicannon.registry.ModMenus;
import dev.schematicannon.schematic.ServerSchematicLoader;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Schematicannon.MOD_ID)
public class Schematicannon {
	public static final String MOD_ID = "schematicannon";
	public static final Logger LOGGER = LogUtils.getLogger();

	public static final ServerSchematicLoader SCHEMATIC_RECEIVER = new ServerSchematicLoader();

	public Schematicannon(IEventBus modBus, ModContainer container) {
		ModComponents.REGISTER.register(modBus);
		ModBlocks.REGISTER.register(modBus);
		ModItems.REGISTER.register(modBus);
		ModBlockEntities.REGISTER.register(modBus);
		ModMenus.REGISTER.register(modBus);
		ModCreativeTab.REGISTER.register(modBus);

		modBus.addListener(ModPackets::register);
		modBus.addListener(ModBlockEntities::registerCapabilities);

		container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			SCHEMATIC_RECEIVER.tick();
			dev.schematicannon.schematic.InstantPrints.tick(event.getServer());
			NtmCompat.tick();
		});
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
			SCHEMATIC_RECEIVER.shutdown();
			dev.schematicannon.schematic.InstantPrints.clear();
			NtmCompat.serverStopping();
		});

		NtmCompat.init();
		dev.schematicannon.compat.ClaimCompat.init();

		if (!net.neoforged.fml.loading.FMLEnvironment.isProduction())
			dev.schematicannon.gametest.SchematicannonGameTests.register(modBus);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
