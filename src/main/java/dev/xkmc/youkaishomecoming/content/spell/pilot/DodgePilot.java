package dev.xkmc.youkaishomecoming.content.spell.pilot;

import dev.xkmc.youkaishomecoming.content.spell.pilot.apf.PotentialFieldSolver;
import dev.xkmc.youkaishomecoming.content.spell.pilot.debug.PilotDebugView;
import dev.xkmc.youkaishomecoming.content.spell.pilot.gap.GapEscapePlanner;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.ActionModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.CorridorEvaluator;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.FreeFlightModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.SpatioTemporalSearch;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.NodeScorer;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ScoreResult;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.ThreatSnapshot;
import net.minecraft.world.phys.Vec3;

/** Shared layered controller for preview, players and live entities. */
public final class DodgePilot {

	private static final int RATE_WINDOW = 20;
	private static final int SEARCH_MIN_DWELL = 5;
	private static final double PLAN_REPLACE_MARGIN = 0.35;

	private final PilotProfile profile;
	private final ActionModel actionModel;
	private final PotentialFieldSolver apf = new PotentialFieldSolver();
	private final NodeScorer scorer;
	private final SpatioTemporalSearch search;
	private final GapEscapePlanner gapPlanner = new GapEscapePlanner();
	private final PilotDebugView debugView = new PilotDebugView();

	private boolean searchMode;
	private int searchDwellLeft;
	private Vec3 lastVel = Vec3.ZERO;
	private Vec3 committedVelocity = Vec3.ZERO;
	private int commitTicksLeft;
	private long lastTickNanos;
	private int lastSearchNodes;
	private double lastClearance = Double.POSITIVE_INFINITY;
	private boolean lastHardHit;
	private boolean lastRefined;

	private int windowTicks;
	private int windowFlips;
	private int windowModeSwitches;
	private double flipRatePerSec;
	private double modeSwitchRatePerSec;

	public DodgePilot(PilotProfile profile) {
		this(profile, new FreeFlightModel());
	}

