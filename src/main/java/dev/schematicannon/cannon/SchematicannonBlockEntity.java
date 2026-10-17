package dev.schematicannon.cannon;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.config.ServerConfig;
import dev.schematicannon.registry.ModBlockEntities;
import dev.schematicannon.registry.ModBlocks;
import dev.schematicannon.registry.ModItems;
import dev.schematicannon.registry.ModComponents;
import dev.schematicannon.schematic.ItemRequirement;
import dev.schematicannon.schematic.SchematicPrinter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Port of Create's SchematicannonBlockEntity. */
public class SchematicannonBlockEntity extends BlockEntity implements MenuProvider {
	public static final int NEIGHBOUR_CHECKING = 100;
	public static final int MAX_ANCHOR_DISTANCE = 256;

	public static final int SLOT_SCHEMATIC = 0;
	public static final int SLOT_SCHEMATIC_OUT = 1;
	public static final int SLOT_BOOK_IN = 2;
	public static final int SLOT_BOOK_OUT = 3;
	public static final int SLOT_GUNPOWDER = 4;

	// Inventory
	public final SimpleContainer inventory = new SimpleContainer(5) {
		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return switch (slot) {
				case SLOT_SCHEMATIC -> stack.is(ModItems.SCHEMATIC.get());
				case SLOT_BOOK_IN -> stack.is(Items.BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(ModItems.CLIPBOARD.get());
				case SLOT_GUNPOWDER -> isElectric() ? dev.schematicannon.compat.NtmCompat.isBattery(stack)
					: stack.is(Items.GUNPOWDER);
				default -> false;
			};
		}

		@Override
		public boolean canTakeItem(net.minecraft.world.Container into, int slot, ItemStack stack) {
			return slot == SLOT_SCHEMATIC_OUT || slot == SLOT_BOOK_OUT;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			SchematicannonBlockEntity.this.setChanged();
		}
	};

	/** The battery taken for the battery socket being placed now (see grabItemsFromAttachedInventories). */
	private @Nullable ItemStack takenBattery;

	/** Scan time all cannons on the server thread may still use this tick (see cannonScanBudgetMs). */
	private static long scanBudgetTick = -1, scanBudgetLeft;
	private static long initTick = -1;
	private static int initsThisTick;

	private boolean claimInitSlot() {
		long tick = level.getServer() == null ? 0 : level.getServer().getTickCount();
		if (tick != initTick) {
			initTick = tick;
			initsThisTick = 0;
		}
		return initsThisTick++ < 2;
	}

	public boolean sendUpdate;
	public boolean dontUpdateChecklist;
	public int neighbourCheckCooldown;
	private int lazyTickCounter;

	// Printer
	public final SchematicPrinter printer = new SchematicPrinter();
	public ItemStack missingItem = ItemStack.EMPTY;
	public boolean positionNotLoaded;
	public boolean hasCreativeCrate;
	private int printerCooldown;
	private int skipsLeft;
	private boolean blockSkipped;

	public @Nullable BlockPos previousTarget;
	public final List<ResourceHandler<ItemResource>> attachedInventories = new ArrayList<>();
	public final List<LaunchedItem> flyingBlocks = new LinkedList<>();
	public final MaterialChecklist checklist = new MaterialChecklist();

	// Gui information
	public int remainingFuel;
	public float bookPrintingProgress;
	public float schematicProgress;
	public String statusMsg = "idle";
	public State state = State.STOPPED;
	public int blocksPlaced;
	public int blocksToPlace;

	// Settings
	public int replaceMode = 2;
	public boolean skipMissing;
	public boolean replaceBlockEntities;

	// Render
	public float defaultYaw;

	/** The player who last pressed Start; their land-claim permissions apply to everything this cannon prints. */
	public java.util.@Nullable UUID owner;
	/** Blocks of the current print skipped because the owner may not build there (land claims). */
	public int claimSkipped;
	/** Client-only, smoothed turret aim used by the renderer. */
	public float clientYaw = Float.NaN;
	public float clientPitch = 25;

