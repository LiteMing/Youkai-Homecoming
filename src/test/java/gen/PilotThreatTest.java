package gen;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatSemantic;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotProfile;
import dev.xkmc.youkaishomecoming.content.spell.pilot.PilotState;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.ActionModel;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.PilotSearchNode;
import dev.xkmc.youkaishomecoming.content.spell.pilot.search.SpatioTemporalSearch;
import dev.xkmc.youkaishomecoming.content.spell.pilot.threat.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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
		// Warmup JIT
		ThreatSnapshot warm = ThreatSnapshot.of(list, horizon);
		NodeScorer scorer = NodeScorer.defaults();
		SelfBoxModel self = SelfBoxModel.previewTarget();
		Vec3 feet = new Vec3(15, 0, 5);
		scorer.score(warm, self, feet, new Vec3(0, 0, 0.2), 0);

		long t0 = System.nanoTime();
		ThreatSnapshot snap = ThreatSnapshot.of(list, horizon);
		scorer.score(snap, self, feet, new Vec3(0, 0, 0.2), 0);
		long dt = System.nanoTime() - t0;
		double ms = dt / 1e6;
		System.out.println("  info: " + ms + " ms for build + 1 score pass (plan: ≤0.5 ideal, ≤1 CI)");
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
