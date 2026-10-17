package dev.schematicannon.compat;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.flansmod.warforge.common.ProtectionsModule;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;

import dev.schematicannon.Schematicannon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Only loaded when WarForge is present. Uses WarForge's own place/break protection rules. */
final class WarForgeClaims {
	private static boolean warned;

	static boolean canModify(Level level, @Nullable UUID owner, BlockPos pos, BlockState newState, BlockState existing) {
		try {
			// Ops bypass claims only when WarForge's own op override is switched on, same as hand placement.
			if (ProtectionsModule.OP_OVERRIDE && owner != null && level.getServer() != null) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
				if (player != null && WarForgeMod.isOp(player))
					return true;
			}

			WarForgeConfig.ProtectionConfig rules = ProtectionsModule.GetProtections(owner,
				new DimBlockPos(level.dimension(), pos.getX(), pos.getY(), pos.getZ()));
			if (rules == null)
				return true;
			// replacing or clearing a block counts as breaking it
			if (!existing.isAir() && existing != newState && ProtectionsModule.breakDenied(rules, existing.getBlock()))
				return false;
			return newState.isAir() || !ProtectionsModule.placeDenied(rules, newState.getBlock());
		} catch (LinkageError | RuntimeException e) {
			// Fail closed: if the claims API can't be read, don't let schematics modify anything.
			if (!warned) {
				warned = true;
				Schematicannon.LOGGER.error("Could not check WarForge claim protection; schematic printing is blocked", e);
			}
			return false;
		}
	}

	private WarForgeClaims() {}
}
