package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Temporary collision target used only by the spell preview scene. */
public final class PreviewTarget {

	public static final Vec3 DEFAULT_BOX_SIZE = new Vec3(1, 1, 1);
	private static final double MIN_SIZE = 0.05;
	private static final double MAX_SIZE = 128;
	private static final double EPSILON = 1.0e-9;

	public enum HitType {
		ENTITY,
		BLOCK
	}

	private PreviewTarget() {
	}

	public static Vec3 sanitizeSize(Vec3 size) {
		if (size == null) return DEFAULT_BOX_SIZE;
		return new Vec3(sanitizeDimension(size.x), sanitizeDimension(size.y), sanitizeDimension(size.z));
	}

	public static AABB boxAt(Vec3 bottomCenter, Vec3 requestedSize) {
		Vec3 size = sanitizeSize(requestedSize);
		double halfX = size.x * 0.5;
		double halfZ = size.z * 0.5;
		return new AABB(
				bottomCenter.x - halfX, bottomCenter.y, bottomCenter.z - halfZ,
				bottomCenter.x + halfX, bottomCenter.y + size.y, bottomCenter.z + halfZ
		);
	}

	/**
	 * Returns the first point where a swept center line crosses one of the six box faces.
	 * Both entering from outside and exiting from inside count as block-target hits.
	 */
	public static Optional<Vec3> firstSurfaceIntersection(AABB box, Vec3 from, Vec3 to) {
		if (isOnSurface(box, from)) return Optional.of(from);
		Vec3 delta = to.subtract(from);
		double bestT = Double.POSITIVE_INFINITY;

		bestT = testXFace(box.minX, box, from, delta, bestT);
		bestT = testXFace(box.maxX, box, from, delta, bestT);
		bestT = testYFace(box.minY, box, from, delta, bestT);
		bestT = testYFace(box.maxY, box, from, delta, bestT);
		bestT = testZFace(box.minZ, box, from, delta, bestT);
		bestT = testZFace(box.maxZ, box, from, delta, bestT);

		return Double.isFinite(bestT) ? Optional.of(from.add(delta.scale(bestT))) : Optional.empty();
	}

	/** Returns the first point where the segment enters or starts inside the box volume. */
	public static Optional<Vec3> firstVolumeIntersection(AABB box, Vec3 from, Vec3 to) {
		return box.contains(from) ? Optional.of(from) : box.clip(from, to);
	}

	private static double testXFace(double plane, AABB box, Vec3 from, Vec3 delta, double bestT) {
		if (Math.abs(delta.x) <= EPSILON) return bestT;
		double t = (plane - from.x) / delta.x;
		if (!isCandidate(t, bestT)) return bestT;
		double y = from.y + delta.y * t;
		double z = from.z + delta.z * t;
		return within(y, box.minY, box.maxY) && within(z, box.minZ, box.maxZ) ? t : bestT;
	}

	private static double testYFace(double plane, AABB box, Vec3 from, Vec3 delta, double bestT) {
		if (Math.abs(delta.y) <= EPSILON) return bestT;
		double t = (plane - from.y) / delta.y;
		if (!isCandidate(t, bestT)) return bestT;
		double x = from.x + delta.x * t;
		double z = from.z + delta.z * t;
		return within(x, box.minX, box.maxX) && within(z, box.minZ, box.maxZ) ? t : bestT;
	}

	private static double testZFace(double plane, AABB box, Vec3 from, Vec3 delta, double bestT) {
		if (Math.abs(delta.z) <= EPSILON) return bestT;
		double t = (plane - from.z) / delta.z;
		if (!isCandidate(t, bestT)) return bestT;
		double x = from.x + delta.x * t;
		double y = from.y + delta.y * t;
		return within(x, box.minX, box.maxX) && within(y, box.minY, box.maxY) ? t : bestT;
	}

	private static boolean isCandidate(double t, double bestT) {
		return t >= -EPSILON && t <= 1 + EPSILON && t < bestT;
	}

	private static boolean within(double value, double min, double max) {
		return value >= min - EPSILON && value <= max + EPSILON;
	}

	private static boolean isOnSurface(AABB box, Vec3 point) {
		boolean withinX = within(point.x, box.minX, box.maxX);
		boolean withinY = within(point.y, box.minY, box.maxY);
		boolean withinZ = within(point.z, box.minZ, box.maxZ);
		return withinY && withinZ && (near(point.x, box.minX) || near(point.x, box.maxX))
				|| withinX && withinZ && (near(point.y, box.minY) || near(point.y, box.maxY))
				|| withinX && withinY && (near(point.z, box.minZ) || near(point.z, box.maxZ));
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) <= EPSILON;
	}

	private static double sanitizeDimension(double value) {
		if (!Double.isFinite(value)) return 1;
		return Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
	}
}
