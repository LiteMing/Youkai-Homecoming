package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
		List<Threat> threats = snapshot.threats();
		if (threats.isEmpty()) {
			return ScoreResult.safe(0, Double.POSITIVE_INFINITY, 0, -1);
		}

		AABB hard = selfBox.hardAt(feet);
		AABB query = hard.inflate(Math.max(grazeBand, 2.0) + selfDelta.length());
		Set<Integer> seen = new HashSet<>();
		double minClear = Double.POSITIVE_INFINITY;
		int grazes = 0;
		int nearestId = -1;
		double scoreSum = 0;
		int count = 0;

		SpatioTemporalHash hash = snapshot.broadphase();
		if (hash != null) {
			hash.query(tick, query, idx -> seen.add(idx));
		} else {
			for (int i = 0; i < threats.size(); i++) seen.add(i);
		}

		for (int idx : seen) {
			Threat threat = threats.get(idx);
			ThreatFrame[] frames = threat.frames();
			if (tick >= frames.length) continue;
			ThreatFrame f = frames[tick];
			if (!f.active()) continue;

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
			double clear = SweptCollision.clearance(hardEnd, threatBox.move(threatDelta));
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
		return ScoreResult.safe(score, minClear, grazes, nearestId);
	}
}