	// Advanced (electric) cannon: HBM NTM power instead of gunpowder
	public long power;
	/** NTM's energy handler for this cannon, created by the NTM compat code (typed Object so NTM stays optional). */
	public @Nullable Object energyHandler;
	private int lastPowerSync = -1;

	public SchematicannonBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SCHEMATICANNON.get(), pos, state);
	}

	/** The Advanced Atomicannon runs on NTM power (HE) instead of gunpowder. */
	public boolean isElectric() {
		return getBlockState().getBlock() instanceof SchematicannonBlock block && block.isElectric();
	}

	public long getMaxPower() {
		return ServerConfig.SPEC.isLoaded() ? ServerConfig.ADVANCED_ENERGY_CAPACITY.get() : 100_000;
	}

	public long getEnergyPerShot() {
		return ServerConfig.SPEC.isLoaded() ? ServerConfig.ADVANCED_ENERGY_PER_SHOT.get() : 500;
	}

	/** Called by NTM's power network. */
	public void setPower(long power) {
		long clamped = Math.max(0, Math.min(power, getMaxPower()));
		if (clamped != this.power) {
			this.power = clamped;
			setChanged();
		}
	}

	private String outOfFuelStatus() {
		return isElectric() ? "noPower" : "noGunpowder";
	}

	public void findInventories() {
		hasCreativeCrate = false;
		attachedInventories.clear();
		for (Direction facing : Direction.values()) {
			BlockPos neighbour = worldPosition.relative(facing);
			if (!level.isLoaded(neighbour))
				continue;
			if (level.getBlockState(neighbour).is(ModBlocks.CREATIVE_CRATE.get()))
				hasCreativeCrate = true;
			ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, neighbour, facing.getOpposite());
			if (handler != null)
				attachedInventories.add(handler);
		}
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		boolean clientPacket = input.getBooleanOr("ClientPacket", false);
		if (!clientPacket) {
			inventory.clearContent();
			net.minecraft.world.ContainerHelper.loadAllItems(input.childOrEmpty("Inventory"), inventory.getItems());
		}
		input.read("Data", CompoundTag.CODEC).ifPresent(tag -> readData(tag, input.lookup(), clientPacket));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		net.minecraft.world.ContainerHelper.saveAllItems(output.child("Inventory"), inventory.getItems(), true);
		output.store("Data", CompoundTag.CODEC, writeData(level == null ? null : level.registryAccess(), false));
	}

	private void readData(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		statusMsg = compound.getStringOr("Status", "idle");
		schematicProgress = compound.getFloatOr("Progress", 0);
		bookPrintingProgress = compound.getFloatOr("PaperProgress", 0);
		remainingFuel = compound.getIntOr("RemainingFuel", 0);
		power = compound.getLongOr("Power", 0);
		try {
			state = State.valueOf(compound.getStringOr("State", State.STOPPED.name()));
		} catch (IllegalArgumentException e) {
			state = State.STOPPED;
		}
		blocksPlaced = compound.getIntOr("AmountPlaced", 0);
		blocksToPlace = compound.getIntOr("AmountToPlace", 0);
		claimSkipped = compound.getIntOr("ClaimSkipped", 0);

		missingItem = compound.read("MissingItem", ItemStack.CODEC, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE))
			.orElse(ItemStack.EMPTY);

		replaceMode = compound.getIntOr("ReplaceMode", 2);
		skipMissing = compound.getBooleanOr("SkipMissing", false);
		replaceBlockEntities = compound.getBooleanOr("ReplaceBlockEntities", false);

		compound.getCompound("Printer").ifPresent(p -> printer.fromTag(p, clientPacket));
		readFlyingBlocks(compound.getListOrEmpty("FlyingBlocks"), registries, clientPacket);
		defaultYaw = compound.getFloatOr("DefaultYaw", 0);
		owner = compound.getString("Owner").map(s -> {
			try {
				return java.util.UUID.fromString(s);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}).orElse(null);
	}

	private void readFlyingBlocks(ListTag tagBlocks, HolderLookup.Provider registries, boolean clientPacket) {
		var blocks = registries.lookupOrThrow(Registries.BLOCK);
		if (!clientPacket) {
			flyingBlocks.clear();
			for (int i = 0; i < tagBlocks.size(); i++) {
				LaunchedItem launched = LaunchedItem.fromNBT(tagBlocks.getCompoundOrEmpty(i), registries, blocks, false);
				if (launched != null)
					flyingBlocks.add(launched);
			}
			return;
		}

		// Client: keep existing projectiles animating smoothly, add the new ones from the server.
		if (tagBlocks.isEmpty()) {
			flyingBlocks.clear();
			return;
		}
		List<LaunchedItem> incoming = new ArrayList<>();
		for (int i = 0; i < tagBlocks.size(); i++) {
			LaunchedItem launched = LaunchedItem.fromNBT(tagBlocks.getCompoundOrEmpty(i), registries, blocks, true);
			if (launched != null)
				incoming.add(launched);
		}
		BlockPos first = incoming.isEmpty() ? null : incoming.get(0).target;
		while (!flyingBlocks.isEmpty() && first != null && !flyingBlocks.get(0).target.equals(first))
			flyingBlocks.remove(0);
		for (int i = flyingBlocks.size(); i < incoming.size(); i++)
			flyingBlocks.add(incoming.get(i));
	}

	private CompoundTag writeData(HolderLookup.@Nullable Provider registries, boolean clientPacket) {
		CompoundTag compound = new CompoundTag();
		compound.putFloat("Progress", schematicProgress);
		compound.putFloat("PaperProgress", bookPrintingProgress);
		compound.putInt("RemainingFuel", remainingFuel);
		compound.putLong("Power", power);
		compound.putString("Status", statusMsg);
		compound.putString("State", state.name());
		compound.putInt("AmountPlaced", blocksPlaced);
		compound.putInt("AmountToPlace", blocksToPlace);
		compound.putInt("ClaimSkipped", claimSkipped);

		if (!missingItem.isEmpty() && registries != null)
			compound.store("MissingItem", ItemStack.CODEC, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
				missingItem);

		compound.putInt("ReplaceMode", replaceMode);
		compound.putBoolean("SkipMissing", skipMissing);
		compound.putBoolean("ReplaceBlockEntities", replaceBlockEntities);

		CompoundTag printerData = new CompoundTag();
		printer.write(printerData);
		compound.put("Printer", printerData);

		ListTag tagFlyingBlocks = new ListTag();
		if (registries != null)
			for (LaunchedItem b : flyingBlocks)
				tagFlyingBlocks.add(clientPacket ? b.serializeForClient(registries) : b.serialize(registries));
		compound.put("FlyingBlocks", tagFlyingBlocks);
		compound.putFloat("DefaultYaw", defaultYaw);
		if (owner != null)
			compound.putString("Owner", owner.toString());
		return compound;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.putBoolean("ClientPacket", true);
		output.store("Data", CompoundTag.CODEC, writeData(registries, true));
		return output.buildResult();
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null)
			Containers.dropContents(level, pos, inventory);
	}

	// ---------------------------------------------------------------- ticking

	public static void tick(Level level, BlockPos pos, BlockState state, SchematicannonBlockEntity be) {
		be.tick();
	}

	public void tick() {
		if (state != State.STOPPED && neighbourCheckCooldown-- <= 0) {
			neighbourCheckCooldown = NEIGHBOUR_CHECKING;
			findInventories();
		}
		if (lazyTickCounter-- <= 0) {
			lazyTickCounter = 30;
			findInventories();
		}

		previousTarget = printer.getCurrentTarget();
		tickFlyingBlocks();

		if (level.isClientSide())
			return;

		try {
			serverTick();
		} catch (RuntimeException | LinkageError e) {
			// A broken schematic or another mod's block must never crash the server: stop just this cannon.
			dev.schematicannon.Schematicannon.LOGGER.error("Schematicannon at {} stopped after an error", worldPosition, e);
			state = State.STOPPED;
			statusMsg = "schematicErrored";
			try {
				resetPrinter();
			} catch (RuntimeException ignored) {
			}
			sendUpdate = true;
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 6);
		}
	}

	private void serverTick() {
		// Advanced cannon: drain an NTM battery in the battery slot, like NTM machines do
		if (isElectric() && power < getMaxPower() && !inventory.getItem(SLOT_GUNPOWDER).isEmpty())
			setPower(power + dev.schematicannon.compat.NtmCompat.drainBattery(inventory, SLOT_GUNPOWDER, getMaxPower() - power));

		tickChecklist();

		// Update Fuel and Paper
		tickPaperPrinter();
		refillFuelIfPossible();

		// Update Printer: skip over blocks that are already right, within a time budget shared by all cannons
		long tick = level.getServer() == null ? 0 : level.getServer().getTickCount();
		if (tick != scanBudgetTick) {
			scanBudgetTick = tick;
			scanBudgetLeft = ServerConfig.CANNON_SCAN_BUDGET_MS.get() * 1_000_000L;
		}
		long scanStart = System.nanoTime();
		skipsLeft = 1000;
		blockSkipped = true;
		int scanned = 0;
		while (blockSkipped && skipsLeft-- > 0) {
			tickPrinter();
			// every cannon gets at least 32 checks per tick; past that only while the shared budget lasts
			if ((++scanned & 31) == 0 && System.nanoTime() - scanStart > scanBudgetLeft)
				break;
		}
		scanBudgetLeft -= System.nanoTime() - scanStart;

		schematicProgress = 0;
		if (blocksToPlace > 0)
			schematicProgress = (float) blocksPlaced / blocksToPlace;

		// keep the GUI's power bar roughly current without a packet every tick
		if (isElectric()) {
			int percent = (int) (power * 100 / Math.max(1, getMaxPower()));
			if (percent != lastPowerSync) {
				lastPowerSync = percent;
				sendUpdate = true;
			}
		}

		// Update Client block entity
		if (sendUpdate) {
			sendUpdate = false;
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 6);
			setChanged();
		}
	}

	protected void tickPrinter() {
		ItemStack blueprint = inventory.getItem(SLOT_SCHEMATIC);
		blockSkipped = false;

		if (blueprint.isEmpty() && !statusMsg.equals("idle") && inventory.getItem(SLOT_SCHEMATIC_OUT).isEmpty()) {
			state = State.STOPPED;
			statusMsg = "idle";
			sendUpdate = true;
			return;
		}

		// Skip if not Active
		if (state == State.STOPPED) {
			if (printer.isLoaded())
				resetPrinter();
			return;
		}

		// Paused with nothing left to wait for (e.g. freshly loaded and \"Ready\"): stay paused until Start.
		// A creative crate counts as fuelled, otherwise it would skip the Start press.
		if (state == State.PAUSED && !positionNotLoaded && missingItem.isEmpty() && (remainingFuel > 0 || hasCreativeCrate))
			return;

		// Initialize Printer
		if (!printer.isLoaded()) {
			if (claimInitSlot())
				initializePrinter(blueprint);
			return;
		}

		// Cooldown from last shot
		if (printerCooldown > 0) {
			printerCooldown--;
			return;
		}

		// Check Fuel
		if (remainingFuel <= 0 && !hasCreativeCrate) {
			refillFuelIfPossible();
			if (remainingFuel <= 0) {
				state = State.PAUSED;
				statusMsg = outOfFuelStatus();
				sendUpdate = true;
				return;
			}
		}

		if (hasCreativeCrate) {
			remainingFuel = 0;
			if (!missingItem.isEmpty()) {
				missingItem = ItemStack.EMPTY;
				state = State.RUNNING;
			}
		}

		// Update Target
		if (missingItem.isEmpty() && !positionNotLoaded) {
			if (!printer.advanceCurrentPos()) {
				finishedPrinting();
				return;
			}
			sendUpdate = true;
		}

		// Check block
		BlockPos target = printer.getCurrentTarget();
		if (target == null || !level.isLoaded(target)) {
			positionNotLoaded = true;
			statusMsg = "targetNotLoaded";
			state = State.PAUSED;
			return;
		} else if (positionNotLoaded) {
			positionNotLoaded = false;
			state = State.RUNNING;
		}

		// Get item requirement
		ItemRequirement requirement = printer.getCurrentRequirement();
		printer.setOwner(owner);
		printer.consumeClaimDenied();
		if (requirement.isInvalid() || !printer.shouldPlaceCurrent(level, this::shouldPlace)) {
			boolean claimDenied = printer.consumeClaimDenied();
			if (claimDenied)
				claimSkipped++;
			String status = claimDenied ? "claimProtected" : "searching";
			sendUpdate = !statusMsg.equals(status);
			statusMsg = status;
			blockSkipped = true;
			return;
		}

		// Find item
		List<ItemRequirement.StackRequirement> requiredItems = requirement.getRequiredItems();
		if (!requirement.isEmpty()) {
			for (ItemRequirement.StackRequirement required : requiredItems) {
				if (!grabItemsFromAttachedInventories(required, true)) {
					if (skipMissing) {
						statusMsg = "skipping";
						blockSkipped = true;
						if (!missingItem.isEmpty()) {
							missingItem = ItemStack.EMPTY;
							state = State.RUNNING;
						}
						return;
					}

					// a display copy: requirements can be bigger than a stack (e.g. 960 bolts), saved stacks cannot
					missingItem = required.stack.copyWithCount(Math.min(required.stack.getCount(), required.stack.getMaxStackSize()));
					state = State.PAUSED;
					statusMsg = "missingBlock";
					sendUpdate = true;
					return;
				}
			}

			for (ItemRequirement.StackRequirement required : requiredItems)
				grabItemsFromAttachedInventories(required, false);
		}

		// Success
		state = State.RUNNING;
		// a copy: requirements are cached and shared, and the stack is handed to the block's setPlacedBy
		ItemStack icon = requirement.isEmpty() || requiredItems.isEmpty() ? ItemStack.EMPTY : requiredItems.get(0).stack.copyWithCount(1);
		ItemStack battery = takenBattery;
		takenBattery = null;
		printer.handleCurrentTarget(level, (pos, blockState, data) -> {
			// the battery actually taken (with its charge) rides along and goes into the pasted battery socket
			if (battery != null)
				data = dev.schematicannon.compat.NtmCompat.withBattery(level, data, battery);
			// Launch block
			statusMsg = !blockState.isAir() ? "placing" : "clearing";
			launchBlock(pos, icon, blockState, data);
		}, (pos, entity) -> {
			// Launch entity
			statusMsg = "placing";
			launchEntity(pos, icon, entity);
		});

		printerCooldown = isElectric() ? ServerConfig.ADVANCED_DELAY.get() : ServerConfig.SCHEMATICANNON_DELAY.get();
		remainingFuel -= 1;
		sendUpdate = true;
		missingItem = ItemStack.EMPTY;
	}

	public int getShotsPerGunpowder() {
		return hasCreativeCrate ? 0 : ServerConfig.SCHEMATICANNON_SHOTS_PER_GUNPOWDER.get();
	}

	protected void initializePrinter(ItemStack blueprint) {
		if (!blueprint.has(ModComponents.SCHEMATIC_ANCHOR.get())) {
			state = State.STOPPED;
			statusMsg = "schematicInvalid";
			sendUpdate = true;
			return;
		}

		if (!blueprint.getOrDefault(ModComponents.SCHEMATIC_DEPLOYED.get(), false)) {
			state = State.STOPPED;
			statusMsg = "schematicNotPlaced";
			sendUpdate = true;
			return;
		}

		// Load blocks into reader
		printer.setOwner(owner);
		claimSkipped = 0;
		printer.loadSchematic(blueprint, level);

		if (printer.isErrored()) {
			state = State.STOPPED;
			statusMsg = "schematicErrored";
			inventory.setItem(SLOT_SCHEMATIC, ItemStack.EMPTY);
			inventory.setItem(SLOT_SCHEMATIC_OUT, new ItemStack(ModItems.EMPTY_SCHEMATIC.get()));
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		if (printer.isWorldEmpty()) {
			state = State.STOPPED;
			statusMsg = "schematicExpired";
			inventory.setItem(SLOT_SCHEMATIC, ItemStack.EMPTY);
			inventory.setItem(SLOT_SCHEMATIC_OUT, new ItemStack(ModItems.EMPTY_SCHEMATIC.get()));
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		BlockPos anchor = printer.getAnchor();
		if (anchor == null || !anchor.closerThan(getBlockPos(), MAX_ANCHOR_DISTANCE)) {
			state = State.STOPPED;
			statusMsg = "targetOutsideRange";
			printer.resetSchematic();
			sendUpdate = true;
			return;
		}

		state = State.PAUSED;
		statusMsg = "ready";
		updateChecklist();
		sendUpdate = true;
		blocksToPlace += blocksPlaced;
	}

	protected boolean grabItemsFromAttachedInventories(ItemRequirement.StackRequirement required, boolean simulate) {
		if (hasCreativeCrate)
			return true;

		int needed = required.stack.getCount();
		// a battery for a battery socket: whichever battery is taken goes into the pasted socket, charge and all
		if (dev.schematicannon.compat.NtmCompat.isBattery(required.stack)) {
			try (Transaction tx = Transaction.openRoot()) {
				for (ResourceHandler<ItemResource> handler : attachedInventories)
					for (int slot = 0; slot < handler.size(); slot++) {
						ItemResource resource = handler.getResource(slot);
						if (resource.isEmpty() || !required.matches(resource.toStack()))
							continue;
						if (handler.extract(slot, resource, 1, tx) < 1)
							continue;
						if (!simulate) {
							tx.commit();
							takenBattery = resource.toStack(1);
						}
						return true;
					}
				return false;
			}
		}
		try (Transaction tx = Transaction.openRoot()) {
			int found = 0;
			outer:
			for (ResourceHandler<ItemResource> handler : attachedInventories) {
				for (int slot = 0; slot < handler.size(); slot++) {
					ItemResource resource = handler.getResource(slot);
					if (resource.isEmpty() || !required.matches(resource.toStack()))
						continue;
					found += handler.extract(slot, resource, needed - found, tx);
					if (found >= needed)
						break outer;
				}
			}
			if (found < needed)
				return false;
			if (!simulate)
				tx.commit();
			return true;
		}
	}

	public void finishedPrinting() {
		if (replaceMode == ConfigureSchematicannonOption.REPLACE_EMPTY.ordinal())
			printer.sendBlockUpdates(level);
		inventory.setItem(SLOT_SCHEMATIC, ItemStack.EMPTY);
		inventory.setItem(SLOT_SCHEMATIC_OUT, new ItemStack(ModItems.EMPTY_SCHEMATIC.get(),
			inventory.getItem(SLOT_SCHEMATIC_OUT).getCount() + 1));
		state = State.STOPPED;
		statusMsg = "finished";
		resetPrinter();
		level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 0.7f);
		sendUpdate = true;
	}

	protected void resetPrinter() {
		counting = false;
		checklistFresh = false;
		printer.resetSchematic();
		missingItem = ItemStack.EMPTY;
		sendUpdate = true;
		schematicProgress = 0;
		blocksPlaced = 0;
		blocksToPlace = 0;
	}

	protected boolean shouldPlace(BlockPos pos, BlockState state, BlockState toReplace, @Nullable BlockState toReplaceOther,
		boolean isNormalCube) {
		if (pos.closerThan(getBlockPos(), 2f))
			return false;
		if (!replaceBlockEntities
			&& (toReplace.hasBlockEntity() || (toReplaceOther != null && toReplaceOther.hasBlockEntity())))
			return false;

		if (shouldIgnoreBlockState(state))
			return false;

		boolean placingAir = state.isAir();

		if (replaceMode == 3)
			return true;
		if (replaceMode == 2 && !placingAir)
			return true;
		if (replaceMode == 1 && (isNormalCube || (!toReplace.isRedstoneConductor(level, pos)
			&& (toReplaceOther == null || !toReplaceOther.isRedstoneConductor(level, pos)))) && !placingAir)
			return true;
		if (replaceMode == 0 && !toReplace.isRedstoneConductor(level, pos)
			&& (toReplaceOther == null || !toReplaceOther.isRedstoneConductor(level, pos)) && !placingAir)
			return true;

		return false;
	}

	protected boolean shouldIgnoreBlockState(BlockState state) {
		// Block doesn't have a mapping (Water, lava, etc)
		if (state.getBlock() == Blocks.STRUCTURE_VOID)
			return true;

		ItemRequirement requirement = ItemRequirement.of(state, null);
		if (requirement.isEmpty())
			return false;
		if (requirement.isInvalid())
			return false;

		// Block doesn't need to be placed twice (Doors, beds, double plants)
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
			return true;
		if (state.hasProperty(BlockStateProperties.BED_PART)
			&& state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD)
			return true;
		return state.getBlock() instanceof PistonHeadBlock;
	}

	protected void tickFlyingBlocks() {
		flyingBlocks.removeIf(b -> b.update(level));
	}

	protected void refillFuelIfPossible() {
		if (hasCreativeCrate)
			return;
		if (isElectric()) {
			refillFromPower();
			return;
		}
		if (remainingFuel > getShotsPerGunpowder()) {
			remainingFuel = getShotsPerGunpowder();
			sendUpdate = true;
			return;
		}

		if (remainingFuel > 0)
			return;

		if (!inventory.getItem(SLOT_GUNPOWDER).isEmpty()) {
			inventory.getItem(SLOT_GUNPOWDER).shrink(1);
			inventory.setChanged();
		} else {
			boolean externalGunpowderFound = false;
			try (Transaction tx = Transaction.openRoot()) {
				ItemResource gunpowder = ItemResource.of(Items.GUNPOWDER);
				for (ResourceHandler<ItemResource> handler : attachedInventories) {
					if (handler.extract(gunpowder, 1, tx) == 1) {
						externalGunpowderFound = true;
						tx.commit();
						break;
					}
				}
			}
			if (!externalGunpowderFound)
				return;
		}

		remainingFuel += getShotsPerGunpowder();
		if (statusMsg.equals("noGunpowder")) {
			if (blocksPlaced > 0)
				state = State.RUNNING;
			statusMsg = "ready";
		}
		sendUpdate = true;
	}

	/** Electric cannon: each shot is paid for with stored HE, one shot at a time. */
	private void refillFromPower() {
		if (remainingFuel > 1)
			remainingFuel = 1; // e.g. gunpowder shots left over from before
		if (remainingFuel > 0)
			return;
		long cost = getEnergyPerShot();
		if (power < cost)
			return;
		setPower(power - cost);
		remainingFuel = 1;
		if (statusMsg.equals("noPower")) {
			if (blocksPlaced > 0)
				state = State.RUNNING;
			statusMsg = "ready";
			sendUpdate = true;
		}
	}

	protected void tickPaperPrinter() {
		ItemStack blueprint = inventory.getItem(SLOT_SCHEMATIC);
		ItemStack paper = inventory.getItem(SLOT_BOOK_IN);
		ItemStack output = inventory.getItem(SLOT_BOOK_OUT);
		boolean clipboard = paper.is(ModItems.CLIPBOARD.get());
		// clipboards each carry their own list, so they never stack in the output
		boolean outputFull = !output.isEmpty()
			&& (clipboard || output.is(ModItems.CLIPBOARD.get()) || output.getCount() >= output.getMaxStackSize());

		if (printer.isErrored())
			return;

		if (!printer.isLoaded()) {
			// loading a schematic is the most expensive step: at most two cannons start per server tick
			if (!blueprint.isEmpty() && claimInitSlot())
				initializePrinter(blueprint);
			return;
		}

		if (paper.isEmpty() || outputFull) {
			if (bookPrintingProgress != 0)
				sendUpdate = true;
			bookPrintingProgress = 0;
			dontUpdateChecklist = false;
			return;
		}

		if (bookPrintingProgress >= 1) {
			// needs an up-to-date list: (re)count first, over the next ticks, then print
			if ((!dontUpdateChecklist || clipboard) && !checklistFresh) {
				if (!counting)
					updateChecklist();
				return;
			}
			bookPrintingProgress = 0;
			if (!dontUpdateChecklist || clipboard)
				checklistFresh = false; // the next book / clipboard recounts

			dontUpdateChecklist = true;
			ItemStack stack;
			if (clipboard) {
				// write a fresh list, or update the one already on this clipboard
				stack = paper.copyWithCount(1);
				stack.set(ModComponents.CLIPBOARD.get(), checklist.createClipboard(
					dev.schematicannon.clipboard.ClipboardItem.content(paper)));
			} else {
				stack = checklist.createWrittenBook();
				stack.setCount(output.getCount() + 1);
			}
			paper.shrink(1);
			inventory.setItem(SLOT_BOOK_OUT, stack);
			sendUpdate = true;
			return;
		}

		bookPrintingProgress += 0.05f;
		sendUpdate = true;
	}

	protected void launchBlock(BlockPos target, ItemStack stack, BlockState state, @Nullable CompoundTag data) {
		if (!state.isAir())
			blocksPlaced++;
		flyingBlocks.add(new LaunchedItem.ForBlockState(this.getBlockPos(), target, stack, state, data));
		playFiringSound();
	}

	protected void launchEntity(BlockPos target, ItemStack stack, Entity entity) {
		blocksPlaced++;
		flyingBlocks.add(new LaunchedItem.ForEntity(this.getBlockPos(), target, stack, entity));
		playFiringSound();
	}

	public void playFiringSound() {
		level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, .1f, 1.1f);
		if (level instanceof ServerLevel serverLevel)
			serverLevel.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + .5, worldPosition.getY() + 1.1,
				worldPosition.getZ() + .5, 1, .05, .05, .05, .005);
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new SchematicannonMenu(id, inv, this);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(isElectric() ? "schematicannon.gui.advancedSchematicannon.title"
			: "schematicannon.gui.schematicannon.title");
	}

	/** Blocks of the material list counted per tick: big schematics are counted over several ticks, not in one. */
	private static final int COUNT_PER_TICK = 1024;
	private boolean counting;
	/** The material list was recounted and not yet used for a book / clipboard. */
	private boolean checklistFresh;

	/** Starts recounting the material list; it finishes over the next ticks (see {@link #tickChecklist}). */
	public void updateChecklist() {
		checklist.required.clear();
		checklist.damageRequired.clear();
		checklist.blocksNotLoaded = false;
		checklistFresh = false;

		if (printer.isLoaded() && !printer.isErrored()) {
			blocksToPlace = blocksPlaced;
			printer.startCounting();
			counting = true;
			tickChecklist();
		} else {
			finishChecklist();
		}
	}

	private void tickChecklist() {
		if (!counting)
			return;
		blocksToPlace += printer.countBlockRequirements(checklist, level, this::shouldPlace, COUNT_PER_TICK);
		if (!printer.isCounting())
			finishChecklist();
	}

	private void finishChecklist() {
		counting = false;
		checklistFresh = true;
		if (printer.isLoaded() && !printer.isErrored())
			printer.markAllEntityRequirements(checklist);

		checklist.gathered.clear();
		findInventories();
		for (ResourceHandler<ItemResource> handler : attachedInventories) {
			for (int slot = 0; slot < handler.size(); slot++) {
				ItemResource resource = handler.getResource(slot);
				if (resource.isEmpty())
					continue;
				checklist.collect(resource.toStack(handler.getAmountAsInt(slot)));
			}
		}
		sendUpdate = true;
	}

	public enum State {
		STOPPED, PAUSED, RUNNING
	}
}
