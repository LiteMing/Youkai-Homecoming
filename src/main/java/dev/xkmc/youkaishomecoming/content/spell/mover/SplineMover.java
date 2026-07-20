package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Catmull-Rom spline mover. The projectile smoothly passes through all waypoints
 * in order over the specified duration. Uses centripetal Catmull-Rom interpolation
 * for natural-looking curves without cusps.
 *
 * If closed=true, the path loops back to the first waypoint (useful for circular orbits).
 * After completing the path, the projectile continues in a straight line at the final velocity.
 */
@SerialClass
public final class SplineMover extends TargetPosMover {

	@SerialClass.SerialField
	private final ArrayList<Vec3> points = new ArrayList<>();

	@SerialClass.SerialField
	private int duration = 60;

	@SerialClass.SerialField
	private boolean closed = false;

	@Deprecated
	public SplineMover() {
	}

	/**
	 * @param points   absolute world positions the spline passes through
	 * @param duration total ticks to traverse the entire spline
	 * @param closed   if true, the spline loops (last point connects back to first)
	 */
	public SplineMover(List<Vec3> points, int duration, boolean closed) {
		this.points.addAll(points);
		this.duration = Math.max(1, duration);
		this.closed = closed;
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		return pos(info.tick());
	}

	public Vec3 pos(double tick) {
		if (points.size() < 2) {
			return points.isEmpty() ? Vec3.ZERO : points.get(0);
		}

		int segmentCount = closed ? points.size() : points.size() - 1;
		double totalT = tick / duration; // 0..1 over the full path

		if (!closed && totalT >= 1.0) {
			// Past the end: continue linearly
			Vec3 endPos = evalSpline(1.0);
			Vec3 endVel = evalSplineDerivative(1.0);
			return endPos.add(endVel.scale((tick - duration)));
		}

		if (closed) {
			// Loop: wrap t
			totalT = totalT % 1.0;
			if (totalT < 0) totalT += 1.0;
		}

		return evalSpline(totalT);
	}

	/**
	 * Evaluate the Catmull-Rom spline at parameter t (0..1 over the full path).
	 */
	private Vec3 evalSpline(double t) {
		int segmentCount = closed ? points.size() : points.size() - 1;
		if (segmentCount <= 0) {
			return points.isEmpty() ? Vec3.ZERO : points.get(0);
		}
		// Exact endpoint: avoid (int)1.0 → seg overflow clamping back to segment start
		if (!closed && t >= 1.0) {
			return points.get(points.size() - 1);
		}
		if (t <= 0) {
			return points.get(0);
		}
		double scaledT = t * segmentCount;
		int seg = (int) scaledT;
		double localT = scaledT - seg;

		if (!closed) {
			if (seg >= segmentCount) {
				return points.get(points.size() - 1);
			}
			localT = Math.min(localT, 1.0);
		} else {
			seg = seg % segmentCount;
		}

		// Get the 4 control points for this segment (p0, p1, p2, p3)
		Vec3 p0 = getPoint(seg - 1);
		Vec3 p1 = getPoint(seg);
		Vec3 p2 = getPoint(seg + 1);
		Vec3 p3 = getPoint(seg + 2);

		return catmullRom(p0, p1, p2, p3, localT);
	}

	/**
	 * Evaluate the derivative of the spline at parameter t.
	 */
	private Vec3 evalSplineDerivative(double t) {
		double dt = 0.001;
		Vec3 a = evalSpline(Math.max(0, t - dt));
		Vec3 b = evalSpline(Math.min(1, t + dt));
		// Scale to per-tick velocity
		return b.subtract(a).scale(0.5 / dt / duration);
	}

	/**
	 * Get a point by index, handling wrapping for closed splines
	 * and clamping for open splines.
	 */
	private Vec3 getPoint(int index) {
		int n = points.size();
		if (closed) {
			return points.get(((index % n) + n) % n);
		} else {
			return points.get(Math.max(0, Math.min(index, n - 1)));
		}
	}

	/**
	 * Standard Catmull-Rom interpolation between p1 and p2.
	 * p0 and p3 are the neighboring points for tangent calculation.
	 */
	private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
		double t2 = t * t;
		double t3 = t2 * t;

		// Catmull-Rom matrix coefficients (uniform, alpha=0.5)
		double c0 = -0.5 * t3 + t2 - 0.5 * t;
		double c1 = 1.5 * t3 - 2.5 * t2 + 1.0;
		double c2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
		double c3 = 0.5 * t3 - 0.5 * t2;

		return p0.scale(c0).add(p1.scale(c1)).add(p2.scale(c2)).add(p3.scale(c3));
	}
}
