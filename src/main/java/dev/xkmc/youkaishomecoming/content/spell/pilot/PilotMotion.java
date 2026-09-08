package dev.xkmc.youkaishomecoming.content.spell.pilot;

import net.minecraft.world.phys.Vec3;

/** Limits pilot commands and preserves only vertical motion owned by normal ground physics. */
public final class PilotMotion {

	private PilotMotion() {
	}

	public static Vec3 limitSpeed(Vec3 velocity, double maxSpeed) {
		if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y)
				|| !Double.isFinite(velocity.z) || !Double.isFinite(maxSpeed) || maxSpeed <= 0) {
			return Vec3.ZERO;
		}
		double speed = Math.hypot(Math.hypot(velocity.x, velocity.y), velocity.z);
		return speed > maxSpeed ? velocity.scale(maxSpeed / speed) : velocity;
	}

	public static Vec3 playerVelocity(Vec3 desired, Vec3 current, boolean freeFlight, boolean onGround) {
		// Zero is an explicit brake in flight, including elytra. Retaining current.y
		// here would undo a ceiling/floor rejection and preserve a previous climb.
		if (freeFlight) return desired;
		double y = onGround ? desired.y > 0 ? desired.y : Math.min(0, current.y) : current.y;
		return new Vec3(desired.x, y, desired.z);
	}

	public static Vec3 afterCollision(Vec3 requested, Vec3 resolved, boolean freeFlight) {
		// Ground physics must still attempt its vertical step. Pre-clipping gravity
		// to zero would hide the floor collision from Entity.move and clear onGround.
		return freeFlight ? resolved : new Vec3(resolved.x, requested.y, resolved.z);
	}
}
