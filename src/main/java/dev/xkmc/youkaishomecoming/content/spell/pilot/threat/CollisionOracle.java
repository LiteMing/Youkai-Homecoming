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

	/** Footing support for grounded model (Phase 7). Default always supported. */
	default boolean isSupported(AABB box) {
		return true;
	}

	CollisionOracle ALWAYS_FREE = box -> true;
}
