package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

/**
 * Cubic Bezier curve mover. The projectile follows a cubic Bezier path
 * from P0 to P3 over {duration} ticks, then continues in a straight line
 * at the final velocity.
 * <p>
 * B(t) = (1-t)^3 * P0 + 3*(1-t)^2*t * P1 + 3*(1-t)*t^2 * P2 + t^3 * P3
 * <p>
 * P0 = origin (spawn position)
 * P1 = origin + control1 (first control point, relative to origin)
 * P2 = origin + control2 (second control point, relative to origin)
 * P3 = origin + endpoint  (final position, relative to origin)
 */
@SerialClass
public final class BezierMover extends TargetPosMover {

	@SerialClass.SerialField
	private Vec3 p0 = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 p1 = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 p2 = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 p3 = Vec3.ZERO;
	@SerialClass.SerialField
	private int duration = 40;

	@Deprecated
	public BezierMover() {
	}

	/**
	 * @param p0       start position (absolute)
	 * @param p1       first control point (absolute)
	 * @param p2       second control point (absolute)
	 * @param p3       end position (absolute)
	 * @param duration ticks to traverse the curve
	 */
	public BezierMover(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int duration) {
		this.p0 = p0;
		this.p1 = p1;
		this.p2 = p2;
		this.p3 = p3;
		this.duration = Math.max(1, duration);
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		return pos(info.tick());
	}

	@Override
	public boolean allowNextTickStep1Prefetch() {
		return true;
	}

	/**
	 * Evaluate position on the Bezier curve at the given tick.
	 * After {duration} ticks, the projectile continues in a straight line
	 * at the velocity it had at t=1.
	 */
	public Vec3 pos(double tick) {
		if (tick >= duration) {
			// Continue linearly past the curve endpoint
			Vec3 endPos = bezier(1.0);
			Vec3 endVel = bezierDerivative(1.0);
			return endPos.add(endVel.scale(tick - duration));
		}
		double t = tick / duration;
		return bezier(t);
	}

	/**
	 * Cubic Bezier: B(t) = (1-t)^3*P0 + 3*(1-t)^2*t*P1 + 3*(1-t)*t^2*P2 + t^3*P3
	 */
	private Vec3 bezier(double t) {
		double u = 1 - t;
		double u2 = u * u;
		double u3 = u2 * u;
		double t2 = t * t;
		double t3 = t2 * t;
		return p0.scale(u3)
				.add(p1.scale(3 * u2 * t))
				.add(p2.scale(3 * u * t2))
				.add(p3.scale(t3));
	}

	/**
	 * Derivative of the cubic Bezier (velocity in parameter space).
	 * B'(t) = 3*(1-t)^2*(P1-P0) + 6*(1-t)*t*(P2-P1) + 3*t^2*(P3-P2)
	 * Divided by duration to convert to world-space velocity per tick.
	 */
	private Vec3 bezierDerivative(double t) {
		double u = 1 - t;
		Vec3 d = p1.subtract(p0).scale(3 * u * u)
				.add(p2.subtract(p1).scale(6 * u * t))
				.add(p3.subtract(p2).scale(3 * t * t));
		return d.scale(1.0 / duration);
	}
}
