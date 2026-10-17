package dev.schematicannon.cannon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The first four values double as replace modes, in the same order as Create. */
public enum ConfigureSchematicannonOption {
	DONT_REPLACE, REPLACE_SOLID, REPLACE_ANY, REPLACE_EMPTY, SKIP_MISSING, SKIP_BLOCK_ENTITIES, PLAY, PAUSE, STOP;

	public static final StreamCodec<ByteBuf, ConfigureSchematicannonOption> STREAM_CODEC =
		ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)], ConfigureSchematicannonOption::ordinal);
}
