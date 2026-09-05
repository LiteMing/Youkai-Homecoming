package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Translate mover: applies a world-coordinate offset to the projectile.
 * <p>
 * Two modes of operation:
 * <ol>
 *   <li><b>Formula mode</b> (aim="none"): x/y/z formula expressions define the offset.
 *       By default the expressions are world-coordinate offsets. With {@code space="local"}
 *       they use the launch frame (forward/right/up). Supports extended variables
 *       (targetX/Y/Z, casterX/Y/Z, originX/Y/Z, tick).</li>
 *   <li><b>Aim mode</b> (aim="target"/"forward"): pre-computed direction + speed.
 *       Only stores a Vec3 direction and a double speed — minimal network footprint.
 *       Position = origin + direction * speed * tick.</li>
 * </ol>
 * <p>
 * Additional pre-computed variables available in formula mode:
 * <ul>
 *   <li>{@code dist} — distance from origin to target at spawn time</li>
 *   <li>{@code dx}, {@code dy}, {@code dz} — normalized direction from origin to target</li>
 * </ul>
 */
@SerialClass
public final class TranslateMover extends TargetPosMover {

	@SerialClass.SerialField
	private Vec3 origin = Vec3.ZERO;

	// Formula mode fields
	@SerialClass.SerialField
	private String formulaX = "0";
	@SerialClass.SerialField
	private String formulaY = "0";
	@SerialClass.SerialField
	private String formulaZ = "0";

	/** Whether formula offsets use the launch frame instead of world axes. */
	@SerialClass.SerialField
	private boolean localSpace = false;
	/** Launch-frame basis, persisted so a saved projectile keeps deterministic motion. */
	@SerialClass.SerialField
	private Vec3 localForward = new Vec3(0, 0, 1);
	@SerialClass.SerialField
	private Vec3 localRight = new Vec3(1, 0, 0);
	@SerialClass.SerialField
	private Vec3 localUp = new Vec3(0, 1, 0);

	// Pre-computed aim mode fields (no formula strings needed — saves network bandwidth)
	@SerialClass.SerialField
	private Vec3 aimDir = Vec3.ZERO;
	@SerialClass.SerialField
	private double aimSpeed = 0;
	@SerialClass.SerialField
	private boolean useAimMode = false;

	// Snapshotted values for formula mode
	@SerialClass.SerialField
	private Vec3 targetPos = Vec3.ZERO;
	@SerialClass.SerialField
	private Vec3 casterPos = Vec3.ZERO;
	@SerialClass.SerialField
	private double dist = 0;
	@SerialClass.SerialField
	private double dx = 0, dy = 0, dz = 0;

	private transient FormulaExpr.RichEvaluable exprX;
	private transient FormulaExpr.RichEvaluable exprY;
	private transient FormulaExpr.RichEvaluable exprZ;

	@Deprecated
	public TranslateMover() {
	}

	/** Formula mode constructor. */
	public TranslateMover(Vec3 origin, String formulaX, String formulaY, String formulaZ,
						  Vec3 targetPos, Vec3 casterPos) {
		this.origin = origin;
		this.formulaX = formulaX;
		this.formulaY = formulaY;
		this.formulaZ = formulaZ;
		this.targetPos = targetPos;
		this.casterPos = casterPos;
		this.useAimMode = false;
		// Pre-compute dist and dx/dy/dz for use in formulas
		Vec3 diff = targetPos.subtract(origin);
		this.dist = diff.length();
		if (this.dist > 1e-4) {
			this.dx = diff.x / this.dist;
			this.dy = diff.y / this.dist;
			this.dz = diff.z / this.dist;
		}
	}

	/**
	 * Formula mode with an optional launch-local frame. The frame is captured once
	 * when the projectile is emitted, so changing group yaw/pitch affects this
	 * projectile's plane but never causes a per-flight-tick angle update.
	 */
	public TranslateMover(Vec3 origin, String formulaX, String formulaY, String formulaZ,
						  Vec3 targetPos, Vec3 casterPos, Vec3 launchDirection, boolean localSpace) {
		this(origin, formulaX, formulaY, formulaZ, targetPos, casterPos);
		if (localSpace) {
			Vec3 direction = launchDirection != null && launchDirection.lengthSqr() > 1e-8
					? launchDirection.normalize() : new Vec3(0, 0, 1);
			var orientation = DanmakuHelper.getOrientation(direction);
			this.localSpace = true;
			this.localForward = orientation.forward();
			this.localRight = orientation.side();
			this.localUp = orientation.normal();
		}
	}

	/** Aim mode constructor — no formula strings, minimal serialization. */
	public TranslateMover(Vec3 origin, Vec3 aimDir, double aimSpeed) {
		this.origin = origin;
		this.aimDir = aimDir;
		this.aimSpeed = aimSpeed;
		this.useAimMode = true;
		// Formula fields stay at "0" — they won't be evaluated
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		if (useAimMode) {
			return origin.add(aimDir.scale(aimSpeed * info.tick()));
		}
		return posFormula(info.tick(), info);
	}

	public Vec3 pos(double tick) {
		if (useAimMode) {
			return origin.add(aimDir.scale(aimSpeed * tick));
		}
		return posFormula(tick, null);
	}

	private Vec3 posFormula(double tick, MoverInfo info) {
		ensureCompiled();
		double tx = targetPos.x, ty = targetPos.y, tz = targetPos.z;
		double cx = casterPos.x, cy = casterPos.y, cz = casterPos.z;
		double ox = origin.x, oy = origin.y, oz = origin.z;

		// Live caster tracking if available
		if (info != null && info.ownerInfo() != null && info.ownerInfo().ownerPos() != null) {
			Vec3 ownerPos = info.ownerInfo().ownerPos();
			cx = ownerPos.x;
			cy = ownerPos.y;
			cz = ownerPos.z;
		}

		double x = exprX.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		double y = exprY.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		double z = exprZ.eval(tick, tx, ty, tz, cx, cy, cz, ox, oy, oz);
		if (!localSpace) {
			return origin.add(x, y, z);
		}
		return origin.add(localForward.scale(x))
				.add(localRight.scale(y))
				.add(localUp.scale(z));
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
