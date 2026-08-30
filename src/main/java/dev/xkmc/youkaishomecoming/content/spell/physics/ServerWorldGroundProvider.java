package dev.xkmc.youkaishomecoming.content.spell.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalDouble;

public class ServerWorldGroundProvider implements GroundSurfaceProvider {

	private final Level level;

	public ServerWorldGroundProvider(Level level) {
		this.level = level;
	}

	@Override
	public OptionalDouble findFloorHeight(Vec3 currentPos, Vec3 nextPos, double stepHeight) {
		double probeX = nextPos.x;
		double probeZ = nextPos.z;
		BlockPos targetBlockPos = BlockPos.containing(probeX, currentPos.y, probeZ);

		for (int dy = (int) Math.ceil(stepHeight); dy >= -2; dy--) {
			BlockPos checkPos = targetBlockPos.offset(0, dy, 0);
			var state = level.getBlockState(checkPos);
			if (!state.isAir()) {
				var shape = state.getCollisionShape(level, checkPos);
				if (!shape.isEmpty()) {
					double localX = probeX - checkPos.getX();
					double localZ = probeZ - checkPos.getZ();
					double highestBoxY = Double.NEGATIVE_INFINITY;
					for (var aabb : shape.toAabbs()) {
						if (localX >= aabb.minX - 0.05 && localX <= aabb.maxX + 0.05 &&
								localZ >= aabb.minZ - 0.05 && localZ <= aabb.maxZ + 0.05) {
							highestBoxY = Math.max(highestBoxY, checkPos.getY() + aabb.maxY);
						}
					}
					if (highestBoxY > Double.NEGATIVE_INFINITY && highestBoxY <= currentPos.y + stepHeight + 0.1) {
						return OptionalDouble.of(highestBoxY);
					}
				}
			}
		}
		return OptionalDouble.empty();
	}
}
