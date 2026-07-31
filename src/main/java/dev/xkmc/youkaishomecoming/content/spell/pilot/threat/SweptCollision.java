package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * True continuous swept AABB collision (slab method).
 * Relative motion → ray vs Minkowski-sum box; one solve, no 8-step sampling.
 * Slightly more conservative than {@code ProjectileHitHelper.checkHit} (by design).
 */
public final class SweptCollision {

	private SweptCollision() {
	}

	/**
	 * @return earliest hit time t ∈ [0, 1], or -1 if miss
	 */
	public static double sweptHit(AABB self, Vec3 selfDelta, AABB threat, Vec3 threatDelta) {
		// Relative: treat self as static; threat moves by threatDelta - selfDelta
		Vec3 rel = threatDelta.subtract(selfDelta);
		double hx = (threat.maxX - threat.minX) * 0.5;
		double hy = (threat.maxY - threat.minY) * 0.5;
		double hz = (threat.maxZ - threat.minZ) * 0.5;
		AABB expanded = new AABB(
				self.minX - hx, self.minY - hy, self.minZ - hz,
				self.maxX + hx, self.maxY + hy, self.maxZ + hz
		);
		return rayAabb(center(threat), rel, expanded);
	}

	/**
	 * Laser segment vs moving self: AABB of capsule + continuous sweep.
	 *
	 * @return t ∈ [0,1] or -1
	 */
	public static double sweptLaser(AABB self, Vec3 selfDelta,
	                                Vec3 origin, Vec3 dir, float length, float radius,
	                                Vec3 originDelta) {
		Vec3 unit = dir.lengthSqr() < 1e-12 ? new Vec3(0, 0, 1) : dir.normalize();
		Vec3 end = origin.add(unit.scale(length));
		AABB laserBox = new AABB(origin, end).inflate(radius);
		return sweptHit(self, selfDelta, laserBox, originDelta);
	}

	/**
	 * Signed clearance between two AABBs.
	 * Positive = separated (margin on the separating axis), negative = penetrating.
	 */
	public static double clearance(AABB a, AABB b) {
		double gx = gap1D(a.minX, a.maxX, b.minX, b.maxX);
		double gy = gap1D(a.minY, a.maxY, b.minY, b.maxY);
		double gz = gap1D(a.minZ, a.maxZ, b.minZ, b.maxZ);
		if (gx > 0 || gy > 0 || gz > 0) {
			double sep = Double.NEGATIVE_INFINITY;
			if (gx > 0) sep = Math.max(sep, gx);
			if (gy > 0) sep = Math.max(sep, gy);
			if (gz > 0) sep = Math.max(sep, gz);
			return sep;
		}
		return Math.min(gx, Math.min(gy, gz));
	}

	/** Per-axis gap: positive if intervals disjoint, negative if overlapping. */
	public static double gap1D(double a0, double a1, double b0, double b1) {
		if (a1 < b0) return b0 - a1;
		if (b1 < a0) return a0 - b1;
		return -Math.min(a1 - b0, b1 - a0);
	}

	public static Vec3 center(AABB box) {
		return new Vec3(
				(box.minX + box.maxX) * 0.5,
				(box.minY + box.maxY) * 0.5,
				(box.minZ + box.maxZ) * 0.5
		);
	}

	/**
	 * Ray from {@code origin} with displacement {@code delta} vs AABB.
	 *
	 * @return t ∈ [0,1] of first hit, -1 miss. Origin inside → 0.
	 */
	public static double rayAabb(Vec3 origin, Vec3 delta, AABB box) {
		if (box.contains(origin)) return 0;

		double tEnter = 0;
		double tExit = 1;

		for (int axis = 0; axis < 3; axis++) {
			double o = axis == 0 ? origin.x : axis == 1 ? origin.y : origin.z;
			double d = axis == 0 ? delta.x : axis == 1 ? delta.y : delta.z;
			double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
			double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;

			if (Math.abs(d) < 1e-12) {
				if (o < min || o > max) return -1;
				continue;
			}
			double inv = 1.0 / d;
			double t1 = (min - o) * inv;
			double t2 = (max - o) * inv;
			if (t1 > t2) {
				double tmp = t1;
				t1 = t2;
				t2 = tmp;
			}
			tEnter = Math.max(tEnter, t1);
			tExit = Math.min(tExit, t2);
			if (tEnter > tExit) return -1;
		}
		return tEnter <= 1 && tExit >= 0 ? tEnter : -1;
	}

	/**
	 * Discrete 8-step sampler matching ProjectileHitHelper (tests: show miss on fast diagonal).
	 * {@code threatSrc→threatDst} is the projectile segment; self moves by {@code selfVel}.
	 */
	public static boolean discreteSampleHit(AABB self, Vec3 selfVel, Vec3 threatSrc, Vec3 threatDst) {
		double speed = selfVel.length();
		int n = (int) Math.min(8, Math.floor(speed / 0.5));
		for (int i = 0; i <= n; i++) {
			AABB aabb = n == 0 ? self : self.move(selfVel.scale(1d * i / n));
			if (aabb.contains(threatSrc)) return true;
			if (aabb.clip(threatSrc, threatDst).isPresent()) return true;
		}
		return false;
	}
}
