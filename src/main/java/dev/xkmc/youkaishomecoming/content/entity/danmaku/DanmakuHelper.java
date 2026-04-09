package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import net.minecraft.world.phys.Vec3;

public class DanmakuHelper {

	private static final Vec3 DEFAULT_FORWARD = new Vec3(0, 0, 1);

	public record Orientation(Vec3 forward, Vec3 normal, Vec3 side) {

		public Vec3 rotateDegrees(double rad) {
			return rotate(rad / 180 * Math.PI);
		}

		public Vec3 rotateDegrees(double rad, double ver) {
			return rotate(rad / 180 * Math.PI, ver / 180 * Math.PI);
		}

		public Vec3 rotate(double rad) {
			return side.scale(Math.sin(rad)).add(forward.scale(Math.cos(rad)));
		}

		public Vec3 rotate(double rad, double ver) {
			return side.scale(Math.sin(rad) * Math.cos(ver))
					.add(forward.scale(Math.cos(rad) * Math.cos(ver)))
					.add(normal.scale(Math.sin(ver)));
		}

		public Orientation asNormal() {
			return new Orientation(normal, forward, side);
		}

	}

	public static boolean isFinite(Vec3 vec) {
		return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
	}

	public static Vec3 safeDirection(Vec3 preferred, Vec3 fallback) {
		Vec3 safeFallback = normalizeOrFallback(fallback, DEFAULT_FORWARD);
		return normalizeOrFallback(preferred, safeFallback);
	}

	public static Vec3 safeHorizontalDirection(Vec3 preferred, Vec3 fallback) {
		Vec3 horizontalFallback = new Vec3(fallback.x, 0, fallback.z);
		Vec3 safeFallback = normalizeOrFallback(horizontalFallback, DEFAULT_FORWARD);
		Vec3 horizontalPreferred = new Vec3(preferred.x, 0, preferred.z);
		return normalizeOrFallback(horizontalPreferred, safeFallback);
	}

	public static Vec3 safeVelocity(Vec3 velocity, Vec3 fallbackDirection) {
		if (!isFinite(velocity)) {
			return Vec3.ZERO;
		}
		double speedSqr = velocity.lengthSqr();
		if (!Double.isFinite(speedSqr) || speedSqr <= 1e-8) {
			return Vec3.ZERO;
		}
		return safeDirection(velocity, fallbackDirection).scale(Math.sqrt(speedSqr));
	}

	private static Vec3 normalizeOrFallback(Vec3 vec, Vec3 fallback) {
		if (!isFinite(vec) || vec.lengthSqr() <= 1e-8) {
			return fallback;
		}
		Vec3 normalized = vec.normalize();
		return isFinite(normalized) && normalized.lengthSqr() > 1e-8 ? normalized : fallback;
	}

	public static Orientation getOrientation(Vec3 dir) {
		dir = safeDirection(dir, DEFAULT_FORWARD);
		double val = (dir.x * dir.x + dir.z * dir.z);
		Vec3 ax0 = val < 1e-4 ? new Vec3(1, 0, 0) :
				new Vec3(-dir.x * dir.y, val, -dir.z * dir.y).normalize();
		Vec3 ax1 = dir.cross(ax0).normalize();
		return new Orientation(dir, ax0, ax1);
	}

	public static Orientation getOrientation(Vec3 dir, Vec3 ax0) {
		dir = safeDirection(dir, DEFAULT_FORWARD);
		ax0 = safeDirection(ax0, new Vec3(1, 0, 0));
		Vec3 ax1 = dir.cross(ax0).normalize();
		if (!isFinite(ax1) || ax1.lengthSqr() <= 1e-8) {
			return getOrientation(dir);
		}
		return new Orientation(dir, ax0, ax1);
	}

}
