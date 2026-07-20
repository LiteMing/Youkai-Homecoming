package dev.xkmc.youkaishomecoming.content.spell.pilot.search;

import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ScoreResult;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Best-first search in (position, tick) with MaxiMin path scores and node budget.
 * On total death: pick branch that survived most ticks.
 */
public final class SpatioTemporalSearch {

	private final ActionModel model;
	private final NodeScorer scorer;

	public SpatioTemporalSearch(ActionModel model, NodeScorer scorer) {
		this.model = model;
		this.scorer = scorer;
	}

	public record Result(Vec3 firstStep, double score, int nodesExpanded, boolean foundAlive) {
	}

	public Result search(ThreatSnapshot snapshot, PilotState state, PilotProfile profile) {
		int depthLimit = profile.searchDepth();
		int budget = profile.nodeBudget();
		PriorityQueue<PilotSearchNode> open = new PriorityQueue<>(
				Comparator.comparingDouble((PilotSearchNode n) -> -n.pathScore)
						.thenComparingInt(n -> n.depth)
		);

		PilotSearchNode root = PilotSearchNode.root(state.feet, state.velocity);
		open.add(root);

		PilotSearchNode bestAlive = null;
		PilotSearchNode longestSurvive = root;
		int expanded = 0;

		while (!open.isEmpty() && expanded < budget) {
			if (state.timedOut()) break;
			PilotSearchNode cur = open.poll();
			expanded++;

			if (cur.depth >= depthLimit) {
				if (!cur.dead && (bestAlive == null || cur.pathScore > bestAlive.pathScore)) {
					bestAlive = cur;
				}
				continue;
			}

			List<ActionModel.Action> acts = model.actions(cur, profile.highSpeed(), profile.lowSpeed());
			boolean anyChildAlive = false;
			double bestChildScore = Double.NEGATIVE_INFINITY;

			for (ActionModel.Action act : acts) {
				Vec3 nextFeet = cur.feet.add(act.velocity());
				if (state.arena != null && !state.arena.contains(nextFeet)) {
					// Soft clamp: skip out-of-arena
					continue;
				}
				var nextBox = state.selfBox.hardAt(nextFeet);
				if (!state.oracle.isFree(nextBox)) continue;

				int tick = Math.min(cur.depth, snapshot.horizon() - 1);
				ScoreResult sc = scorer.score(snapshot, state.selfBox, cur.feet, act.velocity(), tick);
				boolean dead = sc.hardHit() || !sc.isAlive();
				// MaxiMin: path score = min(parent path, this node safety)
				double nodeSafety = dead ? Double.NEGATIVE_INFINITY : sc.score() + sc.minClearance();
				double pathScore = Math.min(cur.pathScore, nodeSafety);

				Vec3 first = cur.depth == 0 ? act.velocity() : cur.firstStepVel;
				PilotSearchNode child = new PilotSearchNode(
						nextFeet, act.velocity(), cur.depth + 1, act.dirId(), act.highSpeed(),
						pathScore, dead, cur, first
				);

				if (!dead) {
					anyChildAlive = true;
					bestChildScore = Math.max(bestChildScore, pathScore);
					open.add(child);
					if (child.depth > longestSurvive.depth
							|| (child.depth == longestSurvive.depth && child.pathScore > longestSurvive.pathScore)) {
						longestSurvive = child;
					}
				} else if (child.depth > longestSurvive.depth) {
					longestSurvive = child;
				}
			}

			// Leaf-ish: if no expansion and alive, consider as candidate
			if (!anyChildAlive && !cur.dead) {
				if (bestAlive == null || cur.pathScore > bestAlive.pathScore) {
					bestAlive = cur;
				}
			}
		}

		// Drain remaining open for best leaf
		while (!open.isEmpty()) {
			PilotSearchNode n = open.poll();
			if (!n.dead && (bestAlive == null || n.pathScore > bestAlive.pathScore)) {
				bestAlive = n;
			}
		}

		if (bestAlive != null && bestAlive.depth > 0) {
			return new Result(bestAlive.firstStepVel, bestAlive.pathScore, expanded, true);
		}
		// All dead: struggle with longest survival first step
		if (longestSurvive.depth > 0) {
			return new Result(longestSurvive.firstStepVel, longestSurvive.pathScore, expanded, false);
		}
		return new Result(Vec3.ZERO, Double.NEGATIVE_INFINITY, expanded, false);
	}
}
