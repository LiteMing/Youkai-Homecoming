package dev.xkmc.youkaishomecoming.content.spell.pilot;

import dev.xkmc.youkaishomecoming.content.spell.pilot.apf.PotentialFieldSolver;
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
 * <p>
 * Special J (jitter): wider hysteresis + min search dwell; exposes flip/mode rates.
 */
public final class DodgePilot {

	/** Adjacent-tick desired velocity angle &gt; 90° counts as a flip (special J metric). */
	private static final double FLIP_DOT = 0.0;
	/** Sliding window for rates (ticks ≈ seconds/20). */
	private static final int RATE_WINDOW = 20;
	/**
	 * Min ticks to stay in SEARCH after enter (special J step 5).
	 * Stops APF↔SEARCH ping-pong when a sweeping laser wiggles minClearance.
	 */
	private static final int SEARCH_MIN_DWELL = 5;

	private final PilotProfile profile;
	private final PotentialFieldSolver apf = new PotentialFieldSolver();
	private final NodeScorer scorer;
	private final SpatioTemporalSearch search;
	private final PilotDebugView debugView = new PilotDebugView();
	private boolean searchMode;
	private int searchDwellLeft;
	private Vec3 lastVel = Vec3.ZERO;
	private long lastTickNanos;
	private int lastSearchNodes;
	private double lastClearance = Double.POSITIVE_INFINITY;
	private boolean lastHardHit;

	// --- special J metrics (rolling 1s window at 20 TPS) ---
	private int windowTicks;
	private int windowFlips;
	private int windowModeSwitches;
	private double flipRatePerSec;
	private double modeSwitchRatePerSec;

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

	public PilotDebugView debugView() {
		return debugView;
	}

	/** Desired-velocity reversals (&gt;90°) per second (rolling ~1s). Special J. */
	public double flipRatePerSec() {
		return flipRatePerSec;
	}

	/** APF↔SEARCH mode switches per second (rolling ~1s). Special J. */
	public double modeSwitchRatePerSec() {
		return modeSwitchRatePerSec;
	}

	/**
	 * @return desired velocity for this tick (consumer integrates / applies)
	 */
	public Vec3 tick(ThreatSnapshot snapshot, PilotState state) {
		long t0 = System.nanoTime();
		if (state.deadlineNanos == 0 && profile.timeBudgetNanos() > 0) {
			state.deadlineNanos = t0 + profile.timeBudgetNanos();
		}

		ScoreResult now = scorer.score(snapshot, state.selfBox, state.feet, state.velocity, 0, state);
		lastClearance = now.minClearance();
		lastHardHit = now.hardHit();

		boolean wasSearch = searchMode;
		if (searchDwellLeft > 0) {
			searchDwellLeft--;
		}
		if (now.hardHit() || now.minClearance() < profile.searchEnterClearance()) {
			if (!searchMode) {
				searchMode = true;
				searchDwellLeft = SEARCH_MIN_DWELL;
			}
		} else if (searchDwellLeft <= 0 && now.minClearance() > profile.searchExitClearance()) {
			searchMode = false;
		}
		if (searchMode != wasSearch) {
			windowModeSwitches++;
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
			var currentBox = state.selfBox.bodyAt(state.feet);
			var nextBox = state.selfBox.bodyAt(state.feet.add(desired));
			if (!state.oracle.isPathFree(currentBox, desired) || !state.oracle.isFree(nextBox)) {
				desired = Vec3.ZERO;
			}
		}

		// Special J: count velocity direction flips (dot < 0 ⇒ angle > 90°)
		if (lastVel.lengthSqr() > 1e-8 && desired.lengthSqr() > 1e-8) {
			if (lastVel.normalize().dot(desired.normalize()) < FLIP_DOT) {
				windowFlips++;
			}
		}
		windowTicks++;
		if (windowTicks >= RATE_WINDOW) {
			// RATE_WINDOW ticks ≈ 1 second at 20 TPS
			flipRatePerSec = windowFlips * (20.0 / RATE_WINDOW);
			modeSwitchRatePerSec = windowModeSwitches * (20.0 / RATE_WINDOW);
			windowTicks = 0;
			windowFlips = 0;
			windowModeSwitches = 0;
		}

		lastVel = desired;
		state.velocity = desired;
		lastTickNanos = System.nanoTime() - t0;

		// Overlay debug only when enabled (no death-replay ring buffer)
		if (debugView.enabled) {
			debugView.updateFrom(snapshot, state.feet, desired, apf.lastForce(), state.anchor,
					searchMode, lastSearchNodes, lastClearance);
		}

		return desired;
	}

	public void reset() {
		apf.reset();
		searchMode = false;
		searchDwellLeft = 0;
		lastVel = Vec3.ZERO;
		lastSearchNodes = 0;
		lastClearance = Double.POSITIVE_INFINITY;
		lastHardHit = false;
		windowTicks = 0;
		windowFlips = 0;
		windowModeSwitches = 0;
		flipRatePerSec = 0;
		modeSwitchRatePerSec = 0;
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
