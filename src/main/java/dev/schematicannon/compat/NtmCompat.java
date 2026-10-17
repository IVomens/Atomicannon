package dev.schematicannon.compat;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.config.ServerConfig;
import dev.schematicannon.schematic.ItemRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Entry point for HBM's Nuclear Tech Mod support. Every call is a no-op unless NTM is installed, and the
 * classes that touch NTM directly ({@link NtmMultiblocks}) are only loaded when it is.
 * <p>
 * NTM machines are one "core" block plus invisible "cell" blocks filling the rest of the footprint.
 * A schematic captures all of them; the cannon skips the cells, charges one machine item for the core,
 * then asks NTM to rebuild the cells exactly as NTM's own structure loader does.
 */
public final class NtmCompat {
	private static boolean loaded;

	public static void init() {
		loaded = ModList.get().isLoaded("hbm");
		if (loaded)
			Schematicannon.LOGGER.info("HBM's Nuclear Tech Mod detected: multiblock schematic support enabled");
	}

	/** Server tick: finishes pasted pipe anchors (fluid type and links) once NTM's pipe network has them. */
	public static void tick() {
		if (loaded)
			NtmMultiblocks.tick();
	}

	public static void serverStopping() {
		if (loaded)
			NtmMultiblocks.clearPending();
	}

	/** Usable during registration, before {@link #init()}. */
	public static boolean installed() {
		return ModList.get().isLoaded("hbm");
	}

