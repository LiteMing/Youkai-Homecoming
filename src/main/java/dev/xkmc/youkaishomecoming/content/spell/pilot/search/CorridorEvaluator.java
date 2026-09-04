package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ScoreResult;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.Vec3;

/** Evaluates the swept self body along one constant-velocity time-space corridor. */
public final class CorridorEvaluator {

	private CorridorEvaluator() {
	}

	public record Result(Vec3 velocity, int survivalTicks, double freeDistance,
	                     double minClearance, double score, boolean collisionFree) {

		public boolean betterEscapeThan(Result other) {
			if (other == null) return true;
			if (survivalTicks != other.survivalTicks) return survivalTicks > other.survivalTicks;
			int distance = Double.compare(freeDistance, other.freeDistance);
			if (distance != 0) return distance > 0;
			int clearance = Double.compare(minClearance, other.minClearance);
			if (clearance != 0) return clearance > 0;
			return score > other.score;
		}
	}

	public static Result evaluate(ThreatSnapshot snapshot, PilotState state, NodeScorer scorer,
	                              Vec3 velocity, int requestedTicks) {
		int ticks = Math.max(1, Math.min(requestedTicks, snapshot.horizon()));
		Vec3 position = state.feet;
		double minClearance = Double.POSITIVE_INFINITY;
		double pathScore = Double.POSITIVE_INFINITY;
		int survived = 0;
		double freeDistance = 0;

		for (int tick = 0; tick < ticks; tick++) {
			if (state.timedOut()) break;
			Vec3 next = position.add(velocity);
			if (state.arena != null && !state.arena.contains(next)) break;
			if (!state.oracle.isPathFree(state.selfBox.bodyAt(position), velocity)
					|| !state.oracle.isFree(state.selfBox.bodyAt(next))) break;

			ScoreResult score = scorer.score(snapshot, state.selfBox, position, velocity, tick, state);
			if (score.hardHit() || !score.isAlive()) {
				double hitFraction = Double.isFinite(score.hitTime())
						? Math.max(0, Math.min(1, score.hitTime())) : 0;
				freeDistance += velocity.length() * hitFraction;
				return new Result(velocity, survived, freeDistance, -1, pathScore, false);
			}

			survived++;
			freeDistance += velocity.length();
			minClearance = Math.min(minClearance, score.minClearance());
			double clearance = Double.isFinite(score.minClearance())
					? Math.min(4.0, score.minClearance()) : 4.0;
			pathScore = Math.min(pathScore,
					score.score() + clearance + state.navigationScore(next, velocity));
			position = next;
		}

		boolean complete = survived == ticks;
		return new Result(velocity, survived, freeDistance, minClearance, pathScore, complete);
	}
}
