package dev.xkmc.youkaishomecoming.content.spell.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalDouble;

public class PreviewBoxGroundProvider implements GroundSurfaceProvider {

	private final AABB arenaBox;

	public PreviewBoxGroundProvider(AABB arenaBox) {
		this.arenaBox = arenaBox;
	}

	@Override
	public OptionalDouble findFloorHeight(Vec3 currentPos, Vec3 nextPos, double stepHeight) {
		if (arenaBox == null) return OptionalDouble.empty();
		// The bottom face of the arena box acts as the floor (inside coordinate Y = arenaBox.minY)
		if (nextPos.x >= arenaBox.minX && nextPos.x <= arenaBox.maxX &&
				nextPos.z >= arenaBox.minZ && nextPos.z <= arenaBox.maxZ) {
			return OptionalDouble.of(arenaBox.minY);
		}
		return OptionalDouble.empty();
	}
}
