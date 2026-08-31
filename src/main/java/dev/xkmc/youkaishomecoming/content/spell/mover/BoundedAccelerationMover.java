package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Axis-independent acceleration mover with optional terminal velocity on each axis.
 * When a specific axis reaches its terminal velocity, acceleration on that axis stops,
 * while other axes continue accelerating independently.
 *
 * <p>Position on each axis follows a closed-form piecewise linear/quadratic equation:
 * <ul>
 *   <li>If no terminal velocity or a == 0: pos(t) = pos0 + v0*t + 0.5*a*t^2</li>
 *   <li>If a > 0 and v0 < termV: accelerates until t_reach = (termV - v0)/a, then flies at constant termV</li>
 *   <li>If a < 0 and v0 > termV: accelerates until t_reach = (termV - v0)/a, then flies at constant termV</li>
 *   <li>If v0 already exceeds/meets terminal velocity in acceleration direction: flies at constant v0</li>
 * </ul>
 */
@SerialClass
public final class BoundedAccelerationMover extends TargetPosMover {

	@SerialClass.SerialField
	private Vec3 origin = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 initialVel = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 acc = Vec3.ZERO;

	@SerialClass.SerialField
	private boolean hasTermX = false;
	@SerialClass.SerialField
	private double termVx = 0;

	@SerialClass.SerialField
	private boolean hasTermY = false;
	@SerialClass.SerialField
	private double termVy = 0;

	@SerialClass.SerialField
	private boolean hasTermZ = false;
	@SerialClass.SerialField
	private double termVz = 0;

	public BoundedAccelerationMover() {}

	public BoundedAccelerationMover(
			Vec3 origin,
			Vec3 initialVel,
			Vec3 acc,
			@Nullable Double termVx,
			@Nullable Double termVy,
			@Nullable Double termVz
	) {
		this.origin = origin;
		this.initialVel = initialVel;
		this.acc = acc;
		if (termVx != null && Double.isFinite(termVx)) {
			this.hasTermX = true;
			this.termVx = termVx;
		}
		if (termVy != null && Double.isFinite(termVy)) {
			this.hasTermY = true;
			this.termVy = termVy;
		}
		if (termVz != null && Double.isFinite(termVz)) {
			this.hasTermZ = true;
			this.termVz = termVz;
		}
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		return pos(info.tick());
	}

	public Vec3 pos(double tick) {
		if (tick <= 0) return origin;

		double x = calcAxisPos(origin.x, initialVel.x, acc.x, hasTermX, termVx, tick);
		double y = calcAxisPos(origin.y, initialVel.y, acc.y, hasTermY, termVy, tick);
		double z = calcAxisPos(origin.z, initialVel.z, acc.z, hasTermZ, termVz, tick);

		return new Vec3(x, y, z);
	}

	public Vec3 vel(double tick) {
		if (tick <= 0) return initialVel;

		double vx = calcAxisVel(initialVel.x, acc.x, hasTermX, termVx, tick);
		double vy = calcAxisVel(initialVel.y, acc.y, hasTermY, termVy, tick);
		double vz = calcAxisVel(initialVel.z, acc.z, hasTermZ, termVz, tick);

		return new Vec3(vx, vy, vz);
	}

	private static double calcAxisPos(double pos0, double v0, double a, boolean hasTerm, double termV, double tick) {
		if (Math.abs(a) < 1e-9 || !hasTerm) {
			return pos0 + v0 * tick + 0.5 * a * tick * tick;
		}

		if (a > 0) {
			if (v0 >= termV) {
				// Already at or above terminal velocity: no further acceleration
				return pos0 + v0 * tick;
			}
			double tReach = (termV - v0) / a;
			if (tick <= tReach) {
				return pos0 + v0 * tick + 0.5 * a * tick * tick;
			} else {
				double distAcc = v0 * tReach + 0.5 * a * tReach * tReach;
				double distConst = termV * (tick - tReach);
				return pos0 + distAcc + distConst;
			}
		} else { // a < 0
			if (v0 <= termV) {
				// Already at or below terminal velocity: no further acceleration
				return pos0 + v0 * tick;
			}
			double tReach = (termV - v0) / a;
			if (tick <= tReach) {
				return pos0 + v0 * tick + 0.5 * a * tick * tick;
			} else {
				double distAcc = v0 * tReach + 0.5 * a * tReach * tReach;
				double distConst = termV * (tick - tReach);
				return pos0 + distAcc + distConst;
			}
		}
	}

	private static double calcAxisVel(double v0, double a, boolean hasTerm, double termV, double tick) {
		if (Math.abs(a) < 1e-9 || !hasTerm) {
			return v0 + a * tick;
		}

		if (a > 0) {
			if (v0 >= termV) return v0;
			double tReach = (termV - v0) / a;
			return tick <= tReach ? v0 + a * tick : termV;
		} else {
			if (v0 <= termV) return v0;
			double tReach = (termV - v0) / a;
			return tick <= tReach ? v0 + a * tick : termV;
		}
	}
}
