package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

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
	public boolean isSupported(AABB box) {
		// Footing: block below feet
		AABB feet = new AABB(box.minX, box.minY - 0.05, box.minZ, box.maxX, box.minY, box.maxZ);
		return !level.noCollision(entity, feet);
	}
}
