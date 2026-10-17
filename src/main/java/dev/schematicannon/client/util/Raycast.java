package dev.schematicannon.client.util;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Port of the bits of Create's RaycastHelper the schematic tools use. */
public final class Raycast {

	public static Vec3 getTraceTarget(Player player, double range, Vec3 origin) {
		return origin.add(player.getViewVector(partialTicks()).scale(range));
	}

	public static float partialTicks() {
		return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	public static BlockHitResult rayTraceRange(Level level, Player player, double range) {
		Vec3 origin = player.getEyePosition(partialTicks());
		Vec3 target = getTraceTarget(player, range, origin);
		return level.clip(new ClipContext(origin, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
	}

	public static PredicateTraceResult rayTraceUntil(Player player, double range, Predicate<BlockPos> predicate) {
		Vec3 origin = player.getEyePosition(partialTicks());
		return rayTraceUntil(origin, getTraceTarget(player, range, origin), predicate);
	}

	/** Walks the voxels between two points and reports the first one matching, and the face it was entered through. */
	public static PredicateTraceResult rayTraceUntil(Vec3 start, Vec3 end, Predicate<BlockPos> predicate) {
		double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
		int x = (int) Math.floor(start.x), y = (int) Math.floor(start.y), z = (int) Math.floor(start.z);
		int stepX = (int) Math.signum(dx), stepY = (int) Math.signum(dy), stepZ = (int) Math.signum(dz);
		double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
		double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
		double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
		double tMaxX = stepX == 0 ? Double.MAX_VALUE : tDeltaX * (stepX > 0 ? 1 - frac(start.x) : frac(start.x));
		double tMaxY = stepY == 0 ? Double.MAX_VALUE : tDeltaY * (stepY > 0 ? 1 - frac(start.y) : frac(start.y));
		double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : tDeltaZ * (stepZ > 0 ? 1 - frac(start.z) : frac(start.z));

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		if (predicate.test(pos))
			return new PredicateTraceResult(pos.immutable(), Direction.UP);

		Direction face = null;
		for (int i = 0; i < 1024; i++) {
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				if (tMaxX > 1)
					break;
				x += stepX;
				tMaxX += tDeltaX;
				face = stepX > 0 ? Direction.WEST : Direction.EAST;
			} else if (tMaxY < tMaxZ) {
				if (tMaxY > 1)
					break;
				y += stepY;
				tMaxY += tDeltaY;
				face = stepY > 0 ? Direction.DOWN : Direction.UP;
			} else {
				if (tMaxZ > 1)
					break;
				z += stepZ;
				tMaxZ += tDeltaZ;
				face = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
			}
			pos.set(x, y, z);
			if (predicate.test(pos))
				return new PredicateTraceResult(pos.immutable(), face);
		}
		return new PredicateTraceResult(null, null);
	}

	private static double frac(double d) {
		return d - Math.floor(d);
	}

	public record PredicateTraceResult(@Nullable BlockPos pos, @Nullable Direction facing) {
		public boolean missed() {
			return pos == null;
		}

		public @Nullable Direction getFacing() {
			return facing;
		}
	}

	private Raycast() {}
}
