package dev.xkmc.youkaishomecoming.content.spell.pilot.predict;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatFilters;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * T3 observation fallback: last 2–3 tick positions → velocity (+ acceleration) extrapolation.
 * {@link #supports} is always true for Projectile / SimplifiedProjectile — universal catch-all.
 * History &lt; 2 ticks degrades to T4 straight-line from current velocity.
 * Lasers always emit segment frames (never point threats).
 */
public class ObservedMotionProvider implements ThreatProvider {

	private static final int HISTORY_SIZE = 3;
	private final Map<Integer, Deque<PositionRecord>> history = new HashMap<>();

	@Override
	public boolean supports(Entity entity) {
		return entity instanceof Projectile || entity instanceof SimplifiedProjectile;
	}

	@Override
	@Nullable
	public Threat capture(Entity entity, int horizon) {
		if (entity == null || horizon <= 0) return null;

		// Lasers must stay segments even on T3 (position-only fit would collapse to a point)
		if (entity instanceof YHBaseLaserEntity laser) {
			return captureLaser(laser, horizon);
		}

		// Critical: BallisticProvider may return null for stuck arrows; without this gate
		// T3 would still emit a stationary VANILLA threat and dominate APF repulsion.
		if (ThreatFilters.isInertProjectile(entity)) return null;

		Vec3 pos = entity.position();
		Vec3 vel = entity.getDeltaMovement();
		// Same radius rule as game / MoverExactProvider (giants & bubbles)
		float hitRadius = MoverExactProvider.danmakuHitRadius(entity);

		int id = entity.getId();
		Deque<PositionRecord> records = history.computeIfAbsent(id, k -> new ArrayDeque<>());
		records.addLast(new PositionRecord(pos, entity.tickCount));
		while (records.size() > HISTORY_SIZE) {
			records.removeFirst();
		}

		// Fit: linear when 2 samples, quadratic when 3 samples (second-order)
		Vec3 v0 = vel;
		Vec3 accel = Vec3.ZERO;
		boolean quadratic = false;

		if (records.size() >= 3) {
			PositionRecord[] r = records.toArray(new PositionRecord[0]);
			Fit fit = fitQuadratic(r[0], r[1], r[2]);
			if (fit != null) {
				v0 = fit.velocity;
				accel = fit.accel;
				quadratic = true;
			} else {
				v0 = r[2].pos.subtract(r[1].pos).scale(1.0 / Math.max(1, r[2].tick - r[1].tick));
			}
		} else if (records.size() >= 2) {
			PositionRecord[] r = records.toArray(new PositionRecord[0]);
			int dt = Math.max(1, r[1].tick - r[0].tick);
			v0 = r[1].pos.subtract(r[0].pos).scale(1.0 / dt);
		}

		ThreatFrame[] frames = new ThreatFrame[horizon];
		for (int i = 0; i < horizon; i++) {
			Vec3 p;
			if (i == 0) {
				p = pos;
			} else if (quadratic) {
				// p(t) = p0 + v*t + 0.5*a*t^2 from current sample
				double t = i;
				p = pos.add(v0.scale(t)).add(accel.scale(0.5 * t * t));
			} else {
				p = pos.add(v0.scale(i));
			}
			frames[i] = new ThreatFrame(p, null, hitRadius, 0f, true);
		}

		ThreatSemantic sem = entity instanceof IYHDanmaku ? ThreatSemantic.DANMAKU : ThreatSemantic.VANILLA;
		return new Threat(entity.getId(), frames, sem, entity, 0);
	}

	private static Threat captureLaser(YHBaseLaserEntity laser, int horizon) {
		Vec3 anchor = laser.beamStart();
		Vec3 orient = laser.getLookAngle();
		if (orient.lengthSqr() < 1e-12) {
			Vec3 rot = laser.rot();
			orient = rot.equals(Vec3.ZERO)
					? new Vec3(0, 0, 1)
					: Vec3.directionFromRotation((float) (rot.x * Mth.RAD_TO_DEG), (float) (rot.y * Mth.RAD_TO_DEG));
		}
		float hitRadius = laser.getEffectiveHitRadius();
		// Full length for geometry (warn-phase still occupies space); active flag for hard-hit
		float fullLen = (float) laser.getLength();
		float warnLen = laser.effectiveLength(0f);
		float length = Math.max(fullLen, warnLen);
		int t0 = laser.tickCount;
		ThreatFrame[] frames = new ThreatFrame[horizon];
		for (int i = 0; i < horizon; i++) {
			boolean open = laser.isHitWindowOpen(t0 + i);
			// Keep segment present during warn/fade so APF can side-step before open
			float len = open ? fullLen : Math.max(warnLen, fullLen * 0.5f);
			frames[i] = new ThreatFrame(anchor, orient, hitRadius, len, open || len > 0.01f);
		}
		return new Threat(laser.getId(), frames, ThreatSemantic.DANMAKU, laser, laser.damage);
	}

	public void evict(int entityId) {
		history.remove(entityId);
	}

	public void clear() {
		history.clear();
	}

	/**
	 * Finite-difference fit at the latest sample:
	 * v01 = (p1-p0)/dt01, v12 = (p2-p1)/dt12, a ≈ (v12-v01) / ((dt01+dt12)/2),
	 * velocity at p2 ≈ v12.
	 */
	@Nullable
	public static Fit fitQuadratic(PositionRecord r0, PositionRecord r1, PositionRecord r2) {
		double dt01 = r1.tick - r0.tick;
		double dt12 = r2.tick - r1.tick;
		if (dt01 <= 0 || dt12 <= 0) return null;

		Vec3 v01 = r1.pos.subtract(r0.pos).scale(1.0 / dt01);
		Vec3 v12 = r2.pos.subtract(r1.pos).scale(1.0 / dt12);
		double midDt = 0.5 * (dt01 + dt12);
		if (midDt <= 0) return null;
		Vec3 a = v12.subtract(v01).scale(1.0 / midDt);
		return new Fit(v12, a);
	}

	/** Offline path for unit tests (does not touch history map). */
	public static Vec3[] extrapolateLinear(Vec3 pos, Vec3 vel, int horizon) {
		Vec3[] out = new Vec3[horizon];
		for (int i = 0; i < horizon; i++) {
			out[i] = pos.add(vel.scale(i));
		}
		return out;
	}

	public static Vec3[] extrapolateQuadratic(Vec3 pos, Vec3 vel, Vec3 accel, int horizon) {
		Vec3[] out = new Vec3[horizon];
		for (int i = 0; i < horizon; i++) {
			double t = i;
			out[i] = pos.add(vel.scale(t)).add(accel.scale(0.5 * t * t));
		}
		return out;
	}

	public record PositionRecord(Vec3 pos, int tick) {
	}

	public record Fit(Vec3 velocity, Vec3 accel) {
	}
}