	public DodgePilot(PilotProfile profile, ActionModel actionModel) {
		this.profile = profile;
		this.actionModel = actionModel;
		this.scorer = new NodeScorer(profile.grazeBand(), 1.5, true);
		this.search = new SpatioTemporalSearch(actionModel, scorer);
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

	public double flipRatePerSec() {
		return flipRatePerSec;
	}

	public double modeSwitchRatePerSec() {
		return modeSwitchRatePerSec;
	}

	public Vec3 tick(ThreatSnapshot snapshot, PilotState state) {
		long started = System.nanoTime();
		if (state.deadlineNanos == 0 && profile.timeBudgetNanos() > 0) {
			state.deadlineNanos = started + profile.timeBudgetNanos();
		}
		state.continuityPreference = committedVelocity.lengthSqr() > 1e-10
				? committedVelocity : lastVel;

		ScoreResult now = scorer.score(snapshot, state.selfBox, state.feet, state.velocity, 0, state);
		lastClearance = now.minClearance();
		lastHardHit = now.hardHit();
		CorridorEvaluator.Result currentCourse = CorridorEvaluator.evaluate(snapshot, state, scorer,
				state.velocity, Math.min(profile.searchDepth(), snapshot.horizon()));

		boolean wasSearch = searchMode;
		if (searchDwellLeft > 0) searchDwellLeft--;
		boolean predictedDanger = now.hardHit() || !currentCourse.collisionFree()
				|| currentCourse.minClearance() < profile.searchEnterClearance();
		if (predictedDanger) {
			if (!searchMode) searchDwellLeft = SEARCH_MIN_DWELL;
			searchMode = true;
		} else if (searchDwellLeft <= 0 && now.minClearance() > profile.searchExitClearance()) {
			searchMode = false;
		}
		if (searchMode != wasSearch) windowModeSwitches++;

		boolean directedByPlayer = state.inputPreference.lengthSqr() > 1e-10;
		if (!searchMode && !directedByPlayer && !state.timedOut()) {
			state.gapPreference = gapPlanner.update(snapshot, state, profile, scorer, actionModel);
		} else {
			state.gapPreference = Vec3.ZERO;
		}

		Vec3 desired;
		lastSearchNodes = 0;
		lastRefined = false;
		boolean planned = (searchMode || directedByPlayer) && !state.timedOut();
		if (planned) {
			SpatioTemporalSearch.Result result = search.search(snapshot, state, profile);
			lastSearchNodes = result.nodesExpanded();
			lastRefined = result.refined();
			desired = stabilizePlan(result.firstStep(), result.score(), currentCourse, state);
			if (desired.lengthSqr() < 1e-10) desired = apf.solve(snapshot, state, profile);
		} else {
			desired = apf.solve(snapshot, state, profile);
			commitTicksLeft = 0;
			committedVelocity = Vec3.ZERO;
		}

		desired = clampToArena(state, desired);
		if (!pathIsFree(state, desired)) {
			desired = Vec3.ZERO;
			commitTicksLeft = 0;
			committedVelocity = Vec3.ZERO;
		}

		recordMetrics(desired);
		lastVel = desired;
		state.velocity = desired;
		lastTickNanos = System.nanoTime() - started;
		if (debugView.enabled) {
			debugView.updateFrom(snapshot, state.feet, desired, apf.lastForce(), state.anchor,
					searchMode, lastSearchNodes, lastClearance);
		}
		return desired;
	}

	private Vec3 stabilizePlan(Vec3 candidate, double candidateScore,
	                           CorridorEvaluator.Result currentCourse, PilotState state) {
		if (commitTicksLeft > 0 && committedVelocity.lengthSqr() > 1e-10
				&& currentCourse.collisionFree()) {
			double currentScore = currentCourse.score();
			boolean playerPrefersCandidate = state.inputPreference.lengthSqr() > 1e-10
					&& alignment(candidate, state.inputPreference)
					> alignment(committedVelocity, state.inputPreference) + 0.2;
			if (!playerPrefersCandidate && candidateScore < currentScore + PLAN_REPLACE_MARGIN) {
				commitTicksLeft--;
				return committedVelocity;
			}
		}
		if (candidate.lengthSqr() > 1e-10) {
			committedVelocity = candidate;
			commitTicksLeft = profile.planCommitTicks();
		} else {
			committedVelocity = Vec3.ZERO;
			commitTicksLeft = 0;
		}
		return candidate;
	}

	private static Vec3 clampToArena(PilotState state, Vec3 desired) {
		if (state.arena == null || desired.lengthSqr() <= 1e-10) return desired;
		Vec3 next = state.feet.add(desired);
		if (state.arena.contains(next)) return desired;
		Vec3 clamped = new Vec3(
				Math.max(state.arena.minX, Math.min(state.arena.maxX, next.x)),
				Math.max(state.arena.minY, Math.min(state.arena.maxY, next.y)),
				Math.max(state.arena.minZ, Math.min(state.arena.maxZ, next.z)));
		return clamped.subtract(state.feet);
	}

	private static boolean pathIsFree(PilotState state, Vec3 desired) {
		if (desired.lengthSqr() <= 1e-10) return true;
		return state.oracle.isPathFree(state.selfBox.bodyAt(state.feet), desired)
				&& state.oracle.isFree(state.selfBox.bodyAt(state.feet.add(desired)));
	}

	private void recordMetrics(Vec3 desired) {
		if (lastVel.lengthSqr() > 1e-8 && desired.lengthSqr() > 1e-8
				&& lastVel.normalize().dot(desired.normalize()) < 0) {
			windowFlips++;
		}
		windowTicks++;
		if (windowTicks < RATE_WINDOW) return;
		flipRatePerSec = windowFlips * (20.0 / RATE_WINDOW);
		modeSwitchRatePerSec = windowModeSwitches * (20.0 / RATE_WINDOW);
		windowTicks = 0;
		windowFlips = 0;
		windowModeSwitches = 0;
	}

	private static double alignment(Vec3 first, Vec3 second) {
		if (first.lengthSqr() < 1e-10 || second.lengthSqr() < 1e-10) return 0;
		return first.normalize().dot(second.normalize());
	}

	public void reset() {
		apf.reset();
		gapPlanner.reset();
		searchMode = false;
		searchDwellLeft = 0;
		lastVel = Vec3.ZERO;
		committedVelocity = Vec3.ZERO;
		commitTicksLeft = 0;
		lastSearchNodes = 0;
		lastClearance = Double.POSITIVE_INFINITY;
		lastHardHit = false;
		lastRefined = false;
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

	public boolean lastRefined() {
		return lastRefined;
	}
}