	/** NTM power for the Advanced Atomicannon. */
	public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event,
		net.minecraft.world.level.block.entity.BlockEntityType<dev.schematicannon.cannon.SchematicannonBlockEntity> type) {
		if (installed())
			NtmEnergy.register(event, type);
	}

	/** NTM battery items (incl. the creative battery) for the Advanced Atomicannon's battery slot. */
	public static boolean isBattery(net.minecraft.world.item.ItemStack stack) {
		return installed() && NtmEnergy.isBattery(stack);
	}

	/** Settings key: the battery the cannon took for a battery socket (placed into it as is, charge included). */
	static final String BATTERY = "schematicannonBattery";

	/** Adds the battery the cannon took from its inventories to a battery socket's placement data. */
	public static net.minecraft.nbt.@Nullable CompoundTag withBattery(Level level, net.minecraft.nbt.@Nullable CompoundTag data,
		net.minecraft.world.item.ItemStack battery) {
		var encoded = net.minecraft.world.item.ItemStack.CODEC
			.encodeStart(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), battery).result();
		if (encoded.isEmpty())
			return data;
		var out = data == null ? new net.minecraft.nbt.CompoundTag() : data.copy();
		var settings = out.getCompoundOrEmpty(dev.schematicannon.schematic.SchematicPlacement.SETTINGS_KEY).copy();
		settings.put(BATTERY, encoded.get());
		out.put(dev.schematicannon.schematic.SchematicPlacement.SETTINGS_KEY, settings);
		return out;
	}

	/** Charge of an NTM battery item (0 for anything else). */
	public static long batteryCharge(net.minecraft.world.item.ItemStack stack) {
		return installed() ? NtmEnergy.batteryCharge(stack) : 0;
	}

	/** Takes up to {@code max} HE out of the battery in that slot; returns how much was taken. */
	public static long drainBattery(net.minecraft.world.Container container, int slot, long max) {
		return installed() ? NtmEnergy.drainBattery(container, slot, max) : 0;
	}

	private static boolean active() {
		return loaded && ServerConfig.SPEC.isLoaded() ? ServerConfig.ASSEMBLE_NTM_MULTIBLOCKS.get() : loaded;
	}

	public static boolean isMultiblockCell(BlockState state) {
		return active() && NtmMultiblocks.isCell(state);
	}

	public static boolean isMultiblockCore(BlockState state) {
		return active() && NtmMultiblocks.isCore(state);
	}

	/** @return a requirement for NTM blocks, or null to fall back to the default rules. */
	public static @Nullable ItemRequirement getRequirement(BlockState state) {
		if (!loaded)
			return null;
		if (active()) {
			if (NtmMultiblocks.isCell(state))
				return ItemRequirement.NONE;
			if (NtmMultiblocks.isCore(state))
				return NtmMultiblocks.coreRequirement(state);
		}
		var conveyorWand = NtmMultiblocks.conveyorItem(state);
		if (conveyorWand != null)
			return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, conveyorWand);
		return null;
	}

	/** The paint block of a painted NTM cable, duct or pneumatic tube (read from its saved data), or NONE. */
	public static ItemRequirement paintRequirement(BlockState state, net.minecraft.nbt.@Nullable CompoundTag data) {
		if (!loaded || data == null || !data.contains("camo"))
			return ItemRequirement.NONE;
		return NtmMultiblocks.paintRequirement(state, data);
	}

	/** Recipe blueprints in an NTM machine's saved inventory (they paste with the machine), or NONE. */
	public static ItemRequirement blueprintRequirement(BlockState state, net.minecraft.nbt.@Nullable CompoundTag data) {
		if (!loaded || data == null || !state.hasBlockEntity()
			|| (ServerConfig.SPEC.isLoaded() && !ServerConfig.COPY_NTM_MACHINE_SETTINGS.get()))
			return ItemRequirement.NONE;
		return NtmMultiblocks.blueprintRequirement(state, data);
	}

	/**
	 * Machine configuration (fluid identifier types and anything else NTM's settings tool copies) taken from the
	 * schematic's saved data. Contents are never included.
	 */
	public static net.minecraft.nbt.@Nullable CompoundTag extractSettings(Level level, BlockPos pos, BlockState state,
		net.minecraft.nbt.@Nullable CompoundTag raw) {
		if (!loaded || raw == null || !state.hasBlockEntity())
			return null;
		return NtmMultiblocks.extractSettings(level, pos, state, raw);
	}

	/**
	 * Some NTM blocks keep their configuration outside any block entity (fluid ducts store their fluid type in
	 * NTM's pipe network), so a structure save would lose it. This records it on those blocks' entries of a saved
	 * structure, under {@link dev.schematicannon.schematic.SchematicPlacement#SETTINGS_KEY}.
	 */
	public static void recordBlockSettings(Level level, BlockPos origin, net.minecraft.core.Vec3i size,
		net.minecraft.nbt.CompoundTag structure) {
		if (!loaded)
			return;
		var blocks = structure.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			var entry = blocks.getCompoundOrEmpty(i);
			var posTag = entry.getListOrEmpty("pos");
			BlockPos pos = origin.offset(posTag.getIntOr(0, 0), posTag.getIntOr(1, 0), posTag.getIntOr(2, 0));
			var settings = NtmMultiblocks.blockSettings(level, pos, level.getBlockState(pos), origin, size);
			if (settings == null)
				continue;
			// alongside any block entity data (paintable ducts have both a paint and a fluid type)
			var nbt = entry.getCompound("nbt").orElseGet(net.minecraft.nbt.CompoundTag::new);
			nbt.put(dev.schematicannon.schematic.SchematicPlacement.SETTINGS_KEY, settings);
			entry.put("nbt", nbt);
		}
	}

	/**
	 * An NTM block's saved data with its contents (items except filters, fluids, power, stored stock) removed, so its
	 * settings paste along; null for non-NTM blocks or when turned off in the config.
	 */
	public static net.minecraft.nbt.@Nullable CompoundTag configData(Level level, BlockPos pos, BlockState state,
		net.minecraft.nbt.@Nullable CompoundTag raw, java.util.function.Function<BlockPos, @Nullable BlockPos> savedToWorld) {
		if (!loaded || raw == null || !state.hasBlockEntity()
			|| (ServerConfig.SPEC.isLoaded() && !ServerConfig.COPY_NTM_MACHINE_SETTINGS.get()))
			return null;
		return NtmMultiblocks.configData(level, pos, state, raw, savedToWorld);
	}

	/**
	 * For a Schematic and Quill save made on a player's computer: the NTM block's settings as the server knows them
	 * (contents emptied, like for pasting), or null for non-NTM blocks and blocks the player may not build on.
	 */
	public static net.minecraft.nbt.@Nullable CompoundTag shareableSettings(net.minecraft.server.level.ServerLevel level,
		net.minecraft.world.level.block.entity.BlockEntity be, java.util.UUID player) {
		if (!loaded)
			return null;
		BlockState state = be.getBlockState();
		BlockPos pos = be.getBlockPos();
		if (!ClaimCompat.canModify(level, player, pos, state, state))
			return null;
		var raw = be.saveWithFullMetadata(level.registryAccess());
		var settings = configData(level, pos, state, raw, p -> p);
		if (settings != null)
			settings.putString("id", raw.getStringOr("id", ""));
		return settings;
	}

	/** Turns recorded schematic-local link positions into world positions for this placement. */
	public static void placeLinks(net.minecraft.nbt.CompoundTag settings,
		java.util.function.Function<BlockPos, BlockPos> localToWorld) {
		if (settings.contains(NtmMultiblocks.MIRROR)) {
			int[] p = settings.getIntArray(NtmMultiblocks.MIRROR).orElse(new int[3]);
			settings.remove(NtmMultiblocks.MIRROR);
			settings.putLong(NtmMultiblocks.MIRROR_WORLD, localToWorld.apply(new BlockPos(p[0], p[1], p[2])).asLong());
		}
		if (!settings.contains(NtmMultiblocks.LINKS))
			return;
		var list = settings.getListOrEmpty(NtmMultiblocks.LINKS);
		long[] world = new long[list.size()];
		for (int i = 0; i < list.size(); i++) {
			int[] p = list.getIntArray(i).orElse(new int[3]);
			world[i] = localToWorld.apply(new BlockPos(p[0], p[1], p[2])).asLong();
		}
		settings.remove(NtmMultiblocks.LINKS);
		settings.putLongArray(NtmMultiblocks.LINKS_WORLD, world);
	}

	public static void applySettings(Level level, BlockPos pos, net.minecraft.nbt.@Nullable CompoundTag settings) {
		if (loaded && settings != null)
			NtmMultiblocks.applySettings(level, pos, settings);
	}

	/** Places a multiblock core and has NTM fill in the rest of the machine. */
	public static void assemble(Level level, BlockPos pos, BlockState coreState) {
		NtmMultiblocks.assemble(level, pos, coreState);
	}

	private NtmCompat() {}
}
