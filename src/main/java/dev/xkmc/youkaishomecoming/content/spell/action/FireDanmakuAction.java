package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.mover.SpaceAttachedMover;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType;

import java.util.List;
import java.util.Optional;

/**
 * Data-driven action to fire danmaku projectiles.
 * Supports ring, line, random, aimed, and nested_ring patterns with configurable parameters.
 */
public record FireDanmakuAction(
		YHDanmaku.Bullet bulletType,
		ColorProvider color,
		NumberProvider count,
		NumberProvider speed,
		NumberProvider lifetime,
		NumberProvider angleOffset,
		NumberProvider spread,
		NumberProvider elevation,
		PatternType pattern,
		OriginConfig origin,
		AimMode aimMode,
		Optional<MoverConfig> mover,
		Optional<NumberProvider> outerCount,
		Optional<List<SpellAction>> onExpiry,
		Optional<List<SpellAction>> onTrail,
		int trailInterval,
		Optional<NumberProvider> tiltAngle,
		Optional<List<SpellAction>> onHitEntity,
		Optional<List<SpellAction>> onHitBlock,
		HitBehavior hitBehaviorEntity,
		HitBehavior hitBehaviorBlock,
		Optional<DanmakuDamageType> damageType,
		Optional<GroupRotation> groupRotation
) implements SpellAction {

	/** Backwards-compatible constructor without tiltAngle/onHit/damageType fields (16 args). */
	public FireDanmakuAction(
			YHDanmaku.Bullet bulletType, ColorProvider color,
			NumberProvider count, NumberProvider speed, NumberProvider lifetime,
			NumberProvider angleOffset, NumberProvider spread, NumberProvider elevation,
			PatternType pattern, OriginConfig origin, AimMode aimMode,
			Optional<MoverConfig> mover, Optional<NumberProvider> outerCount,
			Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
			int trailInterval) {
		this(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation,
				pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval,
				Optional.empty(), Optional.empty(), Optional.empty(), HitBehavior.DISCARD, HitBehavior.CONTINUE,
				Optional.empty(), Optional.empty());
	}

	/** Backwards-compatible constructor with tiltAngle but without onHit/damageType fields (17 args). */
	public FireDanmakuAction(
			YHDanmaku.Bullet bulletType, ColorProvider color,
			NumberProvider count, NumberProvider speed, NumberProvider lifetime,
			NumberProvider angleOffset, NumberProvider spread, NumberProvider elevation,
			PatternType pattern, OriginConfig origin, AimMode aimMode,
			Optional<MoverConfig> mover, Optional<NumberProvider> outerCount,
			Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
			int trailInterval, Optional<NumberProvider> tiltAngle) {
		this(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation,
				pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval,
				tiltAngle, Optional.empty(), Optional.empty(), HitBehavior.DISCARD, HitBehavior.CONTINUE,
				Optional.empty(), Optional.empty());
	}

	// 16-field group (DFU RecordCodecBuilder limit)
	private static final com.mojang.serialization.MapCodec<FireDanmakuAction> BASE_MAP = RecordCodecBuilder.mapCodec(i -> i.group(
			SpellCodecs.BULLET_CODEC.fieldOf("bullet").forGetter(FireDanmakuAction::bulletType),
			ColorProvider.CODEC.fieldOf("color").forGetter(FireDanmakuAction::color),
			NumberProvider.CODEC.fieldOf("count").forGetter(FireDanmakuAction::count),
			NumberProvider.CODEC.fieldOf("speed").forGetter(FireDanmakuAction::speed),
			NumberProvider.CODEC.fieldOf("lifetime").forGetter(FireDanmakuAction::lifetime),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(FireDanmakuAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("spread", NumberProvider.constant(360)).forGetter(FireDanmakuAction::spread),
			NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(FireDanmakuAction::elevation),
			PatternType.CODEC.optionalFieldOf("pattern", PatternType.RING).forGetter(FireDanmakuAction::pattern),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(FireDanmakuAction::origin),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(FireDanmakuAction::aimMode),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(FireDanmakuAction::mover),
			NumberProvider.CODEC.optionalFieldOf("outer_count").forGetter(FireDanmakuAction::outerCount),
			SpellAction.CODEC.listOf().optionalFieldOf("on_expiry").forGetter(FireDanmakuAction::onExpiry),
			SpellAction.CODEC.listOf().optionalFieldOf("on_trail").forGetter(FireDanmakuAction::onTrail),
			Codec.INT.optionalFieldOf("trail_interval", 1).forGetter(FireDanmakuAction::trailInterval)
	).apply(i, FireDanmakuAction::new));

	// Extra fields beyond 16-field limit (merged at same JSON level via codec composition)
	public static final Codec<FireDanmakuAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			BASE_MAP.forGetter(fda -> fda),
			NumberProvider.CODEC.optionalFieldOf("tilt_angle").forGetter(FireDanmakuAction::tiltAngle),
			SpellAction.CODEC.listOf().optionalFieldOf("on_hit_entity").forGetter(FireDanmakuAction::onHitEntity),
			SpellAction.CODEC.listOf().optionalFieldOf("on_hit_block").forGetter(FireDanmakuAction::onHitBlock),
			HitBehavior.CODEC.optionalFieldOf("hit_behavior_entity", HitBehavior.DISCARD).forGetter(FireDanmakuAction::hitBehaviorEntity),
			HitBehavior.CODEC.optionalFieldOf("hit_behavior_block", HitBehavior.CONTINUE).forGetter(FireDanmakuAction::hitBehaviorBlock),
			DanmakuDamageType.CODEC.optionalFieldOf("damage_type").forGetter(FireDanmakuAction::damageType),
			GroupRotation.CODEC.optionalFieldOf("group_rotation").forGetter(FireDanmakuAction::groupRotation)
	).apply(i, (base, tilt, hitEnt, hitBlk, hitEntBhv, hitBlkBhv, dmgType, grpRot) -> new FireDanmakuAction(
			base.bulletType, base.color, base.count, base.speed, base.lifetime,
			base.angleOffset, base.spread, base.elevation, base.pattern, base.origin,
			base.aimMode, base.mover, base.outerCount, base.onExpiry, base.onTrail,
			base.trailInterval, tilt, hitEnt, hitBlk, hitEntBhv, hitBlkBhv, dmgType, grpRot
	)));

	// withXxx helper methods for editor use (preserve all 23 fields)
	private FireDanmakuAction all(YHDanmaku.Bullet bt, ColorProvider c, NumberProvider cnt, NumberProvider spd, NumberProvider lt, NumberProvider ao, NumberProvider sp, NumberProvider el, PatternType pt, OriginConfig o, AimMode am, Optional<MoverConfig> m, Optional<NumberProvider> oc, Optional<List<SpellAction>> oe, Optional<List<SpellAction>> ot, int ti, Optional<NumberProvider> ta, Optional<List<SpellAction>> ohe, Optional<List<SpellAction>> ohb, HitBehavior hbe, HitBehavior hbb, Optional<DanmakuDamageType> ddt, Optional<GroupRotation> gr) { return new FireDanmakuAction(bt, c, cnt, spd, lt, ao, sp, el, pt, o, am, m, oc, oe, ot, ti, ta, ohe, ohb, hbe, hbb, ddt, gr); }
	public FireDanmakuAction withBulletType(YHDanmaku.Bullet v) { return all(v, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withColor(ColorProvider v) { return all(bulletType, v, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withCount(NumberProvider v) { return all(bulletType, color, v, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withSpeed(NumberProvider v) { return all(bulletType, color, count, v, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withLifetime(NumberProvider v) { return all(bulletType, color, count, speed, v, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withAngleOffset(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, v, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withSpread(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, angleOffset, v, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withElevation(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, v, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withPattern(PatternType v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, v, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withOrigin(OriginConfig v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, v, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withAimMode(AimMode v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, v, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withMover(Optional<MoverConfig> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, v, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withOuterCount(Optional<NumberProvider> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, v, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withOnExpiry(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, v, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withOnTrail(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, v, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withTrailInterval(int v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, v, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withTiltAngle(Optional<NumberProvider> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, v, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withOnHitEntity(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, v, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withOnHitBlock(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, v, hitBehaviorEntity, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withHitBehaviorEntity(HitBehavior v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, v, hitBehaviorBlock, damageType, groupRotation); }
	public FireDanmakuAction withHitBehaviorBlock(HitBehavior v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, v, damageType, groupRotation); }
	public FireDanmakuAction withDamageType(Optional<DanmakuDamageType> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, v, groupRotation); }
	public FireDanmakuAction withGroupRotation(Optional<GroupRotation> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle, onHitEntity, onHitBlock, hitBehaviorEntity, hitBehaviorBlock, damageType, v); }

	@Override
	public void execute(SpellContext ctx) {
		CardHolder holder = ctx.holder();
		var diff = ctx.difficulty();

		int n = diff.adjustCount((int) count.get(ctx));
		double spd = diff.adjustSpeed(speed.get(ctx));
		int life = (int) lifetime.get(ctx);
		double angle = angleOffset.get(ctx);
		double spreadDeg = spread.get(ctx);
		double elevDeg = elevation.get(ctx);

		Vec3 originPos = origin.resolve(ctx);
		Vec3 baseDir = aimMode.getBaseDirection(ctx, originPos);

		// Apply origin.rotation to base direction so the entire pattern rotates together
		double originRot = origin.rotation().get(ctx);
		if (originRot != 0) {
			double rad = Math.toRadians(originRot);
			double cos = Math.cos(rad), sin = Math.sin(rad);
			baseDir = new Vec3(
					baseDir.x * cos - baseDir.z * sin,
					baseDir.y,
					baseDir.x * sin + baseDir.z * cos
			);
		}

		// Tilted plane: rotate the normal vector by tiltAngle degrees around baseDir.
		// For NESTED_RING, tilt_angle is consumed by the inner ring axis logic instead.
		DanmakuHelper.Orientation ori;
		if (tiltAngle.isPresent() && pattern != PatternType.NESTED_RING) {
			double tilt = tiltAngle.get().get(ctx);
			var stdOri = DanmakuHelper.getOrientation(baseDir);
			Vec3 tiltedNormal = stdOri.normal().scale(Math.cos(Math.toRadians(tilt)))
					.add(stdOri.side().scale(Math.sin(Math.toRadians(tilt))));
			ori = DanmakuHelper.getOrientation(baseDir, tiltedNormal);
		} else {
			ori = DanmakuHelper.getOrientation(baseDir);
		}

		// Apply group rotation: rotates the pattern's lateral axes (normal/side) only.
		// baseDir (forward) is intentionally left untouched so the overall aim direction —
		// and therefore each projectile's emission direction relative to baseDir — is preserved.
		// rotX/Y/Z thus reshape the ring/pattern plane around the aim axis without deflecting
		// the volley away from the target.
		if (groupRotation.isPresent()) {
			var gr = groupRotation.get();
			Vec3 forward = ori.forward();
			Vec3 rotatedNormal = gr.apply(ori.normal(), ctx);
			// Re-orthogonalize the rotated normal against the unchanged forward
			double dot = rotatedNormal.dot(forward);
			Vec3 projNormal = rotatedNormal.subtract(forward.scale(dot));
			if (projNormal.lengthSqr() > 1e-8) {
				ori = DanmakuHelper.getOrientation(forward, projNormal.normalize());
			}
			// baseDir unchanged → emitDanmaku still aims along the original direction
		}

		// NESTED_RING: outer ring × inner ring with configurable inner axis via tilt_angle.
		//   tilt_angle = 0° (default): inner ring in vertical plane (orange-slice / classic)
		//   tilt_angle = 90°: inner ring perpendicular to outer dir (stacked-hoop)
		//   Any value in between blends the two orientations.
		// count = inner ring count, outer_count = outer ring count, elevation = inner arc range
		if (pattern == PatternType.NESTED_RING && outerCount.isPresent()) {
			int outer = diff.adjustCount((int) outerCount.get().get(ctx));
			double innerSpread = elevDeg != 0 ? elevDeg : 360.0;
			boolean innerClosed = Math.abs(innerSpread) >= 360.0;
			double tilt = tiltAngle.isPresent() ? tiltAngle.get().get(ctx) : 0;
			for (int o = 0; o < outer; o++) {
				double outerAngle = (360.0 / outer) * o + angle;
				Vec3 outerDir = ori.rotateDegrees(outerAngle);
				// Build inner ring orientation based on tilt:
				//   verticalNormal = parent ori.normal (vertical plane → orange-slice)
				//   perpNormal     = getOrientation(outerDir).normal (perpendicular plane → stacked-hoop)
				//   Blend between the two by tilt angle (0°=vertical, 90°=perpendicular)
				DanmakuHelper.Orientation innerOri;
				if (Math.abs(tilt) < 1e-3) {
					// Pure vertical (classic): inner ring in the plane of (outerDir, parent normal)
					innerOri = DanmakuHelper.getOrientation(outerDir, ori.normal());
				} else if (Math.abs(tilt - 90) < 1e-3 || Math.abs(tilt + 90) < 1e-3) {
					// Pure perpendicular (stacked-hoop)
					innerOri = DanmakuHelper.getOrientation(outerDir);
				} else {
					// Blend: rotate the vertical normal toward the perpendicular normal by tilt degrees
					Vec3 vertNormal = ori.normal();
					Vec3 perpNormal = DanmakuHelper.getOrientation(outerDir).normal();
					double tiltRad = Math.toRadians(tilt);
					Vec3 blendedNormal = vertNormal.scale(Math.cos(tiltRad))
							.add(perpNormal.scale(Math.sin(tiltRad))).normalize();
					innerOri = DanmakuHelper.getOrientation(outerDir, blendedNormal);
				}
				for (int j = 0; j < n; j++) {
					double innerAngle;
					if (innerClosed) {
						innerAngle = (360.0 / Math.max(n, 1)) * j;
					} else {
						innerAngle = n > 1 ? -innerSpread / 2.0 + innerSpread * j / (n - 1) : 0;
					}
					Vec3 dir = innerOri.rotateDegrees(0, innerAngle).scale(spd);
					emitDanmaku(holder, ctx, life, dir, originPos, baseDir);
				}
			}
			return;
		}

		// GRID: count = rows, outerCount = cols; spread = row/col angular spacing
		if (pattern == PatternType.GRID) {
			int rows = n;
			int cols = outerCount.map(np -> (int) np.get(ctx)).orElse(n);
			double rowSpread = spreadDeg;
			for (int r = 0; r < rows; r++) {
				double rowAngle = rows > 1 ? rowSpread * (r - (rows - 1) / 2.0) / (rows - 1) : 0;
				for (int c = 0; c < cols; c++) {
					double colAngle = cols > 1 ? rowSpread * (c - (cols - 1) / 2.0) / (cols - 1) : 0;
					Vec3 dir = ori.rotateDegrees(angle + colAngle, rowAngle).scale(spd);
					emitDanmaku(holder, ctx, life, dir, originPos, baseDir);
				}
			}
			return;
		}

		// SPHERE: Fibonacci (golden-angle) uniform distribution
		// count = total projectiles, elevation = latitude range (default 180°), spread = longitude range (default 360°)
		// Uses ori's coordinate system so the sphere is oriented along baseDir
		if (pattern == PatternType.SPHERE) {
			double latRange = elevDeg != 0 ? Math.abs(elevDeg) : 180.0;
			double lonRange = spreadDeg != 360 ? spreadDeg : 360.0;
			// Golden angle in degrees
			double goldenAngle = 360.0 / ((1 + Math.sqrt(5)) / 2);
			// Map latitude range to sin-space for uniform area distribution
			double sinLatMin = Math.sin(Math.toRadians(-latRange / 2.0));
			double sinLatMax = Math.sin(Math.toRadians(latRange / 2.0));
			// Randomize Fibonacci spiral orientation to break the fixed asymmetric pattern.
			// Full sphere: uniform SO(3) rotation (tilt pole axis + spin).
			// Partial sphere: spin around forward only (preserve aiming direction).
			var rand = holder.random();
			boolean fullSphere = latRange >= 180.0 - 1e-3 && lonRange >= 360.0 - 1e-3;
			if (fullSphere) {
				// Uniform random rotation via Euler angles (Haar measure on SO(3))
				double alpha = rand.nextDouble() * 2 * Math.PI;
				double cosTheta = 2 * rand.nextDouble() - 1;
				double thetaRad = Math.acos(cosTheta);
				double gamma = rand.nextDouble() * 2 * Math.PI;
				// Step 1: rotate normal/side around forward by alpha
				Vec3 n1 = ori.normal().scale(Math.cos(alpha)).add(ori.side().scale(Math.sin(alpha)));
				Vec3 s1 = ori.normal().scale(-Math.sin(alpha)).add(ori.side().scale(Math.cos(alpha)));
				// Step 2: tilt forward toward n1 by thetaRad (pole axis randomization)
				Vec3 f2 = ori.forward().scale(Math.cos(thetaRad)).add(n1.scale(Math.sin(thetaRad)));
				Vec3 n2 = ori.forward().scale(-Math.sin(thetaRad)).add(n1.scale(Math.cos(thetaRad)));
				// Step 3: rotate around new forward by gamma
				Vec3 n3 = n2.scale(Math.cos(gamma)).add(s1.scale(Math.sin(gamma)));
				Vec3 s3 = n2.scale(-Math.sin(gamma)).add(s1.scale(Math.cos(gamma)));
				ori = new DanmakuHelper.Orientation(f2, n3, s3);
			} else {
				// Partial sphere: only spin around forward to preserve coverage direction
				double spin = rand.nextDouble() * 2 * Math.PI;
				Vec3 rn = ori.normal().scale(Math.cos(spin)).add(ori.side().scale(Math.sin(spin)));
				Vec3 rs = ori.normal().scale(-Math.sin(spin)).add(ori.side().scale(Math.cos(spin)));
				ori = new DanmakuHelper.Orientation(ori.forward(), rn, rs);
			}
			for (int i = 0; i < n; i++) {
				// Fibonacci latitude: uniform in sin(phi) space → uniform area on sphere
				double t = n > 1 ? (double) i / (n - 1) : 0.5;
				double sinPhi = sinLatMin + (sinLatMax - sinLatMin) * t;
				double phi = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinPhi))));
				// Golden-angle longitude
				double theta = angle + (goldenAngle * i) % lonRange;
				if (lonRange < 360.0) {
					theta = angle - lonRange / 2.0 + (goldenAngle * i) % lonRange;
				}
				Vec3 dir = ori.rotateDegrees(theta, phi).scale(spd);
				emitDanmaku(holder, ctx, life, dir, originPos, baseDir);
			}
			return;
		}

		// SPHERE_RANDOM: random uniform distribution on sphere surface
		// count = total projectiles, elevation = latitude range (default 180°), spread = longitude range (default 360°)
		if (pattern == PatternType.SPHERE_RANDOM) {
			double latRange = elevDeg != 0 ? Math.abs(elevDeg) : 180.0;
			double lonRange = spreadDeg != 360 ? spreadDeg : 360.0;
			double sinLatMin = Math.sin(Math.toRadians(-latRange / 2.0));
			double sinLatMax = Math.sin(Math.toRadians(latRange / 2.0));
			var rand = holder.random();
			for (int i = 0; i < n; i++) {
				// Uniform in sin(phi) space for area-correct random distribution
				double sinPhi = sinLatMin + (sinLatMax - sinLatMin) * rand.nextDouble();
				double phi = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinPhi))));
				double theta = angle - lonRange / 2.0 + lonRange * rand.nextDouble();
				Vec3 dir = ori.rotateDegrees(theta, phi).scale(spd);
				emitDanmaku(holder, ctx, life, dir, originPos, baseDir);
			}
			return;
		}

		// SPIRAL: count projectiles, spread = total turns (in degrees), outerCount = unused
		if (pattern == PatternType.SPIRAL) {
			double totalTurns = spreadDeg; // total angle swept (e.g. 720 for 2 turns)
			for (int i = 0; i < n; i++) {
				double t = n > 1 ? (double) i / (n - 1) : 0;
				double a = angle + totalTurns * t;
				// Radius grows linearly with t; elevation stays flat (horizontal spiral)
				Vec3 dir = ori.rotateDegrees(a).scale(spd * (0.3 + 0.7 * t));
				emitDanmaku(holder, ctx, life, dir, originPos, baseDir);
			}
			return;
		}

		// CONE: forward as axis, elevation = cone half-angle.
		// Equivalent to legacy asNormal().rotateDegrees(a, coneAngle):
		//   dir = forward * sin(coneAngle) + (normal*cos(a) + side*sin(a)) * cos(coneAngle)
		if (pattern == PatternType.CONE) {
			double coneRad = Math.toRadians(elevDeg);
			double sinCone = Math.sin(coneRad);
			double cosCone = Math.cos(coneRad);
			for (int i = 0; i < n; i++) {
				double a = Math.toRadians(angle + (360.0 / n) * i);
				Vec3 radial = ori.normal().scale(Math.cos(a)).add(ori.side().scale(Math.sin(a)));
				Vec3 dir = ori.forward().scale(sinCone).add(radial.scale(cosCone)).scale(spd);
				emitDanmaku(holder, ctx, life, dir, originPos, baseDir);
			}
			return;
		}

		for (int i = 0; i < n; i++) {
			double a = angle;
			double v = elevDeg; // vertical/elevation angle (degrees)
			switch (pattern) {
				case RING -> a += (360.0 / n) * i;
				case LINE -> {
					if (n > 1) {
						a += spreadDeg * (i - (n - 1) / 2.0) / (n - 1);
					}
				}
				case RANDOM -> {
					a += holder.random().nextDouble() * spreadDeg - spreadDeg / 2;
					if (elevDeg != 0) {
						v = holder.random().nextDouble() * elevDeg - elevDeg / 2;
					}
				}
				case AIMED, NESTED_RING -> {} // NESTED_RING handled above
				default -> {} // New patterns handled above
			}
			Vec3 dir = ori.rotateDegrees(a, v).scale(spd);
			emitDanmaku(holder, ctx, life, dir, originPos, baseDir);
		}
	}

	private void emitDanmaku(CardHolder holder, SpellContext ctx, int life, Vec3 dir, Vec3 originPos, Vec3 baseDir) {
		DyeColor resolvedColor = color.get(ctx);
		var danmaku = holder.prepareDanmaku(life, dir, bulletType, resolvedColor);
		danmaku.setPos(originPos);
		// Apply per-action damage type override
		if (damageType.isPresent()) {
			danmaku.damageTypeOverride = damageType.get();
		}
		if (mover.isPresent()) {
			// Pass baseDir, target, and caster positions so movers can use them in expressions
			Vec3 targetPos = holder.target() != null ? holder.target() : Vec3.ZERO;
			Vec3 casterPos = holder.self() != null ? holder.self().position() : Vec3.ZERO;
			danmaku.mover = mover.get().create(originPos, dir, baseDir, targetPos, casterPos);
		} else if (holder.self() instanceof ShooterEntity se && se.isSpaceMode()) {
			// Auto-attach: bullet has no explicit mover and shooter is in space mode
			// Create SpaceAttachedMover with localOffset = bulletPos - shooterPos, expansion_speed = 0
			danmaku.mover = new SpaceAttachedMover(originPos, dir, se.position(), 0);
		}
		if (onExpiry.isPresent()) {
			var expiryAction = new DataDrivenTrailAction(onExpiry.get(), ctx.runtime(), ctx.definition());
			expiryAction.setup(holder);
			danmaku.afterExpiry = expiryAction;
		}
		if (onTrail.isPresent()) {
			var trailAction = new DataDrivenTrailAction(onTrail.get(), ctx.runtime(), ctx.definition());
			trailAction.setup(holder);
			danmaku.onTrail = trailAction;
			danmaku.trailInterval = Math.max(1, trailInterval);
		}
		if (onHitEntity.isPresent()) {
			var hitAction = new DataDrivenTrailAction(onHitEntity.get(), ctx.runtime(), ctx.definition());
			hitAction.setup(holder);
			danmaku.onHitEntityAction = hitAction;
		}
		if (onHitBlock.isPresent()) {
			var hitAction = new DataDrivenTrailAction(onHitBlock.get(), ctx.runtime(), ctx.definition());
			hitAction.setup(holder);
			danmaku.onHitBlockAction = hitAction;
		}
		danmaku.hitBehaviorEntity = hitBehaviorEntity;
		danmaku.hitBehaviorBlock = hitBehaviorBlock;
		// Default from prepareDanmaku is bypassWall=true, bypassEntity=true (boss danmaku defaults).
		// Keep the legacy pass-through behavior unless this action explicitly uses block hit handling:
		// either a block-hit callback is configured, or block hits should stop/expire the danmaku.
		if (onHitBlock.isPresent() || hitBehaviorBlock != HitBehavior.CONTINUE) {
			danmaku.setBypassWall(false);
		}
		// Data-driven danmaku always enable entity collision detection.
		// Whether they keep flying or erase is decided in onHitEntity().
		danmaku.setBypassEntity(false);
		holder.shoot(danmaku);
	}

}
