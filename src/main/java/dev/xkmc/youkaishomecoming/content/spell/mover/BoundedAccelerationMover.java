package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Axis-independent acceleration mover with optional per-axis terminal velocity limits.
 * Supports both World space (absolute world axes) and Local space (aligned with initial velocity).
 *
 * <p>Coordinate conventions:
 * <ul>
 *   <li><b>World space</b>: X = East(+)/West(-), Y = Up(+)/Down(-), Z = South(+)/North(-)</li>
 *   <li><b>Local space</b> (aligned with FormulaMover):
 *       X = Forward (along initial velocity),
 *       Y = Right (horizontal perpendicular),
 *       Z = Up (vertical perpendicular)</li>
 * </ul>
 *
 * <p>Each axis displacement follows closed-form piecewise acceleration equations:
 * <ul>
 *   <li>If no terminal velocity or a == 0: d(t) = v0*t + 0.5*a*t^2</li>
 *   <li>If a > 0 and v0 < termV: accelerates until t_reach = (termV - v0)/a, then flies at constant termV</li>
 *   <li>If a < 0 and v0 > termV: accelerates until t_reach = (termV - v0)/a, then flies at constant termV</li>
 *   <li>If v0 already meets/exceeds terminal velocity in acceleration direction: flies at constant v0</li>
 * </ul>
 */
@SerialClass
public final class BoundedAccelerationMover extends TargetPosMover implements CollisionRebasableMover {

	@SerialClass.SerialField
	private Vec3 origin = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 localVel0 = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 localAcc = Vec3.ZERO;

	@SerialClass.SerialField
	private boolean isLocalSpace = false;
	@SerialClass.SerialField
	private Vec3 forward = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 right = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 up = Vec3.ZERO;

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

	@SerialClass.SerialField
	private int startTick = 0;

	public BoundedAccelerationMover() {}

	/** World space constructor */
	public static BoundedAccelerationMover world(
			Vec3 origin,
			Vec3 velocity,
			Vec3 acc,
			@Nullable Double termVx,
			@Nullable Double termVy,
			@Nullable Double termVz
	) {
		BoundedAccelerationMover m = new BoundedAccelerationMover();
		m.origin = sanitize(origin);
		m.localVel0 = sanitize(velocity);
		m.localAcc = sanitize(acc);
		m.isLocalSpace = false;
		m.setupTerminals(termVx, termVy, termVz);
		return m;
	}

	/** Local space constructor (X=forward, Y=right, Z=up) */
	public static BoundedAccelerationMover local(
			Vec3 origin,
			Vec3 velocity,
			Vec3 forward,
			Vec3 right,
			Vec3 up,
			Vec3 localAcc,
			@Nullable Double termVx,
			@Nullable Double termVy,
			@Nullable Double termVz
	) {
		BoundedAccelerationMover m = new BoundedAccelerationMover();
		m.origin = sanitize(origin);
		m.forward = sanitize(forward);
		m.right = sanitize(right);
		m.up = sanitize(up);
		m.isLocalSpace = true;

		// Project initial velocity onto local basis axes:
		// x = forward, y = right, z = up
		double vForward = velocity.dot(m.forward);
		double vRight = velocity.dot(m.right);
		double vUp = velocity.dot(m.up);
		m.localVel0 = new Vec3(vForward, vRight, vUp);
		m.localAcc = sanitize(localAcc);

		m.setupTerminals(termVx, termVy, termVz);
		return m;
	}

	private void setupTerminals(@Nullable Double termVx, @Nullable Double termVy, @Nullable Double termVz) {
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

	private static Vec3 sanitize(Vec3 v) {
		if (v == null) return Vec3.ZERO;
		double x = Double.isFinite(v.x) ? v.x : 0;
		double y = Double.isFinite(v.y) ? v.y : 0;
		double z = Double.isFinite(v.z) ? v.z : 0;
		return new Vec3(x, y, z);
	}

	@Override
	public DanmakuMover rebaseAfterCollision(Vec3 newPosition, Vec3 newVelocity, int collisionTick) {
		Double tvx = hasTermX ? termVx : null;
		Double tvy = hasTermY ? termVy : null;
		Double tvz = hasTermZ ? termVz : null;

		BoundedAccelerationMover rebased;
		if (isLocalSpace) {
			// Local space rebase: keeps original basis, projects newVelocity onto existing basis
			rebased = local(newPosition, newVelocity, forward, right, up, localAcc, tvx, tvy, tvz);
		} else {
			// World space rebase: new origin and velocity, keeping world acceleration and terminals
			rebased = world(newPosition, newVelocity, localAcc, tvx, tvy, tvz);
		}
		rebased.startTick = Math.max(0, collisionTick);
		return rebased;
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		return pos(info.tick() - startTick);
	}

	public Vec3 pos(double tick) {
		if (tick <= 0) return origin;

		double dx = calcAxisDisplacement(localVel0.x, localAcc.x, hasTermX, termVx, tick);
		double dy = calcAxisDisplacement(localVel0.y, localAcc.y, hasTermY, termVy, tick);
		double dz = calcAxisDisplacement(localVel0.z, localAcc.z, hasTermZ, termVz, tick);

		if (isLocalSpace) {
			// X = forward, Y = right, Z = up
			return origin.add(forward.scale(dx)).add(right.scale(dy)).add(up.scale(dz));
		} else {
			return origin.add(dx, dy, dz);
		}
	}

	public Vec3 vel(double tick) {
		if (tick <= 0) {
			if (isLocalSpace) {
				return forward.scale(localVel0.x).add(right.scale(localVel0.y)).add(up.scale(localVel0.z));
			}
			return localVel0;
		}

		double vx = calcAxisVel(localVel0.x, localAcc.x, hasTermX, termVx, tick);
		double vy = calcAxisVel(localVel0.y, localAcc.y, hasTermY, termVy, tick);
		double vz = calcAxisVel(localVel0.z, localAcc.z, hasTermZ, termVz, tick);

		if (isLocalSpace) {
			return forward.scale(vx).add(right.scale(vy)).add(up.scale(vz));
		} else {
			return new Vec3(vx, vy, vz);
		}
	}

	private static double calcAxisDisplacement(double v0, double a, boolean hasTerm, double termV, double tick) {
		if (Math.abs(a) < 1e-9 || !hasTerm) {
			return v0 * tick + 0.5 * a * tick * tick;
		}

		if (a > 0) {
			if (v0 >= termV) {
				// Already at or above terminal velocity: no further acceleration
				return v0 * tick;
			}
			double tReach = (termV - v0) / a;
			if (tick <= tReach) {
				return v0 * tick + 0.5 * a * tick * tick;
			} else {
				double distAcc = v0 * tReach + 0.5 * a * tReach * tReach;
				double distConst = termV * (tick - tReach);
				return distAcc + distConst;
			}
		} else { // a < 0
			if (v0 <= termV) {
				// Already at or below terminal velocity: no further acceleration
				return v0 * tick;
			}
			double tReach = (termV - v0) / a;
			if (tick <= tReach) {
				return v0 * tick + 0.5 * a * tick * tick;
			} else {
				double distAcc = v0 * tReach + 0.5 * a * tReach * tReach;
				double distConst = termV * (tick - tReach);
				return distAcc + distConst;
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
