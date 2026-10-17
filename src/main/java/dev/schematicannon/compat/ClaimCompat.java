package dev.schematicannon.compat;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.schematicannon.Schematicannon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Land-claim protection for schematic printing. With WarForge installed, every block a schematic places or
 * replaces is checked against WarForge's own protection rules for the player who started the print, exactly
 * as if that player had placed/broken the block by hand. Without a claims mod, everything is allowed.
 */
public final class ClaimCompat {
	private static boolean warforge;

	public static void init() {
		warforge = ModList.get().isLoaded("warforge");
		if (warforge)
			Schematicannon.LOGGER.info("WarForge detected: schematics respect faction claims");
	}

	/**
	 * @param owner    the player responsible for the print (null = nobody; treated like a stranger)
	 * @param newState the block the schematic wants at {@code pos} (air when clearing)
	 * @param existing the block currently in the world at {@code pos}
	 */
	public static boolean canModify(Level level, @Nullable UUID owner, BlockPos pos, BlockState newState, BlockState existing) {
		if (!warforge || level.isClientSide())
			return true;
		return WarForgeClaims.canModify(level, owner, pos, newState, existing);
	}

	private ClaimCompat() {}
}
