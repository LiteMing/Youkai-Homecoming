package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

/**
 * Group-level rotation applied to the entire danmaku pattern's coordinate system.
 * Rotates the Orientation (baseDir + normal + side) by Euler angles (degrees).
 * Applied AFTER aimMode/origin.rotation/tiltAngle, as an outer transform.
 *
 * rotX = pitch (tilt forward/backward)
 * rotY = yaw (rotate left/right)
 * rotZ = roll (spin around forward axis)
 *
 * This only affects the pattern's spawn positions and initial velocities.
 * It does NOT affect mover behavior (movers use the rotated dir as their local frame).
 */
public record GroupRotation(NumberProvider rotX, NumberProvider rotY, NumberProvider rotZ) {

	public static final Codec<GroupRotation> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.optionalFieldOf("rot_x", NumberProvider.constant(0)).forGetter(GroupRotation::rotX),
			NumberProvider.CODEC.optionalFieldOf("rot_y", NumberProvider.constant(0)).forGetter(GroupRotation::rotY),
			NumberProvider.CODEC.optionalFieldOf("rot_z", NumberProvider.constant(0)).forGetter(GroupRotation::rotZ)
	).apply(i, GroupRotation::new));

	/**
	 * Apply this rotation to a direction vector.
	 * Order: rotZ (roll) → rotX (pitch) → rotY (yaw).
	 */
	public Vec3 apply(Vec3 dir, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext ctx) {
		double rx = Math.toRadians(rotX.get(ctx));
		double ry = Math.toRadians(rotY.get(ctx));
		double rz = Math.toRadians(rotZ.get(ctx));

		// Apply rotations in order: Z → X → Y
		Vec3 v = dir;
		if (rz != 0) v = rotateAroundAxis(v, new Vec3(0, 0, 1), rz);
		if (rx != 0) v = rotateAroundAxis(v, new Vec3(1, 0, 0), rx);
		if (ry != 0) v = rotateAroundAxis(v, new Vec3(0, 1, 0), ry);
		return v;
	}

	/**
	 * Rotate a vector around an arbitrary axis by the given angle (radians).
	 * Uses Rodrigues' rotation formula.
	 */
	private static Vec3 rotateAroundAxis(Vec3 v, Vec3 axis, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double dot = v.dot(axis);
		Vec3 cross = axis.cross(v);
		return v.scale(cos).add(cross.scale(sin)).add(axis.scale(dot * (1 - cos)));
	}
}
