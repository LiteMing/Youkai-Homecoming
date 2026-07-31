package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import net.minecraft.world.phys.AABB;

/**
 * World obstacle queries for pilot candidates. Core never imports Level.
 * Preview: always free. Server: wrap {@code level.noCollision}.
 */
public interface CollisionOracle {

	boolean isFree(AABB box);

	/** Footing support for grounded model (Phase 7). Default always supported. */
	default boolean isSupported(AABB box) {
		return true;
	}

	CollisionOracle ALWAYS_FREE = box -> true;
}
