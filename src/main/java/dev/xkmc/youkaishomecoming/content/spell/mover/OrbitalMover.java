package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Orbital mover: projectile orbits around a fixed axis (typically baseDirection)
 * with a radius that can vary over time via a formula expression.
 * <p>
 * The orbit plane is perpendicular to the axis. The projectile's position at tick t is:
 * <pre>
 *   pos = center + drift*t + radius(t) * (normal*cos(theta) + side*sin(theta))
 * </pre>
 * where theta = initialAngle + angularSpeed * t.
 * <p>
 * The initial angle is derived from the projectile's initial velocity direction
 * projected onto the orbit plane, so each projectile in a CONE/RING pattern
 * naturally gets a different starting angle.
 */
@SerialClass
public final class OrbitalMover extends TargetPosMover {

	@SerialClass.SerialField
	private Vec3 center = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 axis = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 normal = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 side = Vec3.ZERO;
	@SerialClass.SerialField
	private double initialAngle;
	@SerialClass.SerialField
	private double angularSpeed; // radians per tick
	@SerialClass.SerialField
	private String radiusFormula = "1";
	@SerialClass.SerialField
	private String driftFormula = "0";
	@SerialClass.SerialField
	private Vec3 driftDir = Vec3.ZERO;

	private transient FormulaExpr.Evaluable radiusExpr;
	private transient FormulaExpr.Evaluable driftExpr;

	@Deprecated
	public OrbitalMover() {
	}

	/**
	 * @param center       orbit center (world coords, typically the on_expiry position)
	 * @param axis         orbit axis (unit vector, typically baseDirection)
	 * @param velocity     per-projectile initial velocity (used to compute initial angle)
	 * @param angularSpeed angular speed in degrees per tick
	 * @param radiusFormula formula expression for radius (can use 'tick')
	 * @param driftFormula  formula expression for drift distance along axis (can use 'tick', e.g. "3 * sin(tick * 0.05)")
	 */
	public OrbitalMover(Vec3 center, Vec3 axis, Vec3 velocity, double angularSpeed,
						String radiusFormula, String driftFormula) {
		this.center = center;
		this.axis = axis.normalize();
		this.angularSpeed = Math.toRadians(angularSpeed);
		this.radiusFormula = radiusFormula;
		this.driftFormula = driftFormula;
		this.driftDir = this.axis;

		// Build orthonormal basis for the orbit plane (perpendicular to axis)
		var ori = DanmakuHelper.getOrientation(this.axis);
		this.normal = ori.normal();
		this.side = ori.side();

		// Compute initial angle so each bullet in a RING/CONE pattern gets a unique orbit position.
		//
		// For RING pattern: velocity = side*sin(a) + axis*cos(a), where a is the ring angle.
		// The orbit plane is spanned by (normal, side). The velocity's projection onto this plane
		// is just side*sin(a), which degenerates (only ±side direction).
		//
		// Solution: use the FULL velocity direction to compute a unique azimuth angle around the axis.
		// We compute atan2 of velocity's components in a coordinate system where:
		//   - one axis is 'side' (orbit plane)
		//   - other axis is 'normal' (orbit plane)  
		//   - third axis is 'axis' (ignored for angle, but velocity has component here)
		//
		// For RING: velDir·side = sin(a), velDir·normal = 0, velDir·axis = cos(a)
		// Direct orbit plane projection gives only (0, sin(a)) → degenerate.
		//
		// Instead, we use the velocity's angle in the (axis, side) plane as a proxy:
		// initialAngle = atan2(velDir·side, velDir·normal)  — but normal component is 0 for RING.
		//
		// Final approach: compute the angle that the velocity makes when projected onto
		// ANY plane containing the axis. Use atan2(velDir·side, velDir·axis) to get a unique
		// angle per bullet, then map it to orbit position.
		// For RING: atan2(sin(a), cos(a)) = a. Perfect!
		Vec3 velDir = velocity.lengthSqr() > 1e-8 ? velocity.normalize() : this.normal;
		double compAlongSide = velDir.dot(this.side);
		double compAlongAxis = velDir.dot(this.axis);
		double compAlongNormal = velDir.dot(this.normal);

		// Compute a unique initial angle for each bullet based on its velocity direction.
		// We need an angle that varies per-bullet for any pattern (RING, CONE, SPHERE, etc.).
		//
		// For RING: velocity = side*sin(a) + axis*cos(a)
		//   → atan2(sin(a), cos(a)) = a ✓
		// For CONE (elevation=θ): velocity = axis*sin(θ) + (normal*cos(a) + side*sin(a))*cos(θ)
		//   → atan2(sin(a)*cos(θ), sin(θ)) — unique per bullet ✓
		// For SPHERE: velocity has all three components
		//   → atan2(side_comp, axis_comp) — unique per bullet ✓
		//
		// Using atan2(compAlongSide, compAlongAxis) works universally because:
		// - It uses the velocity's projection onto the (axis, side) plane
		// - For any pattern that distributes bullets around the axis, this gives unique angles
		// - Only degenerates when velocity is exactly along 'normal' (extremely rare)
		if (Math.abs(compAlongSide) > 1e-8 || Math.abs(compAlongAxis) > 1e-8) {
			this.initialAngle = Math.atan2(compAlongSide, compAlongAxis);
		} else {
			// Velocity is exactly along normal — use normal component vs side as fallback
			this.initialAngle = Math.atan2(compAlongSide, compAlongNormal);
		}
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		return pos(info.tick());
	}

	public Vec3 pos(double tick) {
		ensureCompiled();
		double radius = radiusExpr.eval(tick);
		double driftDist = driftExpr.eval(tick);
		double theta = initialAngle + angularSpeed * tick;
		Vec3 driftedCenter = center.add(driftDir.scale(driftDist));
		return driftedCenter.add(normal.scale(radius * Math.cos(theta)))
				.add(side.scale(radius * Math.sin(theta)));
	}

	private void ensureCompiled() {
		if (radiusExpr == null) {
			radiusExpr = FormulaExpr.parse(radiusFormula);
			if (radiusExpr == null) radiusExpr = t -> 1;
		}
		if (driftExpr == null) {
			driftExpr = FormulaExpr.parse(driftFormula);
			if (driftExpr == null) driftExpr = t -> 0;
		}
	}
}
