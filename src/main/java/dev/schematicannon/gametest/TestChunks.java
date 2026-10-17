package dev.schematicannon.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Force-loads the area a big test builds in. Tests of different batches can run at the same time, so a test releases
 * only its own chunks, when it passes; a full run then never piles up hundreds of loaded chunks.
 */
final class TestChunks {
	/**
	 * A spot for a big test that no other test can reach: far from the test plots, 1024 blocks apart per slot (test
	 * plots are laid out in a grid, so offsets from a plot could collide). Height is the plot's.
	 */
	static BlockPos isolated(net.minecraft.gametest.framework.GameTestHelper helper, int slot, BlockPos inPlot) {
		BlockPos p = helper.absolutePos(inPlot);
		return new BlockPos(2_000_000 + slot * 1024, p.getY(), 2_000_000);
	}

	static List<int[]> force(ServerLevel level, BlockPos min, BlockPos max) {
		List<int[]> forced = new ArrayList<>();
		for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
			for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				forced.add(new int[] { cx, cz });
			}
		return forced;
	}

	static void release(ServerLevel level, List<int[]> forced) {
		for (int[] c : forced)
			level.setChunkForced(c[0], c[1], false);
		forced.clear();
	}

	private TestChunks() {}
}
