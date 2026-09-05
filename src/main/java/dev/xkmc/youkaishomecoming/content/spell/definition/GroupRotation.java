package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Group-level rotation applied to the entire danmaku pattern's coordinate system.
 * Rotates the Orientation (baseDir + normal + side) by Euler angles (degrees).
 * Applied after aimMode/origin.rotation/tiltAngle in the firing frame's local axes.
 *
 * rotX = pitch (tilt forward/backward)
 * rotY = yaw (rotate left/right)
 * rotZ = roll (spin around forward axis)
 *
 * This affects initial velocities and the base direction supplied to movers.
 */
public record GroupRotation(NumberProvider rotX, NumberProvider rotY, NumberProvider rotZ) {

	public static final Codec<GroupRotation> CODEC = RecordCodecBuilder.create(i -> i.group(
			NumberProvider.CODEC.optionalFieldOf("rot_x", NumberProvider.constant(0)).forGetter(GroupRotation::rotX),
			NumberProvider.CODEC.optionalFieldOf("rot_y", NumberProvider.constant(0)).forGetter(GroupRotation::rotY),
			NumberProvider.CODEC.optionalFieldOf("rot_z", NumberProvider.constant(0)).forGetter(GroupRotation::rotZ)
	).apply(i, GroupRotation::new));

	/** Apply local roll, pitch and yaw to the complete firing frame. */
	public DanmakuHelper.Orientation apply(DanmakuHelper.Orientation input,
			dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext ctx) {
		double rx = Math.toRadians(rotX.get(ctx));
		double ry = Math.toRadians(rotY.get(ctx));
		double rz = Math.toRadians(rotZ.get(ctx));

		Vec3 forward = input.forward().normalize();
		Vec3 normal = input.normal().normalize();
		Vec3 side = input.side().normalize();
		if (rz != 0) {
			normal = rotateAroundAxis(normal, forward, rz);
			side = rotateAroundAxis(side, forward, rz);
		}
		if (rx != 0) {
			forward = rotateAroundAxis(forward, side, rx);
			normal = rotateAroundAxis(normal, side, rx);
		}
		if (ry != 0) {
			forward = rotateAroundAxis(forward, normal, ry);
			side = rotateAroundAxis(side, normal, ry);
		}
		forward = forward.normalize();
		normal = normal.subtract(forward.scale(normal.dot(forward))).normalize();
		side = forward.cross(normal).normalize();
		return new DanmakuHelper.Orientation(forward, normal, side);
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
