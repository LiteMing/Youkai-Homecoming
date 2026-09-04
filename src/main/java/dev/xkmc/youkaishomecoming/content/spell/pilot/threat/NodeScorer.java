package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores a self pose (and optional motion) against threats at a given horizon tick.
 * Uses per-axis AABB clearance + exponential near-field penalty; graze band is non-lethal.
 */
public final class NodeScorer {

	/** Clearance below this (but not hard-hit) counts as graze. */
	private final float grazeBand;
	/** Score = -exp(-clearance / scale) style; larger scale → softer falloff. */
	private final double falloff;
	/** Hard floor: clearance ≤ 0 on hard box → dead. */
	private final boolean useSweep;

	public NodeScorer(float grazeBand, double falloff, boolean useSweep) {
		this.grazeBand = grazeBand;
		this.falloff = falloff;
		this.useSweep = useSweep;
	}

	public static NodeScorer defaults() {
		return new NodeScorer(SelfBoxModel.GRAZE_RANGE, 1.5, true);
	}

	/**
	 * Score self at {@code feet} with motion {@code selfDelta} against snapshot frame {@code tick}.
	 */
	public ScoreResult score(ThreatSnapshot snapshot, SelfBoxModel selfBox, Vec3 feet, Vec3 selfDelta, int tick) {
		return score(snapshot, selfBox, feet, selfDelta, tick, null);
	}

	/**
	 * Same as {@link #score(ThreatSnapshot, SelfBoxModel, Vec3, Vec3, int)} with optional wall-clearance bias from state.
	 */
	public ScoreResult score(ThreatSnapshot snapshot, SelfBoxModel selfBox, Vec3 feet, Vec3 selfDelta, int tick,
	                         @Nullable PilotState state) {
		List<Threat> threats = snapshot.threats();
		if (threats.isEmpty()) {
			// No threats → fully safe → full wall-space preference
			double wall = wallClearanceScore(state, selfBox, feet.add(selfDelta), 1.0);
			return ScoreResult.safe(wall, Double.POSITIVE_INFINITY, 0, -1);
		}

		// Broadphase pad must cover mid-range projectiles (arrows spawn ~8–16m out).
		// Old pad (~2) made score() report clr=Infinity while snap still held the arrow,
		// so ground takeover never entered search and APF/search thought it was safe.
		double pad = Math.max(16.0, Math.max(grazeBand, 2.0) + selfDelta.length() + 4.0);
		AABB queryBody = selfBox.bodyAt(feet).inflate(pad);
		Set<Integer> seen = new HashSet<>();
		double minClear = Double.POSITIVE_INFINITY;
		int grazes = 0;
		int nearestId = -1;
		double scoreSum = 0;
		int count = 0;

		SpatioTemporalHash hash = snapshot.broadphase();
		// Small threat sets: skip hash (cheap full scan, avoids cell-edge misses)
		if (hash != null && threats.size() > 12) {
			hash.query(tick, queryBody, idx -> seen.add(idx));
		} else {
			for (int i = 0; i < threats.size(); i++) seen.add(i);
		}

		for (int idx : seen) {
			Threat threat = threats.get(idx);
			ThreatFrame[] frames = threat.frames();
			if (tick >= frames.length) continue;
			ThreatFrame f = frames[tick];
			if (!f.active()) continue;

			// Per-threat self box: shrink only for DANMAKU, full box for VANILLA arrows
			AABB hard = selfBox.hitBoxAt(feet, threat.semantic());

			Vec3 threatDelta = Vec3.ZERO;
			if (tick + 1 < frames.length) {
				threatDelta = frames[tick + 1].position().subtract(f.position());
			}

			AABB threatBox = f.bounds();
			double hitT = -1;
			if (useSweep) {
				if (f.isLaser() && f.orientation() != null) {
					hitT = SweptCollision.sweptLaser(hard, selfDelta, f.position(), f.orientation(),
							f.length(), f.hitRadius(), threatDelta);
				} else {
					hitT = SweptCollision.sweptHit(hard, selfDelta, threatBox, threatDelta);
				}
			}
			if (hitT >= 0) {
				return ScoreResult.dead(threat.entityId());
			}

			// Static clearance at end of step (self moved)
			AABB hardEnd = hard.move(selfDelta);
			// Lasers: clearance to segment AABB is conservative but usable
			double clear = SweptCollision.clearance(hardEnd, threatBox.move(threatDelta));
			if (f.isLaser() && f.orientation() != null) {
				// Prefer true segment distance for minClear reporting
				Vec3 end = f.position().add(f.orientation().normalize().scale(f.length())).add(threatDelta);
				Vec3 c = SweptCollision.center(hardEnd);
				clear = distPointToSegment(c, f.position().add(threatDelta), end) - f.hitRadius()
						- Math.max(hardEnd.getXsize(), Math.max(hardEnd.getYsize(), hardEnd.getZsize())) * 0.5;
			}
			if (clear < minClear) {
				minClear = clear;
				nearestId = threat.entityId();
			}
			if (clear <= 0) {
				return ScoreResult.dead(threat.entityId());
			}
			if (clear < grazeBand) {
				grazes++;
			}
			// Exponential penalty for near threats
			scoreSum += -Math.exp(-clear / falloff);
			count++;
		}

		double score = count == 0 ? 0 : scoreSum / count;
		// Bonus for large min clearance
		if (minClear < Double.POSITIVE_INFINITY) {
			score += Math.min(2.0, minClear * 0.1);
		}
		// Wall-space bias only when threat clearance is comfortable
		double safety = state != null ? state.wallSafetyFactor(minClear) : 0;
		score += wallClearanceScore(state, selfBox, feet.add(selfDelta), safety);
		return ScoreResult.safe(score, minClear, grazes, nearestId);
	}

	private static double distPointToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double denom = ab.lengthSqr();
		if (denom < 1e-12) return p.distanceTo(a);
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / denom));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	/**
	 * Negative score when near solids. Scaled by {@code safety} (0 under fire → 1 when safe)
	 * so necessary dodge paths are not discarded for wall distance.
	 */
	private static double wallClearanceScore(@Nullable PilotState state, SelfBoxModel box, Vec3 feet, double safety) {
		if (safety <= 1e-4 || state == null || state.wallClearanceRadius <= 0 || state.wallClearanceGain <= 0) {
			return 0;
		}
		double radius = state.wallClearanceRadius;
		double gain = state.wallClearanceGain * safety;
		double step = 0.25;
		double penalty = 0;
		Vec3[] dirs = {
				new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
				new Vec3(0, 1, 0), new Vec3(0, -1, 0),
				new Vec3(0, 0, 1), new Vec3(0, 0, -1)
		};
		for (Vec3 dir : dirs) {
			double free = radius;
			for (double d = step; d <= radius + 1e-6; d += step) {
				// Terrain uses full body box, not danmaku-shrunk hitbox
				if (!state.oracle.isFree(box.bodyAt(feet.add(dir.scale(d))))) {
					free = d;
					break;
				}
			}
			if (free < radius) {
				// Mild penalty — secondary to threat terms
				penalty -= gain * (radius - free) / radius * 0.35;
			}
		}
		// Preview arenas have no world solids in their oracle. Apply the same
		// safety-gated penalty to their virtual faces so search prefers interior
		// paths instead of riding the hard boundary.
		penalty += state.arenaClearancePenalty(feet) * safety;
		return penalty;
	}
}
