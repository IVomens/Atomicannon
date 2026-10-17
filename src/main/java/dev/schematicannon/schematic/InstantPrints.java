package dev.schematicannon.schematic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Creative "instant print" jobs. Placing a whole schematic in one tick can freeze a server for seconds, so prints are
 * spread over ticks: every tick all jobs together get a small time budget, and each job resumes where it stopped.
 */
public final class InstantPrints {
	private record Job(UUID player, ServerLevel level, SchematicPrinter printer, boolean includeAir) {}

	private static final List<Job> JOBS = new ArrayList<>();

	/** Starts a print for this player, replacing any print of theirs still running. */
	public static void start(UUID player, ServerLevel level, SchematicPrinter printer, boolean includeAir) {
		JOBS.removeIf(job -> job.player().equals(player));
		JOBS.add(new Job(player, level, printer, includeAir));
	}

	public static void tick(MinecraftServer server) {
		if (JOBS.isEmpty())
			return;
		long budget = ServerConfig.INSTANT_PRINT_TICK_BUDGET_MS.get() * 1_000_000L;
		tickJobs(server, System.nanoTime() + budget);
	}

	private static void tickJobs(MinecraftServer server, long deadline) {
		Iterator<Job> it = JOBS.iterator();
		while (it.hasNext() && System.nanoTime() < deadline) {
			Job job = it.next();
			boolean done;
			try {
				done = run(job, deadline);
			} catch (RuntimeException | LinkageError e) {
				Schematicannon.LOGGER.error("Instant schematic print stopped after an error", e);
				done = true;
			}
			if (done) {
				it.remove();
				var player = server.getPlayerList().getPlayer(job.player());
				if (player != null)
					player.sendOverlayMessage(Component.translatable("schematicannon.schematic.instantPrintDone"));
			}
		}
	}

	/** @return true when the job has finished. */
	private static boolean run(Job job, long deadline) {
		SchematicPrinter printer = job.printer();
		ServerLevel level = job.level();
		int steps = 0;
		while (true) {
			// check the clock every 64 blocks, not every block
			if ((++steps & 63) == 0 && System.nanoTime() >= deadline)
				return false;
			if (!printer.advanceCurrentPos())
				return true;
			if (!printer.shouldPlaceCurrent(level)) // also skips unloaded chunks: never force-loads
				continue;
			printer.handleCurrentTarget(level, (pos, state, data) -> {
				if (state.isAir() && !job.includeAir())
					return;
				SchematicPlacement.placeSchematicBlock(level, state, pos, null, data);
			}, (pos, entity) -> level.addFreshEntity(entity));
		}
	}

	public static boolean isRunning(UUID player) {
		return JOBS.stream().anyMatch(job -> job.player().equals(player));
	}

	public static void clear() {
		JOBS.clear();
	}

	private InstantPrints() {}
}
