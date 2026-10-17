package dev.schematicannon.compat;

import com.hbm.api.energymk2.EnergyCaps;
import com.hbm.api.energymk2.IEnergyHandlerMK2;

import dev.schematicannon.Schematicannon;
import dev.schematicannon.cannon.SchematicannonBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Only loaded when NTM is present. Exposes the Advanced Atomicannon to NTM's power network as an energy receiver,
 * so NTM cables connect to its power ports (every side except the top, where the turret sits).
 */
final class NtmEnergy {
	static void register(RegisterCapabilitiesEvent event, BlockEntityType<SchematicannonBlockEntity> type) {
		if (EnergyCaps.RECEIVER == null) {
			Schematicannon.LOGGER.error("NTM's energy capability is not set up; the Advanced Atomicannon cannot take power");
			return;
		}
		event.registerBlockEntity(EnergyCaps.RECEIVER, type,
			(be, side) -> be.isElectric() && side != Direction.UP ? handler(be) : null);
	}

	static long batteryCharge(net.minecraft.world.item.ItemStack stack) {
		try {
			return stack.getItem() instanceof com.hbm.api.energymk2.IBatteryItem b ? b.getCharge(stack) : 0;
		} catch (LinkageError | RuntimeException e) {
			return 0;
		}
	}

	static boolean isBattery(net.minecraft.world.item.ItemStack stack) {
		try {
			return com.hbm.api.energymk2.IBatteryItem.isBattery(stack)
				|| stack.getItem() instanceof com.hbm.items.machine.ItemBatteryCreative;
		} catch (LinkageError e) {
			return false;
		}
	}

	static long drainBattery(net.minecraft.world.Container container, int slot, long max) {
		try {
			return Math.max(0, com.hbm.api.energymk2.ItemEnergyTransfer.extract(container, slot, max, false));
		} catch (LinkageError | RuntimeException e) {
			return 0;
		}
	}

	private static IEnergyHandlerMK2 handler(SchematicannonBlockEntity be) {
		if (be.energyHandler instanceof Receiver receiver)
			return receiver;
		Receiver receiver = new Receiver(be);
		be.energyHandler = receiver;
		return receiver;
	}

	private record Receiver(SchematicannonBlockEntity be) implements IEnergyHandlerMK2 {
		@Override
		public long getPower() {
			return be.power;
		}

		@Override
		public void setPower(long power) {
			be.setPower(power);
		}

		@Override
		public long getMaxPower() {
			return be.getMaxPower();
		}
	}

	private NtmEnergy() {}
}
