package dev.schematicannon.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;

import com.hbm.blocks.multiblock.BlockMultiblockCell;
import com.hbm.blocks.multiblock.BlockMultiblockCore;
import com.hbm.blocks.multiblock.MultiblockSurface;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.schematic.ItemRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Only loaded when NTM is present. Mirrors NTM's own structure assembly.
 * <p>
 * NTM's API differs between releases (1.0.1 has 3-argument {@code fillSpace}/{@code assembled}, 1.0.3 adds
 * 4-argument versions with a clip box), so only the calls shared by every version are linked directly and
 * {@code assembled} is looked up at runtime. Any API mismatch is logged instead of crashing the world.
 */
final class NtmMultiblocks {
	private static boolean warned;
	/** Settings key: the steam a turbine's lever is set to. */
	private static final String TURBINE_STEAM = "schematicannonTurbineSteam";

	static boolean isCell(BlockState state) {
		try {
			return state.getBlock() instanceof BlockMultiblockCell || BlockMultiblockCore.isCell(state)
				|| MultiblockSurface.isFoldedCell(state);
		} catch (LinkageError e) {
			return state.getBlock() instanceof BlockMultiblockCell;
		}
	}

	static boolean isCore(BlockState state) {
		return state.getBlock() instanceof BlockMultiblockCore;
	}

	private static java.util.Map<Block, net.minecraft.world.item.Item> conveyorItems;

