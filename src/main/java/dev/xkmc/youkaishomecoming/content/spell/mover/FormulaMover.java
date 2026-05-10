package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

/**
 * Formula-driven mover: position at each tick is computed from mathematical expressions.
 * Each axis (x, y, z) has its own formula string that can reference 'tick'.
 *
 * The formulas define OFFSET from origin in the projectile's local coordinate system:
 *   x = forward (along initial velocity direction)
 *   y = right (perpendicular, horizontal)
 *   z = up (perpendicular, vertical)
 *
 * Supported in formulas: +, -, *, /, parentheses, sin(), cos(), abs(), sqrt(),
 * min(), max(), pow(), floor(), ceil(), pi, e, tick (or t).
 *
 * Example (spiral): x="tick * 0.3", y="3 * sin(tick * 0.15)", z="3 * cos(tick * 0.15)"
 * Example (figure-8): x="tick * 0.2", y="4 * sin(tick * 0.1)", z="2 * sin(tick * 0.2)"
 */
@SerialClass
public final class FormulaMover extends TargetPosMover {

	@SerialClass.SerialField
	private Vec3 origin = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 forward = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 right = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 up = Vec3.ZERO;

	// Formula strings (serialized for NBT persistence)
	@SerialClass.SerialField
	private String formulaX = "0";
	@SerialClass.SerialField
	private String formulaY = "0";
	@SerialClass.SerialField
	private String formulaZ = "0";

	// Compiled expressions (transient, rebuilt on first use)
	private transient FormulaExpr.Evaluable exprX;
	private transient FormulaExpr.Evaluable exprY;
	private transient FormulaExpr.Evaluable exprZ;

	@Deprecated
	public FormulaMover() {
	}

	/**
	 * @param origin   spawn position (absolute world coords)
	 * @param forward  unit vector along initial velocity direction
	 * @param right    unit vector perpendicular (horizontal)
	 * @param up       unit vector perpendicular (vertical)
	 * @param formulaX expression for forward offset
	 * @param formulaY expression for right offset
	 * @param formulaZ expression for up offset
	 */
	public FormulaMover(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
						String formulaX, String formulaY, String formulaZ) {
		this.origin = origin;
		this.forward = forward;
		this.right = right;
		this.up = up;
		this.formulaX = formulaX;
		this.formulaY = formulaY;
		this.formulaZ = formulaZ;
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		return pos(info.tick());
	}

	public Vec3 pos(double tick) {
		ensureCompiled();
		double fx = exprX.eval(tick);
		double fy = exprY.eval(tick);
		double fz = exprZ.eval(tick);
		return origin.add(forward.scale(fx)).add(right.scale(fy)).add(up.scale(fz));
	}

	private void ensureCompiled() {
		if (exprX == null) exprX = FormulaExpr.parse(formulaX);
		if (exprY == null) exprY = FormulaExpr.parse(formulaY);
		if (exprZ == null) exprZ = FormulaExpr.parse(formulaZ);
		if (exprX == null) exprX = t -> 0;
		if (exprY == null) exprY = t -> 0;
		if (exprZ == null) exprZ = t -> 0;
	}
}
