package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * World obstacle queries for pilot candidates. Core never imports Level.
 * Preview: always free. Server: wrap {@code level.noCollision}.
 */
public interface CollisionOracle {

	boolean isFree(AABB box);

	/**
	 * Returns whether the complete movement from {@code from} by {@code delta}
	 * is collision-free.  Endpoint-only checks are insufficient for fast pilot
	 * steps because they can jump from one side of a wall to the other.
	 */
	default boolean isPathFree(AABB from, Vec3 delta) {
		return isFree(from.move(delta));
	}

	/** World adapters may retain the safe part of a step, e.g. descending onto a floor. */
	default Vec3 resolveMovement(AABB from, Vec3 delta) {
		return isPathFree(from, delta) && isFree(from.move(delta)) ? delta : Vec3.ZERO;
	}

	/** Footing support for grounded model (Phase 7). Default always supported. */
	default boolean isSupported(AABB box) {
		return true;
	}

	/** Check the footprint along a ground step, including the space below a jump. */
	default boolean isSupportedPath(AABB from, Vec3 delta) {
		double distance = Math.hypot(delta.x, delta.z);
		double step = Math.max(1.0e-4, Math.min(from.getXsize(), from.getZsize()) * 0.5);
		int steps = Math.max(1, (int) Math.ceil(distance / step));
		for (int i = 1; i <= steps; i++) {
			double fraction = (double) i / steps;
			if (!isSupported(from.move(delta.x * fraction, 0, delta.z * fraction))) return false;
		}
		return true;
	}

	/** Shared terrain gate for search, refinement and the final APF command. */
	default boolean isMovementSafe(AABB from, Vec3 delta, boolean grounded) {
		if (!isPathFree(from, delta) || !isFree(from.move(delta))) return false;
		if (!grounded) return true;
		boolean supported = isSupported(from);
		if (delta.y > 1.0e-8 && !supported) return false;
		return !supported || isSupportedPath(from, delta);
	}

	CollisionOracle ALWAYS_FREE = box -> true;
}
