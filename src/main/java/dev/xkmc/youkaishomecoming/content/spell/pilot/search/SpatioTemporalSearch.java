package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ScoreResult;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Best-first time-space search with deterministic continuity and emergency refinement. */
public final class SpatioTemporalSearch {

	private static final double REFINEMENT_HALF_ANGLE = Math.toRadians(40);
	private static final double EARLY_EXIT_CLEARANCE = 0.25;

	private final ActionModel model;
	private final NodeScorer scorer;

	public SpatioTemporalSearch(ActionModel model, NodeScorer scorer) {
		this.model = model;
		this.scorer = scorer;
	}

	public record Result(Vec3 firstStep, double score, int nodesExpanded,
	                     boolean foundAlive, boolean refined) {
	}

	public Result search(ThreatSnapshot snapshot, PilotState state, PilotProfile profile) {
		int depthLimit = Math.min(profile.searchDepth(), snapshot.horizon());
		int budget = profile.nodeBudget();
		Comparator<PilotSearchNode> order = Comparator
				.comparingDouble((PilotSearchNode node) -> -node.pathScore)
				.thenComparingInt(node -> -node.depth)
				.thenComparingDouble(node -> -continuity(node.firstStepVel, state.continuityPreference))
				.thenComparingInt(node -> node.dirId);
		PriorityQueue<PilotSearchNode> open = new PriorityQueue<>(order);

		PilotSearchNode root = PilotSearchNode.root(state.feet, state.velocity);
		open.add(root);
		PilotSearchNode bestComplete = null;
		PilotSearchNode bestPartial = root;
		int expanded = 0;

		while (!open.isEmpty() && expanded < budget && !state.timedOut()) {
			PilotSearchNode current = open.poll();
			expanded++;
			if (current.depth >= depthLimit) {
				if (betterComplete(current, bestComplete, state)) bestComplete = current;
				continue;
			}

			List<ActionModel.Action> actions = model.actions(current,
					profile.highSpeed(), profile.lowSpeed());
			for (ActionModel.Action action : actions) {
				Vec3 nextFeet = current.feet.add(action.velocity());
				if (!terrainAllows(state, current.feet, nextFeet, action.velocity())) continue;

				int tick = Math.min(current.depth, snapshot.horizon() - 1);
				ScoreResult score = scorer.score(snapshot, state.selfBox, current.feet,
						action.velocity(), tick, state);
				if (score.hardHit() || !score.isAlive()) continue;

				double clearance = Double.isFinite(score.minClearance())
						? Math.min(4.0, score.minClearance()) : 4.0;
				double nodeSafety = score.score() + clearance
						+ state.navigationScore(nextFeet, action.velocity());
				double pathScore = Math.min(current.pathScore, nodeSafety);
				Vec3 first = current.depth == 0 ? action.velocity() : current.firstStepVel;
				PilotSearchNode child = new PilotSearchNode(
						nextFeet, action.velocity(), current.depth + 1, action.dirId(), action.highSpeed(),
						pathScore, false, current, first);
				open.add(child);
				if (betterPartial(child, bestPartial, state)) bestPartial = child;
			}
		}

		if (bestComplete != null && bestComplete.depth > 0) {
			return new Result(bestComplete.firstStepVel, bestComplete.pathScore, expanded, true, false);
		}

		Vec3 fallback = bestPartial.depth > 0 ? bestPartial.firstStepVel : Vec3.ZERO;
		if (expanded < budget && !state.timedOut()) {
			Result refined = refineBlockedDirections(snapshot, state, profile, fallback, expanded);
			if (refined != null) return refined;
		}
		return new Result(fallback, bestPartial.pathScore, expanded, false, false);
	}

	private Result refineBlockedDirections(ThreatSnapshot snapshot, PilotState state,
	                                       PilotProfile profile, Vec3 fallback, int expanded) {
		int horizon = Math.min(profile.searchDepth(), snapshot.horizon());
		int budget = profile.nodeBudget();
		CorridorEvaluator.Result bestBase = null;
		boolean baseHasSafeRoute = false;
		for (Vec3 direction : model.directionSeeds()) {
			if (expanded >= budget || state.timedOut()) break;
			CorridorEvaluator.Result result = CorridorEvaluator.evaluate(snapshot, state, scorer,
					direction.scale(profile.highSpeed()), horizon);
			expanded++;
			if (result.collisionFree()) baseHasSafeRoute = true;
			if (result.betterEscapeThan(bestBase)) bestBase = result;
		}
		if (bestBase == null) return null;
		if (baseHasSafeRoute) {
			return new Result(bestBase.velocity(), bestBase.score(), expanded, true, false);
		}

		Vec3 center = bestBase.velocity().lengthSqr() > 1e-10
				? bestBase.velocity().normalize()
				: fallback.lengthSqr() > 1e-10 ? fallback.normalize() : new Vec3(0, 0, 1);
		CorridorEvaluator.Result bestRefined = null;
		for (Vec3 direction : DirectionalSampler.localCone(center,
				profile.emergencyRefinementRays(), REFINEMENT_HALF_ANGLE,
				model.supportsVerticalMovement())) {
			if (expanded >= budget || state.timedOut()) break;
			CorridorEvaluator.Result result = CorridorEvaluator.evaluate(snapshot, state, scorer,
					direction.scale(profile.highSpeed()), horizon);
			expanded++;
			if (result.collisionFree() && result.minClearance() >= EARLY_EXIT_CLEARANCE) {
				return new Result(result.velocity(), result.score(), expanded, true, true);
			}
			if (result.betterEscapeThan(bestRefined)) bestRefined = result;
		}
		CorridorEvaluator.Result chosen = bestRefined != null
				&& bestRefined.betterEscapeThan(bestBase) ? bestRefined : bestBase;
		return new Result(chosen.velocity(), chosen.score(), expanded,
				chosen.collisionFree(), bestRefined == chosen);
	}

	private static boolean terrainAllows(PilotState state, Vec3 current, Vec3 next, Vec3 velocity) {
		if (state.arena != null && !state.arena.contains(next)) return false;
		var currentBody = state.selfBox.bodyAt(current);
		var nextBody = state.selfBox.bodyAt(next);
		if (!state.oracle.isPathFree(currentBody, velocity) || !state.oracle.isFree(nextBody)) return false;
		if (!state.grounded) return true;
		boolean supported = state.oracle.isSupported(currentBody);
		if (velocity.y > 1.0e-8) return supported;
		return !supported || state.oracle.isSupported(nextBody);
	}

	private static boolean betterComplete(PilotSearchNode candidate, PilotSearchNode current,
	                                      PilotState state) {
		if (current == null) return true;
		int score = Double.compare(candidate.pathScore, current.pathScore);
		if (score != 0) return score > 0;
		return continuity(candidate.firstStepVel, state.continuityPreference)
				> continuity(current.firstStepVel, state.continuityPreference);
	}

	private static boolean betterPartial(PilotSearchNode candidate, PilotSearchNode current,
	                                     PilotState state) {
		if (current == null || candidate.depth != current.depth) return current == null || candidate.depth > current.depth;
		return betterComplete(candidate, current, state);
	}

	private static double continuity(Vec3 candidate, Vec3 previous) {
		if (candidate.lengthSqr() < 1e-10 || previous.lengthSqr() < 1e-10) return 0;
		return candidate.normalize().dot(previous.normalize());
	}
}
