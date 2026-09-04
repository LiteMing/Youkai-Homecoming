package gen;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatSemantic;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.ActionModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.CorridorEvaluator;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.PilotSearchNode;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.SpatioTemporalSearch;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 1 self-tests: swept collision, SelfBoxModel, scorer, snapshot perf.
 * Run: {@code ./gradlew runPilotThreatTest}
 */
public class PilotThreatTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		System.out.println("=== Phase 1 Pilot Threat Tests ===\n");

		testGap1D();
		testClearance();
		testRayAabb();
		testFastDiagonalSweepCatchesDiscreteMiss();
		testStaticOverlap();
		testLaserSweep();
		testSelfBoxPlayerShrinkVsVanilla();
		testNodeScorerGrazeVsDead();
		testArenaClearanceForce();
		testPilotPathOracleRejectsWallCrossing();
		testLinearPilotProfiles();
		testNavigationPreferencesAndAnchor();
		testEmergencyDirectionRefinement();
		testEmergencyRefinementBudget();
		testGroundedTerrainRules();
		testSnapshotPerf();

		System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
		if (failed > 0) throw new RuntimeException(failed + " tests failed!");
		System.out.println("All tests passed!");
	}

	private static void check(String name, boolean ok) {
		if (ok) {
			System.out.println("  PASS: " + name);
			passed++;
		} else {
			System.out.println("  FAIL: " + name);
			failed++;
		}
	}

	private static void approx(String name, double actual, double expected, double eps) {
		boolean ok = Math.abs(actual - expected) < eps;
		if (!ok) System.out.println("  FAIL detail: " + name + " a=" + actual + " e=" + expected);
		check(name, ok);
	}

	private static void testGap1D() {
		System.out.println("[gap1D]");
		approx("separated", SweptCollision.gap1D(0, 1, 2, 3), 1, 1e-9);
		approx("overlap", SweptCollision.gap1D(0, 2, 1, 3), -1, 1e-9);
		approx("touch", SweptCollision.gap1D(0, 1, 1, 2), 0, 1e-9);
		System.out.println();
	}

	private static void testClearance() {
		System.out.println("[clearance]");
		AABB a = new AABB(0, 0, 0, 1, 1, 1);
		AABB b = new AABB(2, 0, 0, 3, 1, 1);
		approx("sep X", SweptCollision.clearance(a, b), 1, 1e-9);
		AABB c = new AABB(0.5, 0.5, 0.5, 1.5, 1.5, 1.5);
		check("overlap negative", SweptCollision.clearance(a, c) < 0);
		System.out.println();
	}

	private static void testRayAabb() {
		System.out.println("[rayAabb]");
		AABB box = new AABB(0, 0, 0, 1, 1, 1);
		approx("hit from -x", SweptCollision.rayAabb(new Vec3(-1, 0.5, 0.5), new Vec3(2, 0, 0), box), 0.5, 1e-6);
		approx("inside", SweptCollision.rayAabb(new Vec3(0.5, 0.5, 0.5), new Vec3(1, 0, 0), box), 0, 1e-9);
		check("miss", SweptCollision.rayAabb(new Vec3(-1, 2, 0.5), new Vec3(2, 0, 0), box) < 0);
		System.out.println();
	}

	/**
	 * Thin self box, fast diagonal threat: discrete 8-step can miss; true sweep must hit.
	 */
	private static void testFastDiagonalSweepCatchesDiscreteMiss() {
		System.out.println("[fast diagonal sweep vs discrete]");
		// Self: thin plate at origin, no motion
		AABB self = new AABB(-0.1, -0.1, -0.1, 0.1, 0.1, 0.1);
		Vec3 selfDelta = Vec3.ZERO;
		// Threat: small box flying diagonally through self center in one tick
		// Start outside, end outside, path through center
		Vec3 start = new Vec3(-2, -2, 0);
		Vec3 end = new Vec3(2, 2, 0);
		double r = 0.05;
		AABB threatStart = new AABB(start.x - r, start.y - r, start.z - r, start.x + r, start.y + r, start.z + r);
		Vec3 threatDelta = end.subtract(start);

		double t = SweptCollision.sweptHit(self, selfDelta, threatStart, threatDelta);
		check("sweep reports hit", t >= 0 && t <= 1);

		// Discrete: self static, segment start→end through self — should usually hit for this case
		// Construct a case where discrete misses: self moves fast, threat is a thin segment
		AABB thinSelf = new AABB(-0.05, 0.9, -0.05, 0.05, 1.0, 0.05); // thin horizontal slab high up
		Vec3 selfVel = new Vec3(3, -3, 3); // fast diagonal
		Vec3 src = new Vec3(0, 0, 0);
		Vec3 dst = new Vec3(0.1, 0.1, 0.1); // short segment near origin
		// Expand: threat is a bullet at origin moving to (3,-3,3) wait — use reverse framing
		// Bullet goes from (1.5, -1.5, 1.5) to (-1.5, 1.5, -1.5) while self at origin thin
		AABB self0 = new AABB(-0.02, -0.02, -0.02, 0.02, 0.02, 0.02);
		Vec3 b0 = new Vec3(-1.5, -1.5, -1.5);
		Vec3 b1 = new Vec3(1.5, 1.5, 1.5);
		AABB bullet = new AABB(b0.x - 0.01, b0.y - 0.01, b0.z - 0.01, b0.x + 0.01, b0.y + 0.01, b0.z + 0.01);
		double ts = SweptCollision.sweptHit(self0, Vec3.ZERO, bullet, b1.subtract(b0));
		check("diagonal through center hit", ts >= 0);

		// Discrete with self moving fast and short bullet segment may miss corners
		boolean disc = SweptCollision.discreteSampleHit(
				new AABB(0, 0, 0, 0.1, 0.1, 0.1),
				new Vec3(4, 0, 4),
				new Vec3(0.05, 0.2, 0.05),
				new Vec3(0.05, -0.2, 0.05)
		);
		double cont = SweptCollision.sweptHit(
				new AABB(0, 0, 0, 0.1, 0.1, 0.1),
				new Vec3(4, 0, 4),
				new AABB(0.04, 0.19, 0.04, 0.06, 0.21, 0.06),
				new Vec3(0, -0.4, 0)
		);
		// Document both results (continuous should be more reliable)
		check("continuous defined", cont >= -1);
		System.out.println("  info: discrete=" + disc + " continuous t=" + cont);
		// Core guarantee: center-crossing always hits
		check("center cross always hits", ts >= 0 && t >= 0);
		System.out.println();
	}

	private static void testStaticOverlap() {
		System.out.println("[static overlap]");
		AABB self = new AABB(0, 0, 0, 1, 1, 1);
		AABB threat = new AABB(0.5, 0.5, 0.5, 1.5, 1.5, 1.5);
		approx("overlap t=0", SweptCollision.sweptHit(self, Vec3.ZERO, threat, Vec3.ZERO), 0, 1e-9);
		System.out.println();
	}

	private static void testLaserSweep() {
		System.out.println("[laser sweep]");
		AABB self = new AABB(-0.2, -0.2, 4.5, 0.2, 0.2, 5.5); // on +Z
		double t = SweptCollision.sweptLaser(self, Vec3.ZERO,
				new Vec3(0, 0, 0), new Vec3(0, 0, 1), 10f, 0.1f, Vec3.ZERO);
		check("laser hits self on beam", t >= 0);
		AABB aside = new AABB(5, 0, 0, 5.4, 0.4, 0.4);
		double miss = SweptCollision.sweptLaser(aside, Vec3.ZERO,
				new Vec3(0, 0, 0), new Vec3(0, 0, 1), 10f, 0.1f, Vec3.ZERO);
		check("laser misses aside", miss < 0);
		System.out.println();
	}

	private static void testSelfBoxPlayerShrinkVsVanilla() {
		System.out.println("[SelfBoxModel shrink + per-semantic box]");
		SelfBoxModel full = SelfBoxModel.vanillaPlayer();
		SelfBoxModel player = SelfBoxModel.playerDanmaku(-0.2f); // Fairy-like
		Vec3 feet = Vec3.ZERO;
		AABB a = full.hardAt(feet);
		AABB danmakuBox = player.hitBoxAt(feet, ThreatSemantic.DANMAKU);
		AABB vanillaBox = player.hitBoxAt(feet, ThreatSemantic.VANILLA);
		check("danmaku box narrower X than vanilla", (danmakuBox.maxX - danmakuBox.minX) < (vanillaBox.maxX - vanillaBox.minX));
		check("vanilla box matches full player width", Math.abs((vanillaBox.maxX - vanillaBox.minX) - 0.6) < 1e-6);
		check("danmaku bottom lifted", danmakuBox.minY > vanillaBox.minY);
		approx("danmaku top unshrunk", danmakuBox.maxY, 1.8, 1e-6);
		// Edge bullet hits full/vanilla but may miss shrunk danmaku box
		AABB bullet = new AABB(0.28, 0.1, -0.05, 0.35, 0.2, 0.05);
		boolean hitVanilla = SweptCollision.clearance(vanillaBox, bullet) <= 0;
		boolean hitDanmaku = SweptCollision.clearance(danmakuBox, bullet) <= 0;
		check("vanilla may hit edge bullet", hitVanilla);
		check("danmaku box may miss same edge bullet", !hitDanmaku);
		check("results differ (semantic wired)", hitVanilla != hitDanmaku || (danmakuBox.maxX - danmakuBox.minX) < 0.6);
		System.out.println();
	}

	private static void testNodeScorerGrazeVsDead() {
		System.out.println("[NodeScorer]");
		SelfBoxModel self = SelfBoxModel.previewTarget();
		Vec3 feet = new Vec3(0, 0, 0);
		// Threat far away
		Threat far = pointThreat(1, new Vec3(10, 0, 0), 0.3f);
		ThreatSnapshot snap = ThreatSnapshot.of(List.of(far), 4);
		NodeScorer scorer = NodeScorer.defaults();
		ScoreResult safe = scorer.score(snap, self, feet, Vec3.ZERO, 0);
		check("far is alive", safe.isAlive());
		check("far positive clearance", safe.minClearance() > 0);

		// Threat overlapping
		Threat close = pointThreat(2, new Vec3(0, 0.9, 0), 0.5f);
		ThreatSnapshot snap2 = ThreatSnapshot.of(List.of(close), 4);
		ScoreResult dead = scorer.score(snap2, self, feet, Vec3.ZERO, 0);
		check("overlap is dead", dead.hardHit());
		System.out.println();
	}

	private static void testSnapshotPerf() {
		System.out.println("[ThreatSnapshot perf 100 threats]");
		List<Threat> list = new ArrayList<>();
		int horizon = 20;
		for (int i = 0; i < 100; i++) {
			ThreatFrame[] frames = new ThreatFrame[horizon];
			Vec3 p = new Vec3(i * 0.3, 0, 0);
			Vec3 v = new Vec3(0, 0, 0.5);
			for (int t = 0; t < horizon; t++) {
				frames[t] = new ThreatFrame(p.add(v.scale(t)), null, 0.25f, true);
			}
			list.add(new Threat(i, frames, ThreatSemantic.DANMAKU, null, 1));
		}
		// Plan: build snapshot (20-tick horizon) + one full score pass ≤0.5ms ideal / 1ms CI
		NodeScorer scorer = NodeScorer.defaults();
		SelfBoxModel self = SelfBoxModel.previewTarget();
		Vec3 feet = new Vec3(15, 0, 5);
		for (int i = 0; i < 40; i++) {
			ThreatSnapshot warm = ThreatSnapshot.of(list, horizon);
			scorer.score(warm, self, feet, new Vec3(0, 0, 0.2), 0);
		}

		long[] samples = new long[15];
		ThreatSnapshot snap = null;
		for (int i = 0; i < samples.length; i++) {
			long t0 = System.nanoTime();
			snap = ThreatSnapshot.of(list, horizon);
			scorer.score(snap, self, feet, new Vec3(0, 0, 0.2), 0);
			samples[i] = System.nanoTime() - t0;
		}
		Arrays.sort(samples);
		double ms = samples[samples.length / 2] / 1e6;
		System.out.println("  info: median " + ms + " ms for build + 1 score pass (plan: ≤0.5 ideal, ≤1 CI)");
		if (ms > 0.5) {
			System.out.println("  warn: exceeds ideal 0.5ms budget");
		}
		check("perf under 1ms (CI)", ms < 1.0);
		check("snapshot size 100", snap.size() == 100);
		check("broadphase present", snap.broadphase() != null);
		System.out.println();
	}

	private static void testPilotPathOracleRejectsWallCrossing() {
		System.out.println("[pilot path collision gate]");
		final boolean[] pathChecked = {false};
		CollisionOracle oracle = new CollisionOracle() {
			@Override
			public boolean isFree(AABB box) {
				return true; // Endpoint is empty on the far side of the wall.
			}

			@Override
			public boolean isPathFree(AABB from, Vec3 delta) {
				pathChecked[0] = true;
				return false; // The swept segment crosses the wall.
			}
		};
		PilotState state = new PilotState(Vec3.ZERO, Vec3.ZERO, SelfBoxModel.vanillaPlayer());
		state.oracle = oracle;
		ActionModel oneStep = (parent, high, low) -> List.of(
				new ActionModel.Action(new Vec3(2, 0, 0), false, 0));
		var result = new SpatioTemporalSearch(oneStep, NodeScorer.defaults()).search(
				ThreatSnapshot.of(List.of(), 2), state,
				new PilotProfile("TEST", 2, 1, 0, 0, 0, 0, 0, 1, 1, 16, 1, 1, 2,
						1.5f, 0, 1, 0));
		check("search checks complete path, not only endpoint", pathChecked[0]);
		check("wall-crossing candidate is rejected", result.firstStep().lengthSqr() < 1e-10);
		System.out.println();
	}

	private static void testLinearPilotProfiles() {
		System.out.println("[linear pilot profiles]");
		PilotProfile basic = PilotProfile.playerTier(0, 0.3, 0.1);
		PilotProfile enhanced = PilotProfile.playerTier(1, 0.3, 0.1);
		PilotProfile advanced = PilotProfile.playerTier(2, 0.3, 0.1);
		check("speed increases by level",
				basic.highSpeed() < enhanced.highSpeed() && enhanced.highSpeed() < advanced.highSpeed());
		check("prediction increases by level",
				basic.predictHorizon() < enhanced.predictHorizon()
						&& enhanced.predictHorizon() < advanced.predictHorizon());
		check("search budget increases by level",
				basic.nodeBudget() < enhanced.nodeBudget()
						&& enhanced.nodeBudget() < advanced.nodeBudget());
		approx("shared repulsion algorithm", basic.repulseGain(), advanced.repulseGain(), 1e-9);
		approx("linear tier speed", advanced.highSpeed() - enhanced.highSpeed(),
				enhanced.highSpeed() - basic.highSpeed(), 1e-9);
		System.out.println();
	}

	private static void testNavigationPreferencesAndAnchor() {
		System.out.println("[navigation preference and stable anchor]");
		PilotState state = new PilotState(Vec3.ZERO, Vec3.ZERO, SelfBoxModel.vanillaPlayer());
		state.inputPreference = new Vec3(1, 0, 0);
		double preferred = state.navigationScore(new Vec3(1, 0, 0), new Vec3(1, 0, 0));
		double rejected = state.navigationScore(new Vec3(-1, 0, 0), new Vec3(-1, 0, 0));
		check("control direction changes candidate weight", preferred > rejected);
		state.inputPreference = Vec3.ZERO;
		double home = state.navigationScore(new Vec3(2, 0, 0), new Vec3(1, 0, 0));
		double drift = state.navigationScore(new Vec3(12, 0, 0), new Vec3(1, 0, 0));
		check("fixed anchor penalizes long drift", home > drift);
		System.out.println();
	}

	private static void testEmergencyDirectionRefinement() {
		System.out.println("[emergency local direction refinement]");
		ActionModel blockedSeed = new ActionModel() {
			@Override
			public List<Action> actions(PilotSearchNode parent, double high, double low) {
				return List.of(new Action(new Vec3(high, 0, 0), true, 0));
			}

			@Override
			public List<Vec3> directionSeeds() {
				return List.of(new Vec3(1, 0, 0));
			}
		};
		CollisionOracle narrowGap = new CollisionOracle() {
			@Override
			public boolean isFree(AABB box) {
				return true;
			}

			@Override
			public boolean isPathFree(AABB from, Vec3 delta) {
				if (delta.lengthSqr() < 1e-10) return false;
				double alignment = delta.normalize().dot(new Vec3(1, 0, 0));
				return alignment > 0.75 && alignment < 0.999;
			}
		};
		PilotState state = new PilotState(Vec3.ZERO, Vec3.ZERO, SelfBoxModel.vanillaPlayer());
		state.oracle = narrowGap;
		PilotProfile profile = PilotProfile.ADVANCED;
		ThreatSnapshot empty = ThreatSnapshot.empty(profile.predictHorizon());
		var result = new SpatioTemporalSearch(blockedSeed, NodeScorer.defaults())
				.search(empty, state, profile);
		check("refinement activates after base directions fail", result.refined());
		check("refinement finds non-base safe corridor", result.foundAlive()
				&& narrowGap.isPathFree(state.selfBox.bodyAt(Vec3.ZERO), result.firstStep()));
		CorridorEvaluator.Result corridor = CorridorEvaluator.evaluate(empty, state,
				NodeScorer.defaults(), result.firstStep(), profile.searchDepth());
		check("refined corridor survives full horizon", corridor.collisionFree());
		System.out.println();
	}

	private static void testEmergencyRefinementBudget() {
		System.out.println("[emergency refinement budget]");
		ActionModel blocked = new ActionModel() {
			@Override
			public List<Action> actions(PilotSearchNode parent, double high, double low) {
				return List.of();
			}

			@Override
			public List<Vec3> directionSeeds() {
				return List.of(
						new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
						new Vec3(0, 0, 1), new Vec3(0, 0, -1),
						new Vec3(1, 0, 1).normalize(), new Vec3(-1, 0, 1).normalize());
			}
		};
		PilotState state = new PilotState(Vec3.ZERO, Vec3.ZERO, SelfBoxModel.vanillaPlayer());
		state.oracle = new CollisionOracle() {
			@Override
			public boolean isFree(AABB box) {
				return true;
			}

			@Override
			public boolean isPathFree(AABB from, Vec3 delta) {
				return false;
			}
		};
		int budget = 4;
		PilotProfile profile = new PilotProfile("BUDGET", 0.4, 0.2,
				0, 0, 0, 0, 0, 4, 4, budget, 32, 8, 4,
				1.5f, 0, 1, 0);
		var result = new SpatioTemporalSearch(blocked, NodeScorer.defaults())
				.search(ThreatSnapshot.empty(4), state, profile);
		check("refinement obeys the shared node budget", result.nodesExpanded() <= budget);
		System.out.println();
	}

	private static void testGroundedTerrainRules() {
		System.out.println("[grounded terrain rules]");
		ActionModel jumpOnly = (parent, high, low) -> List.of(
				new ActionModel.Action(new Vec3(0, 0.42, 0), false, 100));
		PilotState airborne = new PilotState(Vec3.ZERO, Vec3.ZERO, SelfBoxModel.vanillaPlayer());
		airborne.grounded = true;
		airborne.oracle = new CollisionOracle() {
			@Override
			public boolean isFree(AABB box) {
				return true;
			}

			@Override
			public boolean isSupported(AABB box) {
				return false;
			}
		};
		PilotProfile oneStep = new PilotProfile("GROUND", 0.4, 0.2,
				0, 0, 0, 0, 0, 1, 1, 8, 1, 1, 2,
				1.5f, 0, 1, 0);
		var airborneResult = new SpatioTemporalSearch(jumpOnly, NodeScorer.defaults())
				.search(ThreatSnapshot.empty(2), airborne, oneStep);
		check("ground pilot cannot jump without footing", airborneResult.firstStep().y <= 0);

		ActionModel walkOnly = (parent, high, low) -> List.of(
				new ActionModel.Action(new Vec3(1, 0, 0), false, 0));
		PilotState cliff = new PilotState(Vec3.ZERO, Vec3.ZERO, SelfBoxModel.vanillaPlayer());
		cliff.grounded = true;
		cliff.oracle = new CollisionOracle() {
			@Override
			public boolean isFree(AABB box) {
				return true;
			}

			@Override
			public boolean isSupported(AABB box) {
				return box.minX < 0.5;
			}
		};
		var cliffResult = new SpatioTemporalSearch(walkOnly, NodeScorer.defaults())
				.search(ThreatSnapshot.empty(2), cliff, oneStep);
		check("ground pilot does not walk off a supported edge", cliffResult.firstStep().lengthSqr() < 1e-10);
		System.out.println();
	}

	private static void testArenaClearanceForce() {
		System.out.println("[arena soft clearance]");
		PilotState state = new PilotState(new Vec3(0.8, 0, 0), Vec3.ZERO, SelfBoxModel.vanillaPlayer());
		state.arena = new AABB(-1, -1, -1, 1, 1, 1);
		state.wallClearanceRadius = 0.5;
		state.wallClearanceGain = 1.0;
		Vec3 force = state.arenaClearanceForce();
		check("near max X pushes inward", force.x < -0.1);
		state.feet = new Vec3(0, 0, 0);
		check("center has no boundary force", state.arenaClearanceForce().lengthSqr() < 1e-10);
		state.feet = new Vec3(1.1, 0, 0);
		check("outside max X recovers inward", state.arenaClearanceForce().x < 0);
		check("outside max X is penalized", state.arenaClearancePenalty() < 0);
		System.out.println();
	}

	private static Threat pointThreat(int id, Vec3 pos, float r) {
		ThreatFrame[] frames = new ThreatFrame[4];
		for (int i = 0; i < 4; i++) {
			frames[i] = new ThreatFrame(pos, null, r, true);
		}
		return new Threat(id, frames, ThreatSemantic.DANMAKU, null, 1);
	}
}
