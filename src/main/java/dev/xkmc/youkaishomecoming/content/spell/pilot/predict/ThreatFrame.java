package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One predicted tick of a threat.
 * <ul>
 *   <li>Point danmaku: {@code length == 0}, {@code orientation} null, box half-extent = {@code hitRadius}</li>
 *   <li>Laser: segment from {@code position} along unit {@code orientation} of {@code length},
 *       capsule radius = {@code hitRadius}; {@code active} false during warn/fade windows</li>
 * </ul>
 */
public record ThreatFrame(Vec3 position, @Nullable Vec3 orientation, float hitRadius, float length, boolean active) {

	public ThreatFrame(Vec3 position, @Nullable Vec3 orientation, float hitRadius, boolean active) {
		this(position, orientation, hitRadius, 0f, active);
	}

	public boolean isLaser() {
		return length > 0f;
	}

	/** Axis-aligned bounds of the hit volume (sphere for danmaku, segment AABB for laser). */
	public AABB bounds() {
		double r = hitRadius;
		if (!isLaser() || orientation == null) {
			return new AABB(
					position.x - r, position.y - r, position.z - r,
					position.x + r, position.y + r, position.z + r
			);
		}
		Vec3 end = position.add(orientation.normalize().scale(length));
		return new AABB(position, end).inflate(r);
	}

}
