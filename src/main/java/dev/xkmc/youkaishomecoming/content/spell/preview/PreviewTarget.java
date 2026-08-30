package dev.xkmc.youkaishomecoming.content.spell.preview;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Temporary collision target used only by the spell preview scene. */
public final class PreviewTarget {

	public static final Vec3 DEFAULT_BOX_POS = new Vec3(0, -32, 0);
	public static final Vec3 DEFAULT_BOX_SIZE = new Vec3(64, 64, 64);
	private static Vec3 rememberedBoxPos = DEFAULT_BOX_POS;
	private static Vec3 rememberedBoxSize = DEFAULT_BOX_SIZE;

	public static Vec3 getRememberedBoxPos() { return rememberedBoxPos; }
	public static void rememberBoxPos(Vec3 pos) { rememberedBoxPos = pos == null ? DEFAULT_BOX_POS : pos; }
	public static Vec3 getRememberedBoxSize() { return rememberedBoxSize; }
	public static void rememberBoxSize(Vec3 size) { rememberedBoxSize = sanitizeSize(size); }
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

	public record SurfaceHit(Vec3 pos, Vec3 normal) {}

	/**
	 * Returns the first point and outward surface normal where a swept center line crosses one of the six box faces.
	 * Both entering from outside and exiting from inside count as block-target hits.
	 */
	public static Optional<SurfaceHit> firstSurfaceIntersectionWithNormal(AABB box, Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		double bestT = Double.POSITIVE_INFINITY;
		Vec3 bestNormal = Vec3.ZERO;

		double tMinX = testFace(box.minX, from.x, delta.x, from.y, delta.y, from.z, delta.z, box.minY, box.maxY, box.minZ, box.maxZ, bestT);
		if (tMinX < bestT) { bestT = tMinX; bestNormal = new Vec3(-1, 0, 0); }

		double tMaxX = testFace(box.maxX, from.x, delta.x, from.y, delta.y, from.z, delta.z, box.minY, box.maxY, box.minZ, box.maxZ, bestT);
		if (tMaxX < bestT) { bestT = tMaxX; bestNormal = new Vec3(1, 0, 0); }

		double tMinY = testFace(box.minY, from.y, delta.y, from.x, delta.x, from.z, delta.z, box.minX, box.maxX, box.minZ, box.maxZ, bestT);
		if (tMinY < bestT) { bestT = tMinY; bestNormal = new Vec3(0, -1, 0); }

		double tMaxY = testFace(box.maxY, from.y, delta.y, from.x, delta.x, from.z, delta.z, box.minX, box.maxX, box.minZ, box.maxZ, bestT);
		if (tMaxY < bestT) { bestT = tMaxY; bestNormal = new Vec3(0, 1, 0); }

		double tMinZ = testFace(box.minZ, from.z, delta.z, from.x, delta.x, from.y, delta.y, box.minX, box.maxX, box.minY, box.maxY, bestT);
		if (tMinZ < bestT) { bestT = tMinZ; bestNormal = new Vec3(0, 0, -1); }

		double tMaxZ = testFace(box.maxZ, from.z, delta.z, from.x, delta.x, from.y, delta.y, box.minX, box.maxX, box.minY, box.maxY, bestT);
		if (tMaxZ < bestT) { bestT = tMaxZ; bestNormal = new Vec3(0, 0, 1); }

		if (Double.isFinite(bestT)) {
			// If moving in the same direction as the outward normal (hitting face from inside), the collision normal faces inward
			if (delta.dot(bestNormal) > 0) {
				bestNormal = bestNormal.scale(-1);
			}
			return Optional.of(new SurfaceHit(from.add(delta.scale(bestT)), bestNormal));
		}
		return Optional.empty();
	}

	private static double testFace(double plane, double fromAxis, double deltaAxis,
								   double fromU, double deltaU, double fromV, double deltaV,
								   double minU, double maxU, double minV, double maxV, double bestT) {
		if (Math.abs(deltaAxis) <= EPSILON) return bestT;
		double t = (plane - fromAxis) / deltaAxis;
		if (!isCandidate(t, bestT)) return bestT;
		double u = fromU + deltaU * t;
		double v = fromV + deltaV * t;
		return within(u, minU, maxU) && within(v, minV, maxV) ? t : bestT;
	}

	/**
	 * Returns the first point where a swept center line crosses one of the six box faces.
	 * Both entering from outside and exiting from inside count as block-target hits.
	 */
	public static Optional<Vec3> firstSurfaceIntersection(AABB box, Vec3 from, Vec3 to) {
		return firstSurfaceIntersectionWithNormal(box, from, to).map(SurfaceHit::pos);
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
