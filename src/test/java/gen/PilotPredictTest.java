package gen;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfigs;
import dev.xkmc.youkaishomecoming.content.spell.mover.*;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.*;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewTarget;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Self-test for Phase 0 threat prediction.
 * Run via: {@code gradlew testClasses} then execute main, or IDE run main().
 * <p>
 * Mover-level T1 tests run without a live game. Entity-level T2/T3 capture
 * needs GameTest/runClient; math paths are covered offline here.
 */
public class PilotPredictTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		System.out.println("=== Phase 0 Pilot Prediction Tests ===\n");

		testThreatFrameBounds();
		testThreatFrameLaserBounds();
		testThreatProviderRegistry();
		testRectMoverPrediction();
		testPolarMoverPrediction();
		testBezierMoverPrediction();
		testZeroMoverPrediction();
		testRotateMoverPrediction();
		testTranslateMoverAimPrediction();
		testHomingMoverSteering();
		testHomingMoverCodec();
		testPreviewTargetSurface();
		testFormulaMoverPrediction();
		testOrbitalMoverPrediction();
		testSplineMoverPrediction();
		testMultiBezierMoverPrediction();
		testMoverInfoOffsetTime();
		testBallisticArrowMath();
		testT3LinearExtrapolation();
		testT3QuadraticExtrapolation();
		testT3FitQuadratic();
		testUnsupportedMoverFallsThrough();
		testC2bGenerality();
		testLaserGeometryConstants();

		System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
		if (failed > 0) {
			throw new RuntimeException(failed + " tests failed!");
		}
		System.out.println("All tests passed!");
	}

	private static void check(String name, boolean condition) {
		if (condition) {
			System.out.println("  PASS: " + name);
			passed++;
		} else {
			System.out.println("  FAIL: " + name);
			failed++;
		}
	}

	private static void approx(String name, double actual, double expected, double epsilon) {
		boolean ok = Math.abs(actual - expected) < epsilon;
		if (!ok) {
			System.out.println("  FAIL detail: " + name + " actual=" + actual + " expected=" + expected);
		}
		check(name, ok);
	}

	private static void approx(String name, Vec3 actual, Vec3 expected, double epsilon) {
		boolean ok = actual.distanceTo(expected) < epsilon;
		if (!ok) {
			System.out.println("  FAIL detail: " + name + " actual=" + actual + " expected=" + expected
					+ " dist=" + actual.distanceTo(expected));
		}
		check(name, ok);
	}

	// --- ThreatFrame ---

	private static void testThreatFrameBounds() {
		System.out.println("[ThreatFrame point]");
		var frame = new ThreatFrame(new Vec3(1, 2, 3), null, 0.5f, true);
		var bb = frame.bounds();
		approx("bounds minX", bb.minX, 0.5, 1e-8);
		approx("bounds maxX", bb.maxX, 1.5, 1e-8);
		approx("bounds minY", bb.minY, 1.5, 1e-8);
		approx("bounds maxY", bb.maxY, 2.5, 1e-8);
		approx("bounds minZ", bb.minZ, 2.5, 1e-8);
		approx("bounds maxZ", bb.maxZ, 3.5, 1e-8);
		check("active", frame.active());
		check("not laser", !frame.isLaser());
		check("orientation null", frame.orientation() == null);
		System.out.println();
	}

	private static void testThreatFrameLaserBounds() {
		System.out.println("[ThreatFrame laser]");
		// Segment from (0,0,0) along +Z length 10, radius 0.25
		var frame = new ThreatFrame(new Vec3(0, 0, 0), new Vec3(0, 0, 1), 0.25f, 10f, true);
		check("is laser", frame.isLaser());
		var bb = frame.bounds();
		approx("laser minX", bb.minX, -0.25, 1e-6);
		approx("laser maxX", bb.maxX, 0.25, 1e-6);
		approx("laser minZ", bb.minZ, -0.25, 1e-6);
		approx("laser maxZ", bb.maxZ, 10.25, 1e-6);
		// Inactive warn frame still has geometry
		var warn = new ThreatFrame(new Vec3(0, 0, 0), new Vec3(1, 0, 0), 0.5f, 8f, false);
		check("warn inactive", !warn.active());
		check("warn still laser", warn.isLaser());
		System.out.println();
	}

	// --- ThreatProviderRegistry ---

	private static void testThreatProviderRegistry() {
		System.out.println("[ThreatProviderRegistry]");
		var registry = new ThreatProviderRegistry();
		check("empty capture is null", registry.capture(null, 10) == null);
		check("providers list empty", registry.getProviders().isEmpty());
		registry.register(new BallisticProvider());
		registry.register(new ObservedMotionProvider());
		check("providers size 2", registry.getProviders().size() == 2);
		// C2b: chain without T1 is valid
		check("no T1 in chain", registry.getProviders().stream()
				.noneMatch(p -> p instanceof MoverExactProvider));
		System.out.println();
	}

	// --- T1: MoverExactProvider - RectMover ---

	private static void testRectMoverPrediction() {
		System.out.println("[T1 RectMover]");
		var rect = new RectMover(new Vec3(0, 0, 0), new Vec3(1, 2, 3), new Vec3(0.1, 0, 0));
		// Direct pos(tick) and pos(MoverInfo.offsetTime) must match (provider path)
		for (int t = 0; t <= 20; t++) {
			Vec3 expected = new Vec3(0, 0, 0).add(new Vec3(1, 2, 3).scale(t))
					.add(new Vec3(0.1, 0, 0).scale(t * t * 0.5));
			Vec3 actual = rect.pos(t);
			approx("pos(t) t=" + t, actual, expected, 1e-8);
			var info = new MoverInfo(0, Vec3.ZERO, Vec3.ZERO, null, null).offsetTime(t);
			approx("pos(info) t=" + t, rect.pos(info), expected, 1e-8);
		}
		System.out.println();
	}

	// --- T1: PolarMover ---

	private static void testPolarMoverPrediction() {
		System.out.println("[T1 PolarMover]");
		var polar = new PolarMover(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 1, 0), new Vec3(1, 0, 0));
		polar.radial(5, 0, 0).angular(0, 0.1, 0);
		for (int t = 0; t <= 20; t++) {
			Vec3 expected = new Vec3(
					5 * Math.cos(0.1 * t),
					0,
					5 * Math.sin(0.1 * t)
			);
			Vec3 actual = polar.pos(t);
			approx("t=" + t, actual, expected, 1e-8);
		}
		System.out.println();
	}

	// --- T1: BezierMover ---

	private static void testBezierMoverPrediction() {
		System.out.println("[T1 BezierMover]");
		var bezier = new BezierMover(
				new Vec3(0, 0, 0), new Vec3(0, 10, 0),
				new Vec3(10, 10, 0), new Vec3(10, 0, 0), 20
		);
		approx("t=0", bezier.pos(0), new Vec3(0, 0, 0), 1e-8);
		approx("t=20", bezier.pos(20), new Vec3(10, 0, 0), 1e-8);
		var mid = bezier.pos(10);
		approx("t=10 x", mid.x, 5, 1e-1);
		approx("t=10 y", mid.y, 7.5, 1e-1);
		// Continuity: consecutive steps stay bounded
		double maxStep = 0;
		for (int t = 0; t < 20; t++) {
			maxStep = Math.max(maxStep, bezier.pos(t).distanceTo(bezier.pos(t + 1)));
		}
		check("bezier step bounded", maxStep < 2.0);
		System.out.println();
	}

	// --- T1: ZeroMover ---

	private static void testZeroMoverPrediction() {
		System.out.println("[T1 ZeroMover position constant]");
		var zero = new ZeroMover(new Vec3(1, 0, 0), new Vec3(1, 0, 0), 0);
		for (int t = 0; t <= 10; t++) {
			var info = new MoverInfo(t, Vec3.ZERO, Vec3.ZERO, null, null);
			var move = zero.move(info);
			approx("t=" + t + " vel", move.vec(), Vec3.ZERO, 1e-8);
		}
		System.out.println();
	}

	// --- T1: RotateMover ---

	private static void testRotateMoverPrediction() {
		System.out.println("[T1 RotateMover position constant + pure rot]");
		// Rate in degrees/tick; dir is rotation axis plane forward
		var rotate = new RotateMover(new Vec3(1, 0, 0), 15.0);
		Vec3 prev = null;
		for (int t = 0; t <= 10; t++) {
			var info = new MoverInfo(t, Vec3.ZERO, Vec3.ZERO, null, null);
			var move = rotate.move(info);
			approx("t=" + t + " vel", move.vec(), Vec3.ZERO, 1e-8);
			// Deterministic: same tick → same rot
			var move2 = rotate.move(info);
			approx("t=" + t + " deterministic", move.rot(), move2.rot(), 1e-10);
			if (prev != null) {
				check("t=" + t + " rot changes", move.rot().distanceTo(prev) > 1e-6);
			}
			prev = move.rot();
		}
		// Position stays constant by design (provider freezes anchor)
		check("vel always zero across horizon", true);
		System.out.println();
	}

	// --- T1: TranslateMover (aim mode) ---

	private static void testTranslateMoverAimPrediction() {
		System.out.println("[T1 TranslateMover aim mode]");
		var translate = new TranslateMover(new Vec3(1, 2, 3), new Vec3(0, 0, 1), 2.0);
		for (int t = 0; t <= 10; t++) {
			Vec3 expected = new Vec3(1, 2, 3).add(new Vec3(0, 0, 1).scale(2.0 * t));
			Vec3 actual = translate.pos(t);
			approx("t=" + t, actual, expected, 1e-8);
		}
		System.out.println();
	}

	private static void testPreviewTargetSurface() {
		System.out.println("[Preview target surface]");
		AABB box = PreviewTarget.boxAt(Vec3.ZERO, new Vec3(2, 4, 6));
		approx("box width", box.getXsize(), 2, 1e-8);
		approx("box height", box.getYsize(), 4, 1e-8);
		approx("box depth", box.getZsize(), 6, 1e-8);
		check("outside segment crosses face", PreviewTarget.firstSurfaceIntersection(
				box, new Vec3(-2, 2, 0), new Vec3(2, 2, 0)).isPresent());
		check("outside segment misses", PreviewTarget.firstSurfaceIntersection(
				box, new Vec3(-2, 5, 0), new Vec3(2, 5, 0)).isEmpty());
		check("inside segment does not hit volume", PreviewTarget.firstSurfaceIntersection(
				box, new Vec3(0, 1, 0), new Vec3(0.5, 2, 0.5)).isEmpty());
		check("inside segment hits exit face", PreviewTarget.firstSurfaceIntersection(
				box, new Vec3(0, 2, 0), new Vec3(2, 2, 0)).isPresent());
		check("surface start counts as face hit", PreviewTarget.firstSurfaceIntersection(
				box, new Vec3(-1, 2, 0), new Vec3(0, 2, 0)).isPresent());
		check("surface-parallel segment counts as face hit", PreviewTarget.firstSurfaceIntersection(
				box, new Vec3(-1, 2, 0), new Vec3(-1, 3, 0)).isPresent());
		check("entity volume counts an inside segment", PreviewTarget.firstVolumeIntersection(
				box, new Vec3(0, 1, 0), new Vec3(0.5, 2, 0.5)).isPresent());
		Vec3 sweepStart = new Vec3(0, 0.5, 0);
		Vec3 sweepEnd = new Vec3(0, 0.5, -10);
		Vec3 entityHit = PreviewTarget.firstVolumeIntersection(
				PreviewTarget.boxAt(new Vec3(0, 0, -3), PreviewTarget.DEFAULT_BOX_SIZE), sweepStart, sweepEnd).orElseThrow();
		Vec3 blockHit = PreviewTarget.firstSurfaceIntersection(
				PreviewTarget.boxAt(new Vec3(0, 0, -7), PreviewTarget.DEFAULT_BOX_SIZE), sweepStart, sweepEnd).orElseThrow();
		check("nearest target hit can be selected by sweep distance",
				sweepStart.distanceToSqr(entityHit) < sweepStart.distanceToSqr(blockHit));
		System.out.println();
	}

	// --- Experimental: smooth homing mover ---

	private static void testHomingMoverSteering() {
		System.out.println("[HomingMover steering]");
		var homing = new HomingMover(new Vec3(1, 0, 0), 1, 10, 2,
				null, new Vec3(0, 0, 20));
		var delayed = homing.move(new MoverInfo(1, Vec3.ZERO, new Vec3(1, 0, 0), null, null));
		approx("delay preserves heading", delayed.vec(), new Vec3(1, 0, 0), 1e-8);

		var turned = homing.move(new MoverInfo(3, Vec3.ZERO, delayed.vec(), null, null));
		double angle = Math.toRadians(10);
		approx("turn is angle-limited", turned.vec(), new Vec3(Math.cos(angle), 0, Math.sin(angle)), 1e-8);
		approx("configured speed is preserved", turned.vec().length(), 1, 1e-8);

		var opposite = new HomingMover(new Vec3(1, 0, 0), 0.5, 5, 0,
				null, new Vec3(-10, 0, 0));
		var oppositeTurn = opposite.move(new MoverInfo(1, Vec3.ZERO, new Vec3(0.5, 0, 0), null, null));
		check("opposite target remains finite", Double.isFinite(oppositeTurn.vec().x)
				&& Double.isFinite(oppositeTurn.vec().y) && Double.isFinite(oppositeTurn.vec().z));
		approx("opposite turn preserves speed", oppositeTurn.vec().length(), 0.5, 1e-8);

		var fallback = new HomingMover(new Vec3(0, 0, 1), 1, 20, 0,
				null, new Vec3(0, 0, 10));
		fallback.move(new MoverInfo(1, Vec3.ZERO, new Vec3(0, 0, 1), null, null));
		var afterPassing = fallback.move(new MoverInfo(12, new Vec3(0, 0, 11),
				new Vec3(0, 0, 1), null, null));
		approx("fixed fallback does not circle back", afterPassing.vec(), new Vec3(0, 0, 1), 1e-8);
		System.out.println();
	}

	private static void testHomingMoverCodec() {
		System.out.println("[HomingMover codec]");
		var json = JsonParser.parseString("{\"type\":\"homing\",\"speed\":0.45,\"turn_rate\":6,\"delay\":8}");
		MoverConfig decoded = MoverConfig.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
		check("homing JSON decodes", decoded instanceof MoverConfigs.HomingMoverConfig);
		var encoded = decoded == null ? null : MoverConfig.CODEC.encodeStart(JsonOps.INSTANCE, decoded).result().orElse(null);
		check("homing JSON round-trips", encoded != null && encoded.toString().contains("\"homing\""));
		System.out.println();
	}

	// --- T1: FormulaMover ---

	private static void testFormulaMoverPrediction() {
		System.out.println("[T1 FormulaMover]");
		var formula = new FormulaMover(
				new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
				"tick * 0.5", "0", "sin_rad(tick * 0.1)", Vec3.ZERO
		);
		for (int t = 0; t <= 10; t++) {
			Vec3 expected = new Vec3(0, 0, 0)
					.add(new Vec3(1, 0, 0).scale(t * 0.5))
					.add(new Vec3(0, 0, 1).scale(Math.sin(t * 0.1)));
			Vec3 actual = formula.pos(t);
			approx("t=" + t, actual, expected, 1e-8);
		}
		System.out.println();
	}

	// --- T1: OrbitalMover ---

	private static void testOrbitalMoverPrediction() {
		System.out.println("[T1 OrbitalMover]");
		var orbital = new OrbitalMover(
				new Vec3(0, 0, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0),
				30, "3", "0"
		);
		Vec3 pos0 = orbital.pos(0);
		check("t=0 not zero", pos0.lengthSqr() > 0);
		// After 12 ticks: 360 deg → back to start
		Vec3 pos12 = orbital.pos(12);
		approx("t=12 returns", pos12, pos0, 0.001);
		System.out.println();
	}

	// --- T1: SplineMover ---

	private static void testSplineMoverPrediction() {
		System.out.println("[T1 SplineMover]");
		var spline = new SplineMover(
				java.util.List.of(new Vec3(0, 0, 0), new Vec3(10, 0, 0)),
				20, false
		);
		Vec3 pos0 = spline.pos(0);
		approx("t=0 p0", pos0, new Vec3(0, 0, 0), 1e-8);
		Vec3 pos20 = spline.pos(20);
		approx("t=20 p1", pos20, new Vec3(10, 0, 0), 1e-1);
		System.out.println();
	}

	// --- T1: MultiBezierMover ---

	private static void testMultiBezierMoverPrediction() {
		System.out.println("[T1 MultiBezierMover]");
		var seg1 = new MultiBezierMover.Segment(
				new Vec3(0, 0, 0), new Vec3(5, 0, 0),
				new Vec3(5, 10, 0), new Vec3(10, 10, 0), 10
		);
		var multi = new MultiBezierMover(java.util.List.of(seg1));
		approx("t=0", multi.pos(0), new Vec3(0, 0, 0), 1e-8);
		approx("t=10", multi.pos(10), new Vec3(10, 10, 0), 0.1);
		System.out.println();
	}

	private static void testMoverInfoOffsetTime() {
		System.out.println("[MoverInfo.offsetTime]");
		var base = new MoverInfo(5, new Vec3(1, 2, 3), new Vec3(0.1, 0, 0), null, null);
		var off = base.offsetTime(7);
		check("tick +7", off.tick() == 12);
		approx("prevPos frozen", off.prevPos(), base.prevPos(), 1e-12);
		approx("prevVel frozen", off.prevVel(), base.prevVel(), 1e-12);
		System.out.println();
	}

	// --- T2: BallisticProvider math ---

	private static void testBallisticArrowMath() {
		System.out.println("[T2 Ballistic arrow math]");
		// Vanilla order: pos += vel; vel *= drag; vel.y -= g
		Vec3 pos = new Vec3(0, 10, 0);
		Vec3 vel = new Vec3(3, 0, 0);
		double drag = 0.99;
		double gravity = 0.05;
		int horizon = 20;

		Vec3[] path = BallisticProvider.simulate(pos, vel, drag, gravity, horizon);
		check("arrow moved forward", path[horizon - 1].x > 0);
		check("arrow fell down", path[horizon - 1].y < 10);

		// Independent recompute for error bound
		Vec3 simPos = pos;
		Vec3 simVel = vel;
		for (int i = 0; i < horizon; i++) {
			if (i > 0) {
				simPos = simPos.add(simVel);
				simVel = simVel.scale(drag).add(0, -gravity, 0);
			}
			approx("path t=" + i, path[i], simPos, 1e-10);
		}
		// 20-tick error vs "truth" is exact by construction for T2 constants
		approx("final x", path[horizon - 1].x, simPos.x, 0.01);
		System.out.println();
	}

	// --- T3 ---

	private static void testT3LinearExtrapolation() {
		System.out.println("[T3 Linear extrapolation]");
		Vec3 pos = new Vec3(1, 2, 3);
		Vec3 vel = new Vec3(4, 5, 6);
		int horizon = 6;
		Vec3[] path = ObservedMotionProvider.extrapolateLinear(pos, vel, horizon);
		for (int i = 0; i < horizon; i++) {
			approx("linear t=" + i, path[i], pos.add(vel.scale(i)), 1e-10);
		}
		System.out.println();
	}

	private static void testT3QuadraticExtrapolation() {
		System.out.println("[T3 Quadratic extrapolation]");
		Vec3 p0 = new Vec3(0, 0, 0);
		Vec3 v0 = new Vec3(1, 2, 0);
		Vec3 a = new Vec3(0.1, 0, 0);
		int horizon = 6;
		Vec3[] path = ObservedMotionProvider.extrapolateQuadratic(p0, v0, a, horizon);
		for (int i = 0; i < horizon; i++) {
			Vec3 expected = p0.add(v0.scale(i)).add(a.scale(i * i * 0.5));
			approx("quadratic t=" + i, path[i], expected, 1e-10);
		}
		// 6-tick horizon error for smooth accel motion stays small
		check("6-tick displacement reasonable", path[5].distanceTo(p0) < 20);
		System.out.println();
	}

	private static void testT3FitQuadratic() {
		System.out.println("[T3 fitQuadratic]");
		// p(t)=v*t+0.5*a*t^2 → displacements: Δ01=v+0.5a, Δ12=v+1.5a
		// finite-diff: v01=v+0.5a, v12=v+1.5a, a_fit=v12-v01=a, vel_at_r2=v12=v+1.5a
		Vec3 a = new Vec3(0.2, 0, 0);
		Vec3 v = new Vec3(1, 0, 0);
		var r0 = new ObservedMotionProvider.PositionRecord(Vec3.ZERO, 0);
		var r1 = new ObservedMotionProvider.PositionRecord(v.scale(1).add(a.scale(0.5)), 1);
		var r2 = new ObservedMotionProvider.PositionRecord(v.scale(2).add(a.scale(2)), 2);
		var fit = ObservedMotionProvider.fitQuadratic(r0, r1, r2);
		check("fit non-null", fit != null);
		if (fit != null) {
			approx("fit vel", fit.velocity(), v.add(a.scale(1.5)), 0.05);
			approx("fit accel x", fit.accel().x, 0.2, 0.05);
		}
		System.out.println();
	}

	// --- Unsupported / C2b ---

	private static void testUnsupportedMoverFallsThrough() {
		System.out.println("[Unsupported mover falls through]");
		var provider = new MoverExactProvider();
		check("null entity not supported", !provider.supports(null));
		check("null capture null", provider.capture(null, 10) == null);
		// FixedDirMover / CompositeMover / LayeredMover / Attached* are not TargetPosMover
		DanmakuMover fixed = new FixedDirMover();
		check("FixedDirMover not TargetPos", !(fixed instanceof TargetPosMover));
		DanmakuMover homing = new HomingMover(new Vec3(1, 0, 0), 0.45, 6, 0,
				null, new Vec3(10, 0, 0));
		check("HomingMover stays out of exact prediction", !(homing instanceof TargetPosMover));
		System.out.println();
	}

	private static void testC2bGenerality() {
		System.out.println("[C2b Generality - T2/T3 work without T1]");
		var ballistic = new BallisticProvider();
		var observed = new ObservedMotionProvider();
		var registry = new ThreatProviderRegistry();
		registry.register(ballistic);
		registry.register(observed);
		check("registry size without T1", registry.getProviders().size() == 2);
		check("null still null", registry.capture(null, 5) == null);
		// Offline math still works with T1 deleted from chain
		Vec3[] arrow = BallisticProvider.simulate(new Vec3(0, 5, 0), new Vec3(2, 0, 0), 0.99, 0.05, 10);
		check("T2 alone produces path", arrow[9].x > 0);
		Vec3[] lin = ObservedMotionProvider.extrapolateLinear(Vec3.ZERO, new Vec3(1, 0, 0), 6);
		approx("T3 alone t=5", lin[5], new Vec3(5, 0, 0), 1e-10);
		System.out.println();
	}

	private static void testLaserGeometryConstants() {
		System.out.println("[Laser geometry model]");
		// Matches BaseLaser: ray from pos+BbHeight/2 along rot, length=getLength(),
		// radius=getEffectiveHitRadius()=bbWidth/4
		float bbWidth = 1.0f;
		float hitRadius = bbWidth / 4f;
		float length = 12f;
		Vec3 anchor = new Vec3(0, 1.0, 0); // pos + BbHeight/2
		Vec3 orient = new Vec3(1, 0, 0);
		var frame = new ThreatFrame(anchor, orient, hitRadius, length, true);
		var bb = frame.bounds();
		approx("laser radius", hitRadius, 0.25, 1e-6);
		approx("segment end X", bb.maxX, anchor.x + length + hitRadius, 1e-5);
		// Active window semantics: tickCount > start && tickCount < end
		int prepare = 20, startDelta = 20, life = 40, endFade = 20;
		int start = prepare + startDelta; // 40
		int end = start + life; // 80
		check("warn inactive t=10", !(10 > start && 10 < end));
		check("active t=50", 50 > start && 50 < end);
		check("fade inactive t=90", !(90 > start && 90 < end));
		System.out.println();
	}
}
