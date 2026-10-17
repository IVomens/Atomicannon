package dev.schematicannon.client;

import static java.lang.Math.abs;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.schematicannon.client.util.Chaser;
import dev.schematicannon.client.util.Raycast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Port of Create's SchematicTransformation: the animated position/rotation/mirror of the held schematic. */
public class SchematicTransformation {
	private Vec3 chasingPos = Vec3.ZERO;
	private Vec3 prevChasingPos = Vec3.ZERO;
	private BlockPos target = BlockPos.ZERO;
	private final Chaser scaleFrontBack = Chaser.linear();
	private final Chaser scaleLeftRight = Chaser.linear();
	private final Chaser rotation = Chaser.angular();
	private double xOrigin;
	private double zOrigin;

	public void init(BlockPos anchor, StructurePlaceSettings settings, AABB bounds) {
		int leftRight = settings.getMirror() == Mirror.LEFT_RIGHT ? -1 : 1;
		int frontBack = settings.getMirror() == Mirror.FRONT_BACK ? -1 : 1;
		scaleFrontBack.chase(0, 0.45f).startWithValue(frontBack);
		scaleFrontBack.updateChaseTarget(frontBack);
		scaleLeftRight.chase(0, 0.45f).startWithValue(leftRight);
		scaleLeftRight.updateChaseTarget(leftRight);
		xOrigin = bounds.getXsize() / 2f;
		zOrigin = bounds.getZsize() / 2f;

		int r = -(settings.getRotation().ordinal() * 90);
		rotation.chase(0, 0.45f).startWithValue(r);
		rotation.updateChaseTarget(r);
		target = fromAnchor(anchor);
		chasingPos = Vec3.atLowerCornerOf(target);
		prevChasingPos = chasingPos;
	}

	public void applyTransformations(PoseStack ms, Vec3 camera) {
		float pt = Raycast.partialTicks();

		// Translation
		ms.translate(lerp(pt, prevChasingPos, chasingPos).subtract(camera));
		Vec3 rotationOffset = getRotationOffset(true);

		// Rotation & Mirror
		float fb = scaleFrontBack.getValue(pt);
		float lr = scaleLeftRight.getValue(pt);
		float rot = rotation.getValue(pt) + ((fb < 0 && lr < 0) ? 180 : 0);
		ms.translate(xOrigin, 0, zOrigin);
		ms.translate(rotationOffset);
		ms.mulPose(new Quaternionf().rotationY(rot * Mth.DEG_TO_RAD));
		ms.translate(rotationOffset.scale(-1));
		ms.scale(abs(fb), 1, abs(lr));
		ms.translate(-xOrigin, 0, -zOrigin);
	}

	public boolean isFlipped() {
		return getMirrorModifier(Axis.X) < 0 != getMirrorModifier(Axis.Z) < 0;
	}

	public Vec3 getRotationOffset(boolean ignoreMirrors) {
		Vec3 rotationOffset = Vec3.ZERO;
		if ((int) (zOrigin * 2) % 2 != (int) (xOrigin * 2) % 2) {
			boolean xGreaterZ = xOrigin > zOrigin;
			float xIn = (xGreaterZ ? 0 : .5f);
			float zIn = (!xGreaterZ ? 0 : .5f);
			if (!ignoreMirrors) {
				xIn *= getMirrorModifier(Axis.X);
				zIn *= getMirrorModifier(Axis.Z);
			}
			rotationOffset = new Vec3(xIn, 0, zIn);
		}
		return rotationOffset;
	}

