package dev.schematicannon.cannon;

import net.minecraft.world.level.block.Block;

/**
 * Infinite material source: a Schematicannon touching one of these needs no items and no gunpowder.
 * Unbreakable in survival, no recipe, no drops - it can only be handed out by operators / creative mode.
 */
public class CreativeCrateBlock extends Block {
	public CreativeCrateBlock(Properties properties) {
		super(properties);
	}
}
