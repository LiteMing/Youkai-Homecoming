package dev.xkmc.youkaishomecoming.content.spell.mover;

import net.minecraft.world.phys.Vec3;

/**
 * Implemented by movers that support rebasing their trajectory from a new position and velocity
 * after a collision / bounce event.
 */
public interface CollisionRebasableMover {

	/**
	 * Creates a fresh mover instance rebased at the new position with the new velocity vector.
	 *
	 * @param newPosition the new collision separation position
	 * @param newVelocity the new outgoing velocity vector
	 * @return a new rebased DanmakuMover instance
	 */
	DanmakuMover rebaseAfterCollision(Vec3 newPosition, Vec3 newVelocity);
}
