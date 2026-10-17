package dev.schematicannon.registry;

import com.mojang.serialization.Codec;

import dev.schematicannon.Schematicannon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
	public static final DeferredRegister.DataComponents REGISTER =
		DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Schematicannon.MOD_ID);

	public static final StreamCodec<ByteBuf, Rotation> ROTATION_STREAM_CODEC =
		ByteBufCodecs.idMapper(i -> Rotation.values()[i], Rotation::ordinal);
	public static final StreamCodec<ByteBuf, Mirror> MIRROR_STREAM_CODEC =
		ByteBufCodecs.idMapper(i -> Mirror.values()[i], Mirror::ordinal);
	public static final StreamCodec<ByteBuf, Vec3i> VEC3I_STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Vec3i::getX,
		ByteBufCodecs.VAR_INT, Vec3i::getY,
		ByteBufCodecs.VAR_INT, Vec3i::getZ,
		Vec3i::new);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SCHEMATIC_FILE =
		REGISTER.registerComponentType("schematic_file",
			b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SCHEMATIC_OWNER =
		REGISTER.registerComponentType("schematic_owner",
			b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SCHEMATIC_DEPLOYED =
		REGISTER.registerComponentType("schematic_deployed",
			b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> SCHEMATIC_ANCHOR =
		REGISTER.registerComponentType("schematic_anchor",
			b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Rotation>> SCHEMATIC_ROTATION =
		REGISTER.registerComponentType("schematic_rotation",
			b -> b.persistent(Rotation.CODEC).networkSynchronized(ROTATION_STREAM_CODEC));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Mirror>> SCHEMATIC_MIRROR =
		REGISTER.registerComponentType("schematic_mirror",
			b -> b.persistent(Mirror.CODEC).networkSynchronized(MIRROR_STREAM_CODEC));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Vec3i>> SCHEMATIC_BOUNDS =
		REGISTER.registerComponentType("schematic_bounds",
			b -> b.persistent(Vec3i.CODEC).networkSynchronized(VEC3I_STREAM_CODEC));

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<dev.schematicannon.clipboard.ClipboardContent>> CLIPBOARD =
		REGISTER.registerComponentType("clipboard",
			b -> b.persistent(dev.schematicannon.clipboard.ClipboardContent.CODEC)
				.networkSynchronized(dev.schematicannon.clipboard.ClipboardContent.STREAM_CODEC));

	private ModComponents() {}
}
