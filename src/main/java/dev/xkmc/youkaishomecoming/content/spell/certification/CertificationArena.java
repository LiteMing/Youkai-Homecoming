package dev.xkmc.youkaishomecoming.content.spell.certification;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Certification arena (design doc §9): fixed AABB centered at the PREPARE start
 * position; the center never follows the enemy or player.
 */
public record CertificationArena(Vec3 center, double halfSize) {

	public boolean contains(Vec3 pos) {
		return pos.x >= center.x - halfSize && pos.x <= center.x + halfSize
				&& pos.y >= center.y - halfSize && pos.y <= center.y + halfSize
				&& pos.z >= center.z - halfSize && pos.z <= center.z + halfSize;
	}

	public Vec3 randomPoint(net.minecraft.util.RandomSource random, double margin) {
		double range = halfSize - margin;
		return center.add(
				(random.nextDouble() * 2 - 1) * range,
				(random.nextDouble() * 2 - 1) * range,
				(random.nextDouble() * 2 - 1) * range);
	}

	public BlockPos centerBlock() {
		return BlockPos.containing(center);
	}
}
