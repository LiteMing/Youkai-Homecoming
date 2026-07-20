package dev.xkmc.youkaishomecoming.content.spell.pilot;

import dev.xkmc.youkaishomecoming.content.spell.pilot.apf.PotentialFieldSolver;
import dev.xkmc.youkaishomecoming.content.spell.pilot.debug.PilotDeathReplay;
import dev.xkmc.youkaishomecoming.content.spell.pilot.debug.PilotDebugFrame;
import dev.xkmc.youkaishomecoming.content.spell.pilot.debug.PilotDebugView;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.FreeFlightModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.SpatioTemporalSearch;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ScoreResult;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.Vec3;

/**
 * Pilot facade: each tick returns desired velocity (speed-direct control).
 * APF default; spatiotemporal search engages under clearance hysteresis.
 */
public final class DodgePilot {

	private final PilotProfile profile;
	private final PotentialFieldSolver apf = new PotentialFieldSolver();
	private final NodeScorer scorer;
	private final SpatioTemporalSearch search;
	private final PilotDeathReplay replay = new PilotDeathReplay();
	private final PilotDebugView debugView = new PilotDebugView();
	private boolean searchMode;
	private Vec3 lastVel = Vec3.ZERO;
	private long lastTickNanos;
	private int lastSearchNodes;
	private double lastClearance = Double.POSITIVE_INFINITY;
	private boolean lastHardHit;

	public DodgePilot(PilotProfile profile) {
		this.profile = profile;
		this.scorer = new NodeScorer(profile.grazeBand(), 1.5, true);
		this.search = new SpatioTemporalSearch(new FreeFlightModel(), scorer);
	}

	public PilotProfile profile() {
		return profile;
	}

	public long lastTickNanos() {
		return lastTickNanos;
	}

	public boolean searchMode() {
		return searchMode;
	}

	public PilotDeathReplay replay() {
		return replay;
	}

	public PilotDebugView debugView() {
		return debugView;
	}

	/**
	 * @return desired velocity for this tick (consumer integrates / applies)
	 */
	public Vec3 tick(ThreatSnapshot snapshot, PilotState state) {
		long t0 = System.nanoTime();
		if (state.deadlineNanos == 0 && profile.timeBudgetNanos() > 0) {
			state.deadlineNanos = t0 + profile.timeBudgetNanos();
		}

		ScoreResult now = scorer.score(snapshot, state.selfBox, state.feet, state.velocity, 0);
		lastClearance = now.minClearance();
		lastHardHit = now.hardHit();
		if (now.hardHit() || now.minClearance() < profile.searchEnterClearance()) {
			searchMode = true;
		} else if (now.minClearance() > profile.searchExitClearance()) {
			searchMode = false;
		}

		Vec3 desired;
		lastSearchNodes = 0;
		if (searchMode && !state.timedOut()) {
			SpatioTemporalSearch.Result sr = search.search(snapshot, state, profile);
			lastSearchNodes = sr.nodesExpanded();
			if (sr.firstStep().lengthSqr() > 1e-10) {
				desired = sr.firstStep();
			} else {
				desired = apf.solve(snapshot, state, profile);
			}
		} else {
			desired = apf.solve(snapshot, state, profile);
		}

		if (state.arena != null && desired.lengthSqr() > 1e-8) {
			Vec3 next = state.feet.add(desired);
			if (!state.arena.contains(next)) {
				Vec3 c = new Vec3(
						(state.arena.minX + state.arena.maxX) * 0.5,
						(state.arena.minY + state.arena.maxY) * 0.5,
						(state.arena.minZ + state.arena.maxZ) * 0.5
				);
				Vec3 inward = c.subtract(state.feet);
				if (inward.lengthSqr() > 1e-8) {
					desired = inward.normalize().scale(Math.min(profile.highSpeed(), inward.length() * 0.5));
				} else {
					desired = Vec3.ZERO;
				}
			}
		}

		if (desired.lengthSqr() > 1e-8) {
			var nextBox = state.selfBox.hardAt(state.feet.add(desired));
			if (!state.oracle.isFree(nextBox)) {
				desired = Vec3.ZERO;
			}
		}

		lastVel = desired;
		state.velocity = desired;
		lastTickNanos = System.nanoTime() - t0;

		// Debug + death buffer (always on; overlay gated by debugView.enabled)
		Vec3 force = apf.lastForce();
		if (debugView.enabled) {
			debugView.updateFrom(snapshot, state.feet, desired, force, state.anchor,
					searchMode, lastSearchNodes, lastClearance);
		}
		replay.push(new PilotDebugFrame(
				state.tick, state.feet, desired, force, lastClearance,
				searchMode, lastSearchNodes, snapshot.size(), lastHardHit, lastTickNanos
		));

		return desired;
	}

	public void reset() {
		apf.reset();
		searchMode = false;
		lastVel = Vec3.ZERO;
		lastSearchNodes = 0;
		lastClearance = Double.POSITIVE_INFINITY;
		lastHardHit = false;
		replay.clear();
		debugView.clear();
	}

	public Vec3 lastVelocity() {
		return lastVel;
	}

	public NodeScorer scorer() {
		return scorer;
	}

	public int lastSearchNodes() {
		return lastSearchNodes;
	}

	public double lastClearance() {
		return lastClearance;
	}

	public boolean lastHardHit() {
		return lastHardHit;
	}
}
