package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

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
		Optional<NumberProvider> tiltAngle
) implements SpellAction {

	/** Backwards-compatible constructor without tiltAngle. */
	public FireDanmakuAction(
			YHDanmaku.Bullet bulletType, ColorProvider color,
			NumberProvider count, NumberProvider speed, NumberProvider lifetime,
			NumberProvider angleOffset, NumberProvider spread, NumberProvider elevation,
			PatternType pattern, OriginConfig origin, AimMode aimMode,
			Optional<MoverConfig> mover, Optional<NumberProvider> outerCount,
			Optional<List<SpellAction>> onExpiry, Optional<List<SpellAction>> onTrail,
			int trailInterval) {
		this(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation,
				pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, Optional.empty());
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

	// 17th field: tilt_angle (merged at same JSON level via codec composition)
	public static final Codec<FireDanmakuAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			BASE_MAP.forGetter(fda -> fda),
			NumberProvider.CODEC.optionalFieldOf("tilt_angle").forGetter(FireDanmakuAction::tiltAngle)
	).apply(i, (base, tilt) -> new FireDanmakuAction(
			base.bulletType, base.color, base.count, base.speed, base.lifetime,
			base.angleOffset, base.spread, base.elevation, base.pattern, base.origin,
			base.aimMode, base.mover, base.outerCount, base.onExpiry, base.onTrail,
			base.trailInterval, tilt
	)));

	// withXxx helper methods for editor use (preserve all 17 fields)
	private FireDanmakuAction all(YHDanmaku.Bullet bt, ColorProvider c, NumberProvider cnt, NumberProvider spd, NumberProvider lt, NumberProvider ao, NumberProvider sp, NumberProvider el, PatternType pt, OriginConfig o, AimMode am, Optional<MoverConfig> m, Optional<NumberProvider> oc, Optional<List<SpellAction>> oe, Optional<List<SpellAction>> ot, int ti, Optional<NumberProvider> ta) { return new FireDanmakuAction(bt, c, cnt, spd, lt, ao, sp, el, pt, o, am, m, oc, oe, ot, ti, ta); }
	public FireDanmakuAction withBulletType(YHDanmaku.Bullet v) { return all(v, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withColor(ColorProvider v) { return all(bulletType, v, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withCount(NumberProvider v) { return all(bulletType, color, v, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withSpeed(NumberProvider v) { return all(bulletType, color, count, v, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withLifetime(NumberProvider v) { return all(bulletType, color, count, speed, v, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withAngleOffset(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, v, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withSpread(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, angleOffset, v, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withElevation(NumberProvider v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, v, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withPattern(PatternType v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, v, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withOrigin(OriginConfig v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, v, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withAimMode(AimMode v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, v, mover, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withMover(Optional<MoverConfig> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, v, outerCount, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withOuterCount(Optional<NumberProvider> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, v, onExpiry, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withOnExpiry(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, v, onTrail, trailInterval, tiltAngle); }
	public FireDanmakuAction withOnTrail(Optional<List<SpellAction>> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, v, trailInterval, tiltAngle); }
	public FireDanmakuAction withTrailInterval(int v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, v, tiltAngle); }
	public FireDanmakuAction withTiltAngle(Optional<NumberProvider> v) { return all(bulletType, color, count, speed, lifetime, angleOffset, spread, elevation, pattern, origin, aimMode, mover, outerCount, onExpiry, onTrail, trailInterval, v); }

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
		Vec3 baseDir = aimMode.getBaseDirection(ctx);

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

		// Tilted plane: rotate the normal vector by tiltAngle degrees around baseDir
		DanmakuHelper.Orientation ori;
		if (tiltAngle.isPresent()) {
			double tilt = tiltAngle.get().get(ctx);
			var stdOri = DanmakuHelper.getOrientation(baseDir);
			Vec3 tiltedNormal = stdOri.normal().scale(Math.cos(Math.toRadians(tilt)))
					.add(stdOri.side().scale(Math.sin(Math.toRadians(tilt))));
			ori = DanmakuHelper.getOrientation(baseDir, tiltedNormal);
		} else {
			ori = DanmakuHelper.getOrientation(baseDir);
		}

		// NESTED_RING: outer ring × inner ring (perpendicular to each outer direction)
		// elevation controls inner ring spread angle (default 360°, i.e. full perpendicular ring)
		if (pattern == PatternType.NESTED_RING && outerCount.isPresent()) {
			int outer = diff.adjustCount((int) outerCount.get().get(ctx));
			double innerSpread = elevDeg != 0 ? elevDeg : 360.0;
			boolean innerClosed = innerSpread >= 360.0;
			for (int o = 0; o < outer; o++) {
				double outerAngle = (360.0 / outer) * o + angle;
				Vec3 outerDir = ori.rotateDegrees(outerAngle);
				var outerOri = DanmakuHelper.getOrientation(outerDir);
				for (int j = 0; j < n; j++) {
					double innerAngle;
					if (innerClosed) {
						innerAngle = (360.0 / Math.max(n, 1)) * j;
					} else {
						innerAngle = n > 1 ? -innerSpread / 2.0 + innerSpread * j / (n - 1) : 0;
					}
					Vec3 dir = outerOri.rotateDegrees(90, innerAngle).scale(spd);
					emitDanmaku(holder, ctx, life, dir, originPos);
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
					emitDanmaku(holder, ctx, life, dir, originPos);
				}
			}
			return;
		}

		// SPHERE: latitude_count = count, longitude_count = outerCount (default = count)
		// spread controls longitude range (default 360°), elevation controls latitude range (default 180°)
		// Uses ori's coordinate system so the sphere is oriented along baseDir
		if (pattern == PatternType.SPHERE) {
			int latCount = n;
			int lonCount = outerCount.map(np -> (int) np.get(ctx)).orElse(n);
			double latRange = elevDeg != 0 ? Math.abs(elevDeg) : 180.0; // total latitude range
			double lonRange = spreadDeg != 360 ? spreadDeg : 360.0;     // total longitude range
			boolean lonClosed = lonRange >= 360.0; // full circle: don't duplicate first/last
			for (int lat = 0; lat < latCount; lat++) {
				// phi: centered around 0, from -latRange/2 to +latRange/2
				double phi = latCount > 1 ? -latRange / 2.0 + latRange * lat / (latCount - 1) : 0;
				int effectiveLon = lonClosed ? lonCount : Math.max(lonCount, 1);
				for (int lon = 0; lon < effectiveLon; lon++) {
					double theta;
					if (lonClosed) {
						theta = (lonRange / lonCount) * lon + angle;
					} else {
						theta = effectiveLon > 1
								? angle - lonRange / 2.0 + lonRange * lon / (effectiveLon - 1)
								: angle;
					}
					Vec3 dir = ori.rotateDegrees(theta, phi).scale(spd);
					emitDanmaku(holder, ctx, life, dir, originPos);
				}
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
				emitDanmaku(holder, ctx, life, dir, originPos);
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
			emitDanmaku(holder, ctx, life, dir, originPos);
		}
	}

	private void emitDanmaku(CardHolder holder, SpellContext ctx, int life, Vec3 dir, Vec3 originPos) {
		DyeColor resolvedColor = color.get(ctx);
		var danmaku = holder.prepareDanmaku(life, dir, bulletType, resolvedColor);
		danmaku.setPos(originPos);
		if (mover.isPresent()) {
			danmaku.mover = mover.get().create(originPos, dir);
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
		holder.shoot(danmaku);
	}

}
