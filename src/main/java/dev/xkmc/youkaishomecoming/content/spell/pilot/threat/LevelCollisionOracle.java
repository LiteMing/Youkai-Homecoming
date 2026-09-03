package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server/client world collision wrapper. Core only sees {@link CollisionOracle}.
 */
public final class LevelCollisionOracle implements CollisionOracle {

	private final Level level;
	private final Entity entity;

	public LevelCollisionOracle(Level level, Entity entity) {
		this.level = level;
		this.entity = entity;
	}

	@Override
	public boolean isFree(AABB box) {
		return level.noCollision(entity, box);
	}

	@Override
	public boolean isPathFree(AABB from, Vec3 delta) {
		if (delta.lengthSqr() < 1.0e-12) return isFree(from);
		// Use Minecraft's swept collision resolver instead of sampling.  If the
		// resolved displacement is shorter than requested, a solid boundary was
		// crossed (or the world border blocked the step), so reject the pilot step.
		AABB swept = from.expandTowards(delta);
		Vec3 resolved = Entity.collideBoundingBox(entity, delta, from, level,
				level.getEntityCollisions(entity, swept));
		return resolved.distanceToSqr(delta) < 1.0e-10;
	}

	@Override
	public boolean isSupported(AABB box) {
		// Footing: block below feet
		AABB feet = new AABB(box.minX, box.minY - 0.05, box.minZ, box.maxX, box.minY, box.maxZ);
		return !level.noCollision(entity, feet);
	}
}