	/**
	 * NTM conveyors are laid with conveyor wands, not block items: one wand item is used up per conveyor block.
	 * Built from NTM's own wand definitions so it follows whatever conveyor types the installed NTM has.
	 * The regular wand also builds lifts and chutes.
	 */
	static net.minecraft.world.item.@org.jspecify.annotations.Nullable Item conveyorItem(BlockState state) {
		if (conveyorItems == null) {
			java.util.Map<Block, net.minecraft.world.item.Item> map = new java.util.HashMap<>();
			try {
				for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
					if (!(item instanceof com.hbm.items.tool.ItemConveyorWand wand))
						continue;
					Block block = com.hbm.items.tool.ItemConveyorWand.getConveyorBlock(wand.type);
					if (block != null)
						map.putIfAbsent(block, wand);
					if (com.hbm.items.tool.ItemConveyorWand.hasSnakesAndLadders(wand.type))
						for (String id : new String[] { "hbm:conveyor_lift", "hbm:conveyor_chute" })
							net.minecraft.core.registries.BuiltInRegistries.BLOCK
								.getOptional(net.minecraft.resources.Identifier.parse(id))
								.ifPresent(b -> map.putIfAbsent(b, wand));
				}
			} catch (LinkageError | RuntimeException e) {
				Schematicannon.LOGGER.warn("Could not read NTM conveyor wands; conveyors will need their block items", e);
			}
			conveyorItems = map;
		}
		return conveyorItems.get(state.getBlock());
	}

	/**
	 * Reads the machine settings NTM's settings tool would copy (fluid identifier types etc.) out of the
	 * schematic's saved block data, using a detached block entity that never enters the world.
	 */
	static net.minecraft.nbt.@org.jspecify.annotations.Nullable CompoundTag extractSettings(Level level, BlockPos pos,
		BlockState state, net.minecraft.nbt.CompoundTag raw) {
		try {
			var temp = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state, raw.copy(), level.registryAccess());
			if (!(temp instanceof com.hbm.interfaces.ICopiable copiable))
				return null;
			temp.setLevel(level);
			var settings = copiable.getSettings(level, pos);
			// turbines (Leviathan, industrial): the steam density chosen with the lever
			if (temp instanceof com.hbm.blockentity.machine.BlockEntityTurbineBase turbine && turbine.tanks.length > 0) {
				settings = settings == null ? new net.minecraft.nbt.CompoundTag() : settings.copy();
				settings.putString(TURBINE_STEAM, net.minecraft.core.registries.BuiltInRegistries.FLUID
					.getKey(turbine.tanks[0].getTankType()).toString());
			}
			return settings == null || settings.isEmpty() ? null : settings;
		} catch (LinkageError | RuntimeException e) {
			return null;
		}
	}

	/** Settings key: positions (schematic-local at save time, absolute longs once placed) a pylon/anchor links to. */
	static final String LINKS = "schematicannonLinks";
	static final String LINKS_WORLD = "schematicannonLinksWorld";
	/** Settings key: a solar mirror's target (schematic-local when saved, absolute long once placed). */
	static final String MIRROR = "schematicannonMirror";
	static final String MIRROR_WORLD = "schematicannonMirrorWorld";

	/**
	 * Settings that live outside block entity data, recorded when a schematic is saved: a fluid duct's fluid type (in
	 * NTM's settings-tool format) and the wire/pipe links of pylons and pipe anchors to blocks inside the schematic.
	 */
	static net.minecraft.nbt.@org.jspecify.annotations.Nullable CompoundTag blockSettings(Level level, BlockPos pos,
		BlockState state, BlockPos origin, net.minecraft.core.Vec3i size) {
		try {
			var settings = new net.minecraft.nbt.CompoundTag();
			if (state.getBlock() instanceof com.hbm.blocks.network.FluidDuctBlockBase) {
				var fluid = com.hbm.blocks.network.FluidPipeBlock.pipeFluidAt(level, pos);
				if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
					settings.putInt("fluidCount", 1);
					settings.putString("fluidID0", net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).toString());
				}
			}
			long[] links = null;
			var be = level.getBlockEntity(pos);
			// solar mirrors aim at a boiler by absolute position: keep it relative, the paste re-aims them
			if (be instanceof com.hbm.blockentity.machine.BlockEntitySolarMirror mirror
				&& (mirror.tX != 0 || mirror.tY != 0 || mirror.tZ != 0))
				settings.put(MIRROR, new net.minecraft.nbt.IntArrayTag(new int[] { mirror.tX - origin.getX(),
					mirror.tY - origin.getY(), mirror.tZ - origin.getZ() }));
			if (be instanceof com.hbm.blockentity.network.BlockEntityPylonBase pylon)
				links = pylon.connections;
			else if (be instanceof com.hbm.blockentity.network.BlockEntityPipeAnchor anchor)
				links = anchor.connections;
			if (links != null && links.length > 0) {
				var list = new net.minecraft.nbt.ListTag();
				for (long l : links) {
					BlockPos rel = BlockPos.of(l).subtract(origin);
					if (rel.getX() >= 0 && rel.getY() >= 0 && rel.getZ() >= 0 && rel.getX() < size.getX()
						&& rel.getY() < size.getY() && rel.getZ() < size.getZ())
						list.add(new net.minecraft.nbt.IntArrayTag(new int[] { rel.getX(), rel.getY(), rel.getZ() }));
				}
				if (!list.isEmpty())
					settings.put(LINKS, list);
			}
			return settings.isEmpty() ? null : settings;
		} catch (LinkageError | RuntimeException e) {
			return null;
		}
	}

	/** Recreates recorded links once both ends exist (the second end placed makes the link). */
	/**
	 * Pasted pipe anchors: they join NTM's pipe network a little after they are placed, so their fluid type and links
	 * are applied once they are in it, retried every tick for up to 10 seconds.
	 */
	private record PendingAnchor(net.minecraft.server.level.ServerLevel level, BlockPos pos,
		net.minecraft.world.level.material.@org.jspecify.annotations.Nullable Fluid fluid, long[] targets, int[] ticks) {}

	private static final java.util.List<PendingAnchor> PENDING_ANCHORS = new ArrayList<>();

	static void tick() {
		if (PENDING_ANCHORS.isEmpty())
			return;
		var it = PENDING_ANCHORS.iterator();
		while (it.hasNext()) {
			PendingAnchor a = it.next();
			boolean done;
			try {
				done = !a.level().isLoaded(a.pos()) ? false : finishAnchor(a);
			} catch (LinkageError | RuntimeException | ReflectiveOperationException e) {
				Schematicannon.LOGGER.warn("Could not link pasted pipe anchor at {}", a.pos(), e);
				done = true;
			}
			if (done || ++a.ticks()[0] > 200)
				it.remove();
		}
	}

	static void clearPending() {
		PENDING_ANCHORS.clear();
	}

	/** @return true when the anchor has its fluid and every linked anchor that exists is linked. */
	private static boolean finishAnchor(PendingAnchor a) throws ReflectiveOperationException {
		var level = a.level();
		if (!(level.getBlockEntity(a.pos()) instanceof com.hbm.blockentity.network.BlockEntityPipeAnchor))
			return true; // gone
		var data = com.hbm.api.fluidmk2.FluidPipeGraph.dataAt(level, a.pos().asLong());
		if (data == null)
			return false; // not in the pipe network yet
		if (a.fluid() != null && data.fluid() != a.fluid()) {
			var settings = new net.minecraft.nbt.CompoundTag();
			settings.putString("fluidID0", net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(a.fluid()).toString());
			retypeDuct(level, a.pos(), settings);
			return false; // check again next tick
		}
		boolean done = true;
		for (long t : a.targets()) {
			if (!(level.getBlockEntity(BlockPos.of(t)) instanceof com.hbm.blockentity.network.BlockEntityPipeAnchor))
				continue; // not pasted (yet): that anchor links to this one when it lands
			var other = com.hbm.api.fluidmk2.FluidPipeGraph.dataAt(level, t);
			if (other == null || other.fluid() != data.fluid()) {
				done = false; // the other end is not ready yet
				continue;
			}
			com.hbm.api.fluidmk2.FluidPipeGraph.get(level, data.fluid()).addRemoteLink(a.pos().asLong(), t);
		}
		return done;
	}

	private static void relink(net.minecraft.server.level.ServerLevel level, BlockPos pos, long[] targets) {
		var be = level.getBlockEntity(pos);
		for (long t : targets) {
			var other = level.getBlockEntity(BlockPos.of(t));
			if (be instanceof com.hbm.blockentity.network.BlockEntityPylonBase a
				&& other instanceof com.hbm.blockentity.network.BlockEntityPylonBase b) {
				com.hbm.api.energymk2.PowerGraph.get(level).addRemoteLink(pos.asLong(), t);
				a.refreshConnections();
				b.refreshConnections();
			} else if (be instanceof com.hbm.blockentity.network.BlockEntityPipeAnchor
				&& other instanceof com.hbm.blockentity.network.BlockEntityPipeAnchor) {
				var data = com.hbm.api.fluidmk2.FluidPipeGraph.dataAt(level, pos.asLong());
				var otherData = com.hbm.api.fluidmk2.FluidPipeGraph.dataAt(level, t);
				if (data != null && otherData != null && data.fluid() == otherData.fluid())
					com.hbm.api.fluidmk2.FluidPipeGraph.get(level, data.fluid()).addRemoteLink(pos.asLong(), t);
			}
		}
	}

	/**
	 * The block entity's own saved data with its contents taken out: every inventory slot except filter slots,
	 * every fluid tank, stored power and mass storage stock are emptied before it is saved again, so recipes, modes,
	 * filters, channels and the like carry over and nothing else does.
	 */
	static net.minecraft.nbt.@org.jspecify.annotations.Nullable CompoundTag configData(Level level, BlockPos pos,
		BlockState state, net.minecraft.nbt.CompoundTag raw,
		java.util.function.Function<BlockPos, @org.jspecify.annotations.Nullable BlockPos> savedToWorld) {
		try {
			var temp = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state, raw.copy(), level.registryAccess());
			if (temp == null)
				return null;
			var typeKey = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(temp.getType());
			if (typeKey == null || !"hbm".equals(typeKey.getNamespace()))
				return null;
			if (temp instanceof net.minecraft.world.Container container) {
				var keep = new java.util.HashSet<Integer>();
				// NTM gives filter slots as a range: {first, end (exclusive)}
				if (temp instanceof com.hbm.blockentity.IControlReceiverFilter filter) {
					int[] range = filter.getFilterSlots();
					if (range != null && range.length >= 2)
						for (int slot = range[0]; slot < range[1]; slot++)
							keep.add(slot);
				}
				// recipe blueprints and radar linkers stay (the cannon charges them, see keptItemRequirement); a radar
				// linker is re-aimed at the copied radar screen
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					var stack = container.getItem(slot);
					if (keep.contains(slot) || stack.isEmpty() || stack.getItem() instanceof com.hbm.items.machine.ItemBlueprints)
						continue;
					// a battery socket keeps its battery type, drained: the cannon charges a battery of that type
					if (temp instanceof com.hbm.blockentity.machine.storage.BlockEntityBatterySocket
						&& com.hbm.api.energymk2.IBatteryItem.isBattery(stack)) {
						container.setItem(slot, com.hbm.api.energymk2.IBatteryItem.emptyBattery(stack));
						continue;
					}
					if (stack.getItem() instanceof com.hbm.items.tool.ItemRadarLinker) {
						BlockPos linked = com.hbm.items.tool.ItemRadarLinker.getPosition(stack);
						BlockPos moved = linked == null ? null : savedToWorld.apply(linked);
						if (moved != null)
							stack.set(com.hbm.items.ModDataComponents.RADAR_LINKER_TARGET.get(), moved.asLong());
						continue;
					}
					container.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
				}
			}
			if (temp instanceof com.hbm.blockentity.machine.storage.BlockEntityMassStorage storage)
				storage.setStockpile(0);
			if (temp instanceof com.hbm.api.energymk2.IEnergyHandlerMK2 energy)
				energy.setPower(0);
			emptyTanksAndMetals(temp);
			remapPositions(temp, savedToWorld);
			return temp.saveCustomOnly(level.registryAccess());
		} catch (LinkageError | RuntimeException e) {
			Schematicannon.LOGGER.debug("Could not copy settings of {} at {}", state, pos, e);
			return null;
		}
	}

	/** Per block entity class: its instance fields that can hold contents (tanks, lists), looked up once. */
	private static final ClassValue<java.lang.reflect.Field[]> CONTENT_FIELDS = new ClassValue<>() {
		@Override
		protected java.lang.reflect.Field[] computeValue(Class<?> type) {
			var fields = new ArrayList<java.lang.reflect.Field>();
			for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
				for (var field : c.getDeclaredFields()) {
					if (java.lang.reflect.Modifier.isStatic(field.getModifiers()))
						continue;
					Class<?> t = field.getType();
					if (t != com.hbm.inventory.fluid.tank.FluidTankNTM.class && t != com.hbm.inventory.fluid.tank.FluidTankNTM[].class
						&& !java.util.List.class.isAssignableFrom(t))
						continue;
					try {
						field.setAccessible(true);
						fields.add(field);
					} catch (RuntimeException ignored) {
					}
				}
			return fields.toArray(java.lang.reflect.Field[]::new);
		}
	};

	/**
	 * Empties every NTM fluid tank (single or array fields) and molten metal (foundry blocks' amount, lists of NTM
	 * material stacks such as the crucible's contents).
	 */
	private static void emptyTanksAndMetals(Object be) {
		if (be instanceof com.hbm.blockentity.machine.BlockEntityFoundryBase foundry)
			foundry.amount = 0;
		for (var field : CONTENT_FIELDS.get(be.getClass())) {
			try {
				Object value = field.get(be);
				if (value instanceof com.hbm.inventory.fluid.tank.FluidTankNTM tank)
					tank.setFill(0);
				else if (value instanceof com.hbm.inventory.fluid.tank.FluidTankNTM[] tanks) {
					for (var t : tanks)
						if (t != null)
							t.setFill(0);
				} else if (value instanceof java.util.List<?> list && !list.isEmpty()
					&& list.get(0) instanceof com.hbm.inventory.material.Mats.MaterialStack)
					list.clear();
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
	}

	/**
	 * Per block entity class: int field triples named {@code <name>X, <name>Y, <name>Z} (an RBMK console's target, a
	 * radar screen's radar, a solar mirror's boiler, a crane console's center...), looked up once.
	 */
	private static final ClassValue<java.lang.reflect.Field[][]> POSITION_TRIPLES = new ClassValue<>() {
		@Override
		protected java.lang.reflect.Field[][] computeValue(Class<?> type) {
			var triples = new ArrayList<java.lang.reflect.Field[]>();
			for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
				for (var fx : c.getDeclaredFields()) {
					String name = fx.getName();
					if (fx.getType() != int.class || java.lang.reflect.Modifier.isStatic(fx.getModifiers())
						|| java.lang.reflect.Modifier.isFinal(fx.getModifiers()) || !name.endsWith("X"))
						continue;
					String prefix = name.substring(0, name.length() - 1);
					try {
						var fy = c.getDeclaredField(prefix + "Y");
						var fz = c.getDeclaredField(prefix + "Z");
						if (fy.getType() != int.class || fz.getType() != int.class)
							continue;
						fx.setAccessible(true);
						fy.setAccessible(true);
						fz.setAccessible(true);
						triples.add(new java.lang.reflect.Field[] { fx, fy, fz });
					} catch (ReflectiveOperationException | RuntimeException ignored) {
					}
				}
			}
			return triples.toArray(java.lang.reflect.Field[][]::new);
		}
	};

	/** Moves positions that point into (or next to) the saved build to the same spot in the paste. */
	private static void remapPositions(Object be,
		java.util.function.Function<BlockPos, @org.jspecify.annotations.Nullable BlockPos> savedToWorld) {
		for (var t : POSITION_TRIPLES.get(be.getClass())) {
			try {
				int x = t[0].getInt(be), y = t[1].getInt(be), z = t[2].getInt(be);
				if (x == 0 && y == 0 && z == 0)
					continue; // not linked
				BlockPos moved = savedToWorld.apply(new BlockPos(x, y, z));
				if (moved == null)
					continue;
				t[0].setInt(be, moved.getX());
				t[1].setInt(be, moved.getY());
				t[2].setInt(be, moved.getZ());
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
	}

	/** True if any string in the tag (item ids...) names an NTM blueprint; no full string conversion. */
	private static boolean mentionsBlueprint(net.minecraft.nbt.Tag tag) {
		if (tag instanceof net.minecraft.nbt.StringTag s)
			return s.value().startsWith("hbm:blueprint") || s.value().equals("hbm:radar_linker");
		if (tag instanceof net.minecraft.nbt.CompoundTag c) {
			for (String key : c.keySet())
				if (mentionsBlueprint(c.get(key)))
					return true;
		} else if (tag instanceof net.minecraft.nbt.ListTag l) {
			for (var e : l)
				if (mentionsBlueprint(e))
					return true;
		}
		return false;
	}

	private static @org.jspecify.annotations.Nullable Method retypeNode;

	/**
	 * Gives a placed duct its fluid the way NTM's own settings paste does (retype the node, then reconnect).
	 * {@code retypeNode} returns void in NTM 1.0.1 and boolean in 1.0.3, so it is called reflectively.
	 */
	private static void retypeDuct(net.minecraft.server.level.ServerLevel level, BlockPos pos,
		net.minecraft.nbt.CompoundTag settings) throws ReflectiveOperationException {
		var id = net.minecraft.resources.Identifier.tryParse(settings.getStringOr("fluidID0", ""));
		if (id == null)
			return;
		var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
		if (fluid == net.minecraft.world.level.material.Fluids.EMPTY)
			return;
		if (retypeNode == null)
			retypeNode = com.hbm.blocks.network.FluidPipeBlock.class.getMethod("retypeNode",
				net.minecraft.server.level.ServerLevel.class, long.class, net.minecraft.world.level.material.Fluid.class);
		retypeNode.invoke(null, level, pos.asLong(), fluid);
		com.hbm.blocks.network.FluidPipeBlock.refreshAround(level, pos);
	}

	/** Pastes copied settings into the freshly placed machine, like using NTM's settings tool on it. */
	static void applySettings(Level level, BlockPos pos, net.minecraft.nbt.CompoundTag settings) {
		try {
			// the battery the cannon took for this socket, exactly as it was (charge included)
			if (settings.contains(NtmCompat.BATTERY)
				&& level.getBlockEntity(pos) instanceof com.hbm.blockentity.machine.storage.BlockEntityBatterySocket socket) {
				net.minecraft.world.item.ItemStack.CODEC
					.parse(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), settings.get(NtmCompat.BATTERY))
					.result().ifPresent(battery -> {
						socket.setItem(com.hbm.blockentity.machine.storage.BlockEntityBatterySocket.SLOT_BATTERY, battery);
						socket.setChanged();
						level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
					});
				settings = settings.copy();
				settings.remove(NtmCompat.BATTERY);
			}
			if (level.getBlockEntity(pos) instanceof com.hbm.blockentity.network.BlockEntityPipeAnchor
				&& level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				var id = net.minecraft.resources.Identifier.tryParse(settings.getStringOr("fluidID0", ""));
				var fluid = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
				long[] targets = settings.getLongArray(LINKS_WORLD).orElse(new long[0]);
				PENDING_ANCHORS.add(new PendingAnchor(serverLevel, pos.immutable(), fluid, targets, new int[1]));
				return;
			}
			if (level.getBlockState(pos).getBlock() instanceof com.hbm.blocks.network.FluidDuctBlockBase
				&& level instanceof net.minecraft.server.level.ServerLevel serverLevel && settings.contains("fluidID0"))
				retypeDuct(serverLevel, pos, settings); // then paint, below, for paintable ducts
			if (settings.contains(LINKS_WORLD) && level instanceof net.minecraft.server.level.ServerLevel serverLevel)
				settings.getLongArray(LINKS_WORLD).ifPresent(targets -> relink(serverLevel, pos, targets));
			if (settings.contains(MIRROR_WORLD) && level.getBlockEntity(pos) instanceof com.hbm.blockentity.machine.BlockEntitySolarMirror mirror) {
				BlockPos target = BlockPos.of(settings.getLongOr(MIRROR_WORLD, 0));
				mirror.setTarget(target.getX(), target.getY(), target.getZ());
				mirror.setChanged();
				level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
			}
			if (level.getBlockEntity(pos) instanceof com.hbm.blockentity.machine.BlockEntityTurbineBase turbine
				&& settings.contains(TURBINE_STEAM) && !level.isClientSide()) {
				// pull the turbine's own lever until it runs on the saved steam, so both tanks (and the industrial
				// turbine's compressor size) switch exactly as they do in game
				var id = net.minecraft.resources.Identifier.tryParse(settings.getStringOr(TURBINE_STEAM, ""));
				var steam = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
				for (int i = 0; i < 8 && steam != null && turbine.tanks[0].getTankType() != steam; i++)
					turbine.onLeverPull();
				turbine.setChanged();
				level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
				return;
			}
			if (level.getBlockEntity(pos) instanceof com.hbm.interfaces.ICopiable copiable && !level.isClientSide()) {
				// No player: NTM's fluid paste doesn't need one, and creating a FakePlayer crashes with some
				// networking mods (e.g. Foxified Networking).
				copiable.pasteSettings(settings.copy(), 0, level, null, pos);
				level.getBlockEntity(pos).setChanged();
				level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
			}
		} catch (LinkageError | RuntimeException | ReflectiveOperationException e) {
			Schematicannon.LOGGER.warn("Could not apply NTM machine settings at {}", pos, e);
		}
	}

	static ItemRequirement paintRequirement(BlockState state, net.minecraft.nbt.CompoundTag data) {
		try {
			if (!(state.getBlock() instanceof com.hbm.blocks.network.PaintableCamoBlock))
				return ItemRequirement.NONE;
			var camo = BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, data.get("camo")).result().orElse(null);
			if (camo == null || camo.isAir() || camo.getBlock().asItem() == Items.AIR)
				return ItemRequirement.NONE;
			return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, camo.getBlock().asItem());
		} catch (LinkageError | RuntimeException e) {
			return ItemRequirement.NONE;
		}
	}

	static ItemRequirement blueprintRequirement(BlockState state, net.minecraft.nbt.CompoundTag data) {
		var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
		// cheap check first: only machines actually holding a blueprint item are decoded
		if (server == null || !data.contains("id")
			|| !(mentionsBlueprint(data) || data.getStringOr("id", "").contains("socket")))
			return ItemRequirement.NONE;
		try {
			var temp = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(BlockPos.ZERO, state, data.copy(),
				server.registryAccess());
			if (!(temp instanceof net.minecraft.world.Container container))
				return ItemRequirement.NONE;
			var required = new java.util.ArrayList<ItemRequirement.StackRequirement>();
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				var stack = container.getItem(slot);
				if (stack.getItem() instanceof com.hbm.items.machine.ItemBlueprints) // that exact recipe pool
					required.add(new ItemRequirement.StackRequirement(stack.copy(), ItemRequirement.ItemUseType.CONSUME, true));
				else if (stack.getItem() instanceof com.hbm.items.tool.ItemRadarLinker) // any linker; the copy is re-aimed
					required.add(new ItemRequirement.StackRequirement(new net.minecraft.world.item.ItemStack(stack.getItem()),
						ItemRequirement.ItemUseType.CONSUME, false));
				else if (temp instanceof com.hbm.blockentity.machine.storage.BlockEntityBatterySocket
					&& com.hbm.api.energymk2.IBatteryItem.isBattery(stack)) // that battery type, any charge
					required.add(new ItemRequirement.StackRequirement(new net.minecraft.world.item.ItemStack(stack.getItem()),
						ItemRequirement.ItemUseType.CONSUME, false));
			}
			return required.isEmpty() ? ItemRequirement.NONE : new ItemRequirement(required);
		} catch (LinkageError | RuntimeException e) {
			return ItemRequirement.NONE;
		}
	}

	/** Per core block: the parts it breaks into, or an empty list when it drops itself. */
	private static final java.util.Map<Block, java.util.List<net.minecraft.world.item.ItemStack>> PARTS =
		new java.util.concurrent.ConcurrentHashMap<>();

	/** Per core block: what NTM's construction catalog says it is built from (structure core + parts), if listed. */
	private static final java.util.Map<Block, java.util.List<ItemRequirement.StackRequirement>> CONSTRUCTION =
		new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Machines NTM assembles from a structure core and parts (fusion reactor vessel, ICF, Watz...) cost exactly that:
	 * the core plus every part, any listed alternative accepted, as NTM's own construction catalog lists them.
	 */
	private static java.util.List<ItemRequirement.StackRequirement> constructionOf(BlockState state) {
		var cached = CONSTRUCTION.get(state.getBlock());
		if (cached != null)
			return cached;
		java.util.List<? extends Object> catalog;
		try {
			catalog = com.hbm.handler.ConstructionCatalog.entries();
		} catch (LinkageError | RuntimeException e) {
			return java.util.List.of();
		}
		if (catalog.isEmpty())
			return java.util.List.of(); // not filled yet: don't remember "not listed"
		return CONSTRUCTION.computeIfAbsent(state.getBlock(), block -> {
			try {
				for (var entry : com.hbm.handler.ConstructionCatalog.entries()) {
					if (!entry.assembled().is(block.asItem()))
						continue;
					var parts = new ArrayList<ItemRequirement.StackRequirement>();
					if (!entry.seed().isEmpty())
						parts.add(new ItemRequirement.StackRequirement(entry.seed().copy(), ItemRequirement.ItemUseType.CONSUME));
					for (var options : entry.pile()) {
						var first = options.stream().filter(s -> !s.isEmpty()).findFirst();
						// tools used to assemble (blowtorch, bolt gun) are listed too, but are not used up
						if (first.isEmpty() || first.get().getMaxStackSize() == 1)
							continue;
						var others = options.stream().filter(s -> !s.isEmpty() && !s.is(first.get().getItem()))
							.map(net.minecraft.world.item.ItemStack::copy).toList();
						parts.add(new ItemRequirement.StackRequirement(first.get().copy(), ItemRequirement.ItemUseType.CONSUME,
							false, others));
					}
					return java.util.List.copyOf(parts);
				}
			} catch (LinkageError | RuntimeException e) {
				Schematicannon.LOGGER.debug("NTM construction catalog unavailable", e);
			}
			return java.util.List.of();
		});
	}

	static ItemRequirement coreRequirement(BlockState state) {
		// Built from a structure core + parts (fusion reactor vessel, ICF, Watz...): charge those, as NTM lists them
		var construction = constructionOf(state);
		if (!construction.isEmpty())
			return new ItemRequirement(construction.stream().map(r -> new ItemRequirement.StackRequirement(r.stack.copy(),
				r.usage, r.exact, r.alternatives)).toList());
		// Otherwise structures that break back into parts (and whose core has no survival recipe) cost those parts:
		// exactly what breaking the finished machine gives back.
		var parts = partsOf(state);
		if (!parts.isEmpty())
			return new ItemRequirement(parts.stream()
				.map(s -> new ItemRequirement.StackRequirement(s.copy(), ItemRequirement.ItemUseType.CONSUME)).toList());
		var item = state.getBlock().asItem();
		if (item == Items.AIR)
			return ItemRequirement.INVALID;
		return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, item);
	}

	private static java.util.List<net.minecraft.world.item.ItemStack> partsOf(BlockState state) {
		var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
		if (server == null)
			return java.util.List.of();
		return PARTS.computeIfAbsent(state.getBlock(), block -> {
			try {
				var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(server.overworld())
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
						net.minecraft.world.phys.Vec3.ZERO)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,
						net.minecraft.world.item.ItemStack.EMPTY);
				var drops = state.getDrops(params).stream().filter(s -> !s.isEmpty()).toList();
				boolean dropsItself = drops.stream().anyMatch(s -> s.is(block.asItem()));
				return drops.isEmpty() || dropsItself ? java.util.List.of() : drops;
			} catch (RuntimeException e) {
				return java.util.List.of();
			}
		});
	}

	static void assemble(Level level, BlockPos pos, BlockState coreState) {
		BlockMultiblockCore core = (BlockMultiblockCore) coreState.getBlock();
		level.setBlock(pos, coreState, Block.UPDATE_ALL);
		try {
			Direction facing = coreState.getValue(BlockMultiblockCore.FACING);
			core.fillSpace(level, pos, facing); // present in every NTM 26.2 release
			callAssembled(core, level, pos, coreState);
		} catch (LinkageError | Exception e) {
			if (!warned) {
				warned = true;
				Schematicannon.LOGGER.error("Could not assemble NTM multiblock {} at {} - this NTM version's API is not "
					+ "supported; only the core block was placed", coreState, pos, e);
			}
		}
	}

	private static void callAssembled(BlockMultiblockCore core, LevelAccessor level, BlockPos pos, BlockState state)
		throws ReflectiveOperationException {
		for (Method m : core.getClass().getMethods()) {
			if (!m.getName().equals("assembled"))
				continue;
			Class<?>[] p = m.getParameterTypes();
			if (p.length == 4 && p[0].isAssignableFrom(level.getClass())) {
				m.invoke(core, level, pos, state, null); // 1.0.3+: (level, pos, state, clip)
				return;
			}
			if (p.length == 3 && p[0].isAssignableFrom(level.getClass())) {
				m.invoke(core, level, pos, state); // 1.0.1: (level, pos, state)
				return;
			}
		}
	}

	private NtmMultiblocks() {}
}
