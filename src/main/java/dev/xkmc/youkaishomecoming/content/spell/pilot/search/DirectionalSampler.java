package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Deterministic global and local direction samples. */
public final class DirectionalSampler {

	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

	private DirectionalSampler() {
	}

	public static List<Vec3> global(int count, boolean vertical) {
		int n = Math.max(1, count);
		List<Vec3> result = new ArrayList<>(n);
		if (!vertical) {
			for (int i = 0; i < n; i++) {
				double angle = Math.PI * 2.0 * i / n;
				result.add(new Vec3(Math.cos(angle), 0, Math.sin(angle)));
			}
			return result;
		}
		for (int i = 0; i < n; i++) {
			double y = 1.0 - 2.0 * (i + 0.5) / n;
			double radius = Math.sqrt(Math.max(0, 1.0 - y * y));
			double angle = GOLDEN_ANGLE * i;
			result.add(new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
		}
		return result;
	}

	/** Samples are ordered from the cone center outward, enabling safe early exit. */
	public static List<Vec3> localCone(Vec3 center, int count, double halfAngle, boolean vertical) {
		Vec3 axis = center.lengthSqr() < 1e-10 ? new Vec3(0, 0, 1) : center.normalize();
		int n = Math.max(1, count);
		List<Vec3> result = new ArrayList<>(n);
		result.add(axis);
		if (n == 1) return result;

		if (!vertical) {
			double base = Math.atan2(axis.z, axis.x);
			int sideSteps = Math.max(1, (n - 1 + 1) / 2);
			for (int i = 1; i < n; i++) {
				int ring = (i + 1) / 2;
				double sign = (i & 1) == 1 ? 1.0 : -1.0;
				double angle = base + sign * halfAngle * ring / sideSteps;
				result.add(new Vec3(Math.cos(angle), 0, Math.sin(angle)));
			}
			return result;
		}

		Vec3 helper = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 tangent = axis.cross(helper).normalize();
		Vec3 bitangent = axis.cross(tangent).normalize();
		for (int i = 1; i < n; i++) {
			double radius = halfAngle * Math.sqrt((double) i / (n - 1));
			double angle = GOLDEN_ANGLE * i;
			Vec3 radial = tangent.scale(Math.cos(angle)).add(bitangent.scale(Math.sin(angle)));
			result.add(axis.scale(Math.cos(radius)).add(radial.scale(Math.sin(radius))).normalize());
		}
		return result;
	}
}
