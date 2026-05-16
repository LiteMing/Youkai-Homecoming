package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

/**
 * Translate mover: applies a world-coordinate offset defined by formula expressions.
 * <p>
 * Unlike {@link FormulaMover} which uses a per-projectile local coordinate system (forward/right/up),
 * this mover operates in absolute world coordinates (X = east, Y = up, Z = south).
 * This makes it ideal for layered use — add a translate layer to shift the entire pattern
 * in world space regardless of individual bullet directions.
 * <p>
 * Supports extended variables for aiming (snapshotted at creation time):
 * <ul>
 *   <li>{@code targetX}, {@code targetY}, {@code targetZ} (or {@code tx}, {@code ty}, {@code tz}) — target entity position at spawn</li>
 *   <li>{@code casterX}, {@code casterY}, {@code casterZ} (or {@code cx}, {@code cy}, {@code cz}) — caster/owner position at spawn</li>
 *   <li>{@code originX}, {@code originY}, {@code originZ} (or {@code ox}, {@code oy}, {@code oz}) — bullet spawn position</li>
 *   <li>{@code tick} (or {@code t}) — current tick</li>
 * </ul>
 * <p>
 * Example (aim toward target at constant speed):
 * <pre>
 *   x = "(targetX - originX) * tick * 0.02"
 *   y = "(targetY - originY) * tick * 0.02"
 *   z = "(targetZ - originZ) * tick * 0.02"
 * </pre>
 */
@SerialClass
public final class TranslateMover extends TargetPosMover {

	@SerialClass.SerialField
	private Vec3 origin = Vec3.ZERO;
	@SerialClass.SerialField
	private String formulaX = "0";
	@SerialClass.SerialField
	private String formulaY = "0";
	@SerialClass.SerialField
	private String formulaZ = "0";

	// Snapshotted positions at creation time
	@SerialClass.SerialField
	private Vec3 targetPos = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 casterPos = Vec3.ZERO;

	private transient FormulaExpr.RichEvaluable exprX;
	private transient FormulaExpr.RichEvaluable exprY;
	private transient FormulaExpr.RichEvaluable exprZ;

	@Deprecated
	public TranslateMover() {
	}

	public TranslateMover(Vec3 origin, String formulaX, String formulaY, String formulaZ,
						  Vec3 targetPos, Vec3 casterPos) {
		this.origin = origin;
		this.formulaX = formulaX;
		this.formulaY = formulaY;
		this.formulaZ = formulaZ;
		this.targetPos = targetPos;
		this.casterPos = casterPos;
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		ensureCompiled();
		double tick = info.tick();

		// Use snapshotted positions (captured at creation time)
		double tx = targetPos.x, ty = targetPos.y, tz = targetPos.z;
		double cx = casterPos.x, cy = casterPos.y, cz = casterPos.z;
		double ox = origin.x, oy = origin.y, oz = origin.z;

		// If owner position is available at runtime, use it for caster (live tracking)
		if (info.ownerInfo() != null && info.ownerInfo().ownerPos() != null) {
			Vec3 ownerPos = info.ownerInfo().ownerPos();
			cx = ownerPos.x;
			cy = ownerPos.y;
			cz = ownerPos.z;
		}

		double x = exprX.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		double y = exprY.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		double z = exprZ.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		return origin.add(x, y, z);
	}

	/** Fallback for pos(double tick) — uses snapshotted positions only. */
	public Vec3 pos(double tick) {
		ensureCompiled();
		double tx = targetPos.x, ty = targetPos.y, tz = targetPos.z;
		double cx = casterPos.x, cy = casterPos.y, cz = casterPos.z;
		double ox = origin.x, oy = origin.y, oz = origin.z;
		double x = exprX.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		double y = exprY.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		double z = exprZ.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		return origin.add(x, y, z);
	}

	private void ensureCompiled() {
		if (exprX == null) exprX = FormulaExpr.parseRich(formulaX);
		if (exprY == null) exprY = FormulaExpr.parseRich(formulaY);
		if (exprZ == null) exprZ = FormulaExpr.parseRich(formulaZ);
		if (exprX == null) exprX = (t, ttx, tty, ttz, ccx, ccy, ccz, oox, ooy, ooz) -> 0;
		if (exprY == null) exprY = (t, ttx, tty, ttz, ccx, ccy, ccz, oox, ooy, ooz) -> 0;
		if (exprZ == null) exprZ = (t, ttx, tty, ttz, ccx, ccy, ccz, oox, ooy, ooz) -> 0;
	}
}
