package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.AimMode;
import dev.xkmc.youkaishomecoming.content.spell.definition.GroupRotation;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.PatternType;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class PatternEmitter {

	public record Settings(NumberProvider count, NumberProvider speed, NumberProvider angleOffset,
						   NumberProvider spread, NumberProvider elevation, PatternType pattern,
						   AimMode aimMode, NumberProvider originRotation, Optional<NumberProvider> outerCount,
						   Optional<NumberProvider> tiltAngle, Optional<GroupRotation> groupRotation,
						   boolean randomAxis) {
	}

	@FunctionalInterface
	public interface Sink {
		void emit(Vec3 velocity, Vec3 baseDirection, int spawnIndex, double resolvedSpread);
	}

	public static void emit(SpellContext ctx, Vec3 originPos, Settings settings, Sink sink) {
		CardHolder holder = ctx.holder();
		var diff = ctx.difficulty();
		int n = diff.adjustCount((int) settings.count().get(ctx));
		double spd = diff.adjustSpeed(settings.speed().get(ctx));
		double angle = settings.angleOffset().get(ctx);
		double spreadDeg = settings.spread().get(ctx);
		double elevDeg = settings.elevation().get(ctx);

		Vec3 baseDir = settings.aimMode().getBaseDirection(ctx, originPos);
		double originRot = settings.originRotation().get(ctx);
		if (originRot != 0) {
			double rad = Math.toRadians(originRot);
			double cos = Math.cos(rad), sin = Math.sin(rad);
			baseDir = new Vec3(
					baseDir.x * cos - baseDir.z * sin,
					baseDir.y,
					baseDir.x * sin + baseDir.z * cos
			);
		}

		DanmakuHelper.Orientation ori;
		if (settings.tiltAngle().isPresent() && settings.pattern() != PatternType.NESTED_RING) {
			double tilt = settings.tiltAngle().get().get(ctx);
			var stdOri = DanmakuHelper.getOrientation(baseDir);
			Vec3 tiltedNormal = stdOri.normal().scale(Math.cos(Math.toRadians(tilt)))
					.add(stdOri.side().scale(Math.sin(Math.toRadians(tilt))));
			ori = DanmakuHelper.getOrientation(baseDir, tiltedNormal);
		} else {
			ori = DanmakuHelper.getOrientation(baseDir);
		}

		if (settings.groupRotation().isPresent()) {
			ori = settings.groupRotation().get().apply(ori, ctx);
			baseDir = ori.forward();
		}

		if (settings.pattern() == PatternType.NESTED_RING && settings.outerCount().isPresent()) {
			int outer = diff.adjustCount((int) settings.outerCount().get().get(ctx));
			double innerSpread = elevDeg != 0 ? elevDeg : 360.0;
			boolean innerClosed = Math.abs(innerSpread) >= 360.0;
			double tilt = settings.tiltAngle().isPresent() ? settings.tiltAngle().get().get(ctx) : 0;
			int index = 0;
			for (int o = 0; o < outer; o++) {
				double outerAngle = (360.0 / outer) * o + angle;
				Vec3 outerDir = ori.rotateDegrees(outerAngle);
				DanmakuHelper.Orientation innerOri;
				if (Math.abs(tilt) < 1e-3) {
					innerOri = DanmakuHelper.getOrientation(outerDir, ori.normal());
				} else if (Math.abs(tilt - 90) < 1e-3 || Math.abs(tilt + 90) < 1e-3) {
					innerOri = DanmakuHelper.getOrientation(outerDir);
				} else {
					Vec3 vertNormal = ori.normal();
					Vec3 perpNormal = DanmakuHelper.getOrientation(outerDir).normal();
					double tiltRad = Math.toRadians(tilt);
					Vec3 blendedNormal = vertNormal.scale(Math.cos(tiltRad))
							.add(perpNormal.scale(Math.sin(tiltRad))).normalize();
					innerOri = DanmakuHelper.getOrientation(outerDir, blendedNormal);
				}
				for (int j = 0; j < n; j++) {
					double innerAngle = innerClosed ? (360.0 / Math.max(n, 1)) * j :
							n > 1 ? -innerSpread / 2.0 + innerSpread * j / (n - 1) : 0;
					sink.emit(innerOri.rotateDegrees(0, innerAngle).scale(spd), baseDir, index++, spreadDeg);
				}
			}
			return;
		}

		if (settings.pattern() == PatternType.GRID) {
			int rows = n;
			int cols = settings.outerCount().map(np -> (int) np.get(ctx)).orElse(n);
			double rowSpread = spreadDeg;
			int index = 0;
			for (int r = 0; r < rows; r++) {
				double rowAngle = rows > 1 ? rowSpread * (r - (rows - 1) / 2.0) / (rows - 1) : 0;
				for (int c = 0; c < cols; c++) {
					double colAngle = cols > 1 ? rowSpread * (c - (cols - 1) / 2.0) / (cols - 1) : 0;
					sink.emit(ori.rotateDegrees(angle + colAngle, rowAngle).scale(spd), baseDir, index++, spreadDeg);
				}
			}
			return;
		}

		if (settings.pattern() == PatternType.SPHERE) {
			double latRange = elevDeg != 0 ? Math.abs(elevDeg) : 180.0;
			double lonRange = spreadDeg != 360 ? spreadDeg : 360.0;
			double goldenAngle = 360.0 / ((1 + Math.sqrt(5)) / 2);
			double sinLatMin = Math.sin(Math.toRadians(-latRange / 2.0));
			double sinLatMax = Math.sin(Math.toRadians(latRange / 2.0));
			boolean fullSphere = latRange >= 180.0 - 1e-3 && lonRange >= 360.0 - 1e-3;
			if (settings.randomAxis()) {
				var rand = holder.random();
				if (fullSphere) {
					double alpha = rand.nextDouble() * 2 * Math.PI;
					double cosTheta = 2 * rand.nextDouble() - 1;
					double thetaRad = Math.acos(cosTheta);
					double gamma = rand.nextDouble() * 2 * Math.PI;
					Vec3 n1 = ori.normal().scale(Math.cos(alpha)).add(ori.side().scale(Math.sin(alpha)));
					Vec3 s1 = ori.normal().scale(-Math.sin(alpha)).add(ori.side().scale(Math.cos(alpha)));
					Vec3 f2 = ori.forward().scale(Math.cos(thetaRad)).add(n1.scale(Math.sin(thetaRad)));
					Vec3 n2 = ori.forward().scale(-Math.sin(thetaRad)).add(n1.scale(Math.cos(thetaRad)));
					Vec3 n3 = n2.scale(Math.cos(gamma)).add(s1.scale(Math.sin(gamma)));
					Vec3 s3 = n2.scale(-Math.sin(gamma)).add(s1.scale(Math.cos(gamma)));
					ori = new DanmakuHelper.Orientation(f2, n3, s3);
				} else {
					double spin = rand.nextDouble() * 2 * Math.PI;
					Vec3 rn = ori.normal().scale(Math.cos(spin)).add(ori.side().scale(Math.sin(spin)));
					Vec3 rs = ori.normal().scale(-Math.sin(spin)).add(ori.side().scale(Math.cos(spin)));
					ori = new DanmakuHelper.Orientation(ori.forward(), rn, rs);
				}
			}
			for (int i = 0; i < n; i++) {
				double t = n > 1 ? (double) i / (n - 1) : 0.5;
				double sinPhi = sinLatMin + (sinLatMax - sinLatMin) * t;
				double phi = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinPhi))));
				double theta = angle + (goldenAngle * i) % lonRange;
				if (lonRange < 360.0) {
					theta = angle - lonRange / 2.0 + (goldenAngle * i) % lonRange;
				}
				sink.emit(ori.rotateDegrees(theta, phi).scale(spd), baseDir, i, spreadDeg);
			}
			return;
		}

		if (settings.pattern() == PatternType.SPHERE_RANDOM) {
			double latRange = elevDeg != 0 ? Math.abs(elevDeg) : 180.0;
			double lonRange = spreadDeg != 360 ? spreadDeg : 360.0;
			double sinLatMin = Math.sin(Math.toRadians(-latRange / 2.0));
			double sinLatMax = Math.sin(Math.toRadians(latRange / 2.0));
			var rand = holder.random();
			for (int i = 0; i < n; i++) {
				double sinPhi = sinLatMin + (sinLatMax - sinLatMin) * rand.nextDouble();
				double phi = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sinPhi))));
				double theta = angle - lonRange / 2.0 + lonRange * rand.nextDouble();
				sink.emit(ori.rotateDegrees(theta, phi).scale(spd), baseDir, i, spreadDeg);
			}
			return;
		}

		if (settings.pattern() == PatternType.SPIRAL) {
			for (int i = 0; i < n; i++) {
				double t = n > 1 ? (double) i / (n - 1) : 0;
				double a = angle + spreadDeg * t;
				sink.emit(ori.rotateDegrees(a).scale(spd * (0.3 + 0.7 * t)), baseDir, i, spreadDeg);
			}
			return;
		}

		if (settings.pattern() == PatternType.CONE) {
			double coneRad = Math.toRadians(elevDeg);
			double sinCone = Math.sin(coneRad);
			double cosCone = Math.cos(coneRad);
			for (int i = 0; i < n; i++) {
				double a = Math.toRadians(angle + (360.0 / n) * i);
				Vec3 radial = ori.normal().scale(Math.cos(a)).add(ori.side().scale(Math.sin(a)));
				sink.emit(ori.forward().scale(sinCone).add(radial.scale(cosCone)).scale(spd), baseDir, i, spreadDeg);
			}
			return;
		}

		for (int i = 0; i < n; i++) {
			double a = angle;
			double v = elevDeg;
			switch (settings.pattern()) {
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
				case AIMED, NESTED_RING -> {
				}
				default -> {
				}
			}
			sink.emit(ori.rotateDegrees(a, v).scale(spd), baseDir, i, spreadDeg);
		}
	}

}
