package dev.schematicannon.client.util;

import net.minecraft.util.Mth;

/** Minimal stand-in for Catnip's LerpedFloat with an exponential chaser. */
public class Chaser {
	private final boolean angular;
	private float previous;
	private float value;
	private float target;
	private float speed = 0.45f;

	private Chaser(boolean angular) {
		this.angular = angular;
	}

	public static Chaser linear() {
		return new Chaser(false);
	}

	public static Chaser angular() {
		return new Chaser(true);
	}

	public Chaser startWithValue(float value) {
		this.previous = this.value = value;
		return this;
	}

	public Chaser chase(float target, float speed) {
		this.target = target;
		this.speed = speed;
		return this;
	}

	public void updateChaseTarget(float target) {
		this.target = target;
	}

	public float getChaseTarget() {
		return target;
	}

	public void tickChaser() {
		previous = value;
		float diff = angular ? Mth.wrapDegrees(target - value) : target - value;
		if (Math.abs(diff) < 1 / 512f) {
			value = angular ? value + diff : target;
			return;
		}
		value += diff * speed;
	}

	public float getValue(float partialTicks) {
		if (angular)
			return previous + Mth.wrapDegrees(value - previous) * partialTicks;
		return Mth.lerp(partialTicks, previous, value);
	}
}
