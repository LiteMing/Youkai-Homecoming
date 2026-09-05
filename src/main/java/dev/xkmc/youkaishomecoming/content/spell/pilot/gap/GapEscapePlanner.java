package dev.xkmc.youkaishomecoming.content.spell.pilot.gap;

import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.ActionModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.CorridorEvaluator;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.DirectionalSampler;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.Vec3;

/** Low-frequency broad-space guidance. Close-range escape remains search-owned. */
public final class GapEscapePlanner {

	private static final double CLEARANCE_IMPROVEMENT = 0.6;

	private Vec3 cachedDirection = Vec3.ZERO;
	private int nextRefreshTick;

	public Vec3 update(ThreatSnapshot snapshot, PilotState state, PilotProfile profile,
	                   NodeScorer scorer, ActionModel model) {
		if (state.tick < nextRefreshTick) return cachedDirection;
		nextRefreshTick = state.tick + profile.gapRefreshTicks();

		int horizon = Math.min(snapshot.horizon(), Math.max(5, profile.searchDepth() + 2));
		Vec3 currentVelocity = state.velocity.lengthSqr() > 1e-10 ? state.velocity : Vec3.ZERO;
		CorridorEvaluator.Result current = CorridorEvaluator.evaluate(
				snapshot, state, scorer, currentVelocity, horizon);
		CorridorEvaluator.Result best = current;

		for (Vec3 direction : DirectionalSampler.global(profile.directionRays(),
				model.supportsVerticalMovement())) {
			if (state.timedOut()) break;
			Vec3 velocity = direction.scale(profile.lowSpeed());
			CorridorEvaluator.Result candidate = CorridorEvaluator.evaluate(
					snapshot, state, scorer, velocity, horizon);
			if (candidate.betterEscapeThan(best)) best = candidate;
		}

		boolean currentBlocked = !current.collisionFree();
		boolean materiallySafer = best != null && (
				best.survivalTicks() > current.survivalTicks()
						|| best.minClearance() > current.minClearance() + CLEARANCE_IMPROVEMENT);
		cachedDirection = best != null && best.velocity().lengthSqr() > 1e-10
				&& (currentBlocked || materiallySafer)
				? best.velocity().normalize() : Vec3.ZERO;
		return cachedDirection;
	}

	public void reset() {
		cachedDirection = Vec3.ZERO;
		nextRefreshTick = 0;
	}
}
