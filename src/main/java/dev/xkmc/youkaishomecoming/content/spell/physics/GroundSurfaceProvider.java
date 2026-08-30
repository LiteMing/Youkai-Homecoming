package dev.xkmc.youkaishomecoming.content.spell.physics;

import net.minecraft.world.phys.Vec3;

import java.util.OptionalDouble;

public interface GroundSurfaceProvider {

	/**
	 * Probe the highest ground surface Y coordinate under the given horizontal position.
	 *
	 * @param currentPos current position of the projectile
	 * @param nextPos predicted next position of the projectile
	 * @param stepHeight maximum step-up climbing height
	 * @return highest floor Y coordinate, or empty if no valid ground within range
	 */
	OptionalDouble findFloorHeight(Vec3 currentPos, Vec3 nextPos, double stepHeight);
}