	public Vec3 toLocalSpace(Vec3 vec) {
		float pt = Raycast.partialTicks();
		Vec3 rotationOffset = getRotationOffset(true);

		vec = vec.subtract(lerp(pt, prevChasingPos, chasingPos));
		vec = vec.subtract(xOrigin + rotationOffset.x, 0, zOrigin + rotationOffset.z);
		vec = rotate(vec, -rotation.getValue(pt));
		vec = vec.add(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(scaleFrontBack.getValue(pt), 1, scaleLeftRight.getValue(pt));
		vec = vec.add(xOrigin, 0, zOrigin);
		return vec;
	}

	public StructurePlaceSettings toSettings() {
		StructurePlaceSettings settings = new StructurePlaceSettings();

		int i = (int) rotation.getChaseTarget();

		boolean mirrorlr = scaleLeftRight.getChaseTarget() < 0;
		boolean mirrorfb = scaleFrontBack.getChaseTarget() < 0;
		if (mirrorlr && mirrorfb) {
			mirrorlr = mirrorfb = false;
			i += 180;
		}
		i = i % 360;
		if (i < 0)
			i += 360;

		Rotation rotation = switch (i) {
			case 90 -> Rotation.COUNTERCLOCKWISE_90;
			case 180 -> Rotation.CLOCKWISE_180;
			case 270 -> Rotation.CLOCKWISE_90;
			default -> Rotation.NONE;
		};

		settings.setRotation(rotation);
		if (mirrorfb)
			settings.setMirror(Mirror.FRONT_BACK);
		if (mirrorlr)
			settings.setMirror(Mirror.LEFT_RIGHT);
		return settings;
	}

	public BlockPos getAnchor() {
		Vec3 vec = Vec3.ZERO.add(.5, 0, .5);
		Vec3 rotationOffset = getRotationOffset(false);
		vec = vec.subtract(xOrigin, 0, zOrigin);
		vec = vec.subtract(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(scaleFrontBack.getChaseTarget(), 1, scaleLeftRight.getChaseTarget());
		vec = rotate(vec, rotation.getChaseTarget());
		vec = vec.add(xOrigin, 0, zOrigin);
		vec = vec.add(target.getX(), target.getY(), target.getZ());
		return BlockPos.containing(vec.x, vec.y, vec.z);
	}

	public BlockPos fromAnchor(BlockPos pos) {
		Vec3 vec = Vec3.ZERO.add(.5, 0, .5);
		Vec3 rotationOffset = getRotationOffset(false);
		vec = vec.subtract(xOrigin, 0, zOrigin);
		vec = vec.subtract(rotationOffset.x, 0, rotationOffset.z);
		vec = vec.multiply(scaleFrontBack.getChaseTarget(), 1, scaleLeftRight.getChaseTarget());
		vec = rotate(vec, rotation.getChaseTarget());
		vec = vec.add(xOrigin, 0, zOrigin);
		return pos.subtract(BlockPos.containing(vec.x, vec.y, vec.z));
	}

	public int getRotationTarget() {
		return (int) rotation.getChaseTarget();
	}

	public int getMirrorModifier(Axis axis) {
		if (axis == Axis.Z)
			return (int) scaleLeftRight.getChaseTarget();
		return (int) scaleFrontBack.getChaseTarget();
	}

	public float getCurrentRotation() {
		return rotation.getValue(Raycast.partialTicks());
	}

	public void tick() {
		prevChasingPos = chasingPos;
		chasingPos = lerp(0.45f, chasingPos, Vec3.atLowerCornerOf(target));
		scaleLeftRight.tickChaser();
		scaleFrontBack.tickChaser();
		rotation.tickChaser();
	}

	public void flip(Axis axis) {
		if (axis == Axis.X)
			scaleLeftRight.updateChaseTarget(scaleLeftRight.getChaseTarget() * -1);
		if (axis == Axis.Z)
			scaleFrontBack.updateChaseTarget(scaleFrontBack.getChaseTarget() * -1);
	}

	public void rotate90(boolean clockwise) {
		rotation.updateChaseTarget(rotation.getChaseTarget() + (clockwise ? -90 : 90));
	}

	public void move(int xIn, int yIn, int zIn) {
		moveTo(target.offset(xIn, yIn, zIn));
	}

	public void startAt(BlockPos pos) {
		chasingPos = Vec3.atLowerCornerOf(pos);
		prevChasingPos = chasingPos;
		moveTo(pos);
	}

	public void moveTo(BlockPos pos) {
		target = pos;
	}

	public Chaser getScaleFB() {
		return scaleFrontBack;
	}

	public Chaser getScaleLR() {
		return scaleLeftRight;
	}

	private static Vec3 lerp(float pt, Vec3 from, Vec3 to) {
		return from.add(to.subtract(from).scale(pt));
	}

	/** Same convention as Catnip's VecHelper.rotate around the Y axis. */
	public static Vec3 rotate(Vec3 vec, double deg) {
		if (deg == 0)
			return vec;
		float angle = (float) (deg / 180f * Math.PI);
		double sin = Mth.sin(angle);
		double cos = Mth.cos(angle);
		return new Vec3(vec.x * cos + vec.z * sin, vec.y, vec.z * cos - vec.x * sin);
	}
}
