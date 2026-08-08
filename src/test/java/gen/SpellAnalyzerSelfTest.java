package gen;

import dev.xkmc.youkaishomecoming.content.spell.analysis.NumberBounds;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicy;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;

import java.util.Set;

/**
 * Phase 0 analyzer pure-logic self-tests (main-method style, no JUnit).
 * Run: gradlew runSpellAnalyzerSelfTest
 * <p>
 * SCOPE LIMITATION: full SpellAnalyzer / SpellHash tests cannot run in a plain JVM.
 * The spell codec chain (SpellActions → FireLaserAction → YHDanmaku →
 * YoukaisHomecoming.<clinit>) initializes mixin-patched classes such as
 * RecipeBookType.create() ("Enum not extended!"), which only work under the FML +
 * modlauncher + mixin runtime. Analyzer walk math, hash stability and the market
 * facade therefore get their automated verification in Phase 2 integration tests
 * (server-side /yhspell self-test command on a live server), see design doc §23.
 * <p>
 * What IS tested here (no mod class-init side effects):
 * <ul>
 *   <li>NumberBounds resolution rules (bounded / unbounded providers);</li>
 *   <li>certification capability policy table (§11 defaults).</li>
 * </ul>
 */
public class SpellAnalyzerSelfTest {

	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) throws Exception {
		testConstantBounded();
		testRandomRangeBounded();
		testLerpBounded();
		testHealthRatioBounded();
		testTickModBounded();
		testRandomChoiceBounded();
		testIndexedBounded();
		testGameDifficultyBounded();
		testVariableUnbounded();
		testPhaseTickUnbounded();
		testDistanceAndPositionsUnbounded();
		testGaussianUnbounded();
		testSinBoundedByAmplitude();
		testArithmeticCombined();
		testDivByZeroUnbounded();
		testModByZeroUnbounded();
		testClampCapsUnboundedValue();
		testConditionalUnion();
		testPolicyTable();
		testPolicyIds();
		if (failed > 0) {
			throw new RuntimeException(failed + " analyzer self-tests failed!");
		}
		System.out.println("SpellAnalyzerSelfTest: all " + passed + " checks passed");
	}

	private static void check(String name, boolean condition) {
		if (condition) {
			passed++;
			System.out.println("PASS  " + name);
		} else {
			failed++;
			System.out.println("FAIL  " + name);
		}
	}

	private static void checkBounds(String name, NumberBounds b, boolean bounded, double lo, double hi) {
		check(name, b.bounded() == bounded && Math.abs(b.min() - lo) < 1e-9 && Math.abs(b.max() - hi) < 1e-9);
	}

	private static void checkUnbounded(String name, NumberBounds b) {
		check(name, !b.bounded());
	}

	// ----------------------------------------------------------------- tests

	private static void testConstantBounded() {
		checkBounds("constant", NumberBounds.resolve(new NumberProviders.Constant(5)), true, 5, 5);
	}

	private static void testRandomRangeBounded() {
		checkBounds("random range", NumberBounds.resolve(new NumberProviders.RandomRange(2, 8)), true, 2, 8);
		checkBounds("random range reversed", NumberBounds.resolve(new NumberProviders.RandomRange(8, 2)), true, 2, 8);
	}

	private static void testLerpBounded() {
		checkBounds("lerp", NumberBounds.resolve(new NumberProviders.LerpOverTime(10, 30, 100)), true, 10, 30);
	}

	private static void testHealthRatioBounded() {
		checkBounds("by health", NumberBounds.resolve(new NumberProviders.ByHealthRatio(0.2, 1.8)), true, 0.2, 1.8);
	}

	private static void testTickModBounded() {
		checkBounds("tick mod", NumberBounds.resolve(new NumberProviders.PhaseTickMod(20)), true, 0, 19);
		checkBounds("tick mod zero period", NumberBounds.resolve(new NumberProviders.PhaseTickMod(0)), true, 0, 0);
	}

	private static void testRandomChoiceBounded() {
		checkBounds("random choice", NumberBounds.resolve(new NumberProviders.RandomChoice(java.util.List.of(-3.0, 1.0, 2.0))), true, -3, 2);
		checkBounds("random choice empty", NumberBounds.resolve(new NumberProviders.RandomChoice(java.util.List.of())), true, 0, 0);
	}

	private static void testIndexedBounded() {
		// indexed selects from its value list regardless of index
		checkBounds("indexed with unbounded index", NumberBounds.resolve(
				new NumberProviders.Indexed(new NumberProviders.PhaseTick(), java.util.List.of(5.0, 9.0))), true, 5, 9);
	}

	private static void testGameDifficultyBounded() {
		checkBounds("game difficulty", NumberBounds.resolve(new NumberProviders.GameDifficulty()), true, 0, 3);
	}

	private static void testVariableUnbounded() {
		checkUnbounded("variable", NumberBounds.resolve(new NumberProviders.Variable("x")));
	}

	private static void testPhaseTickUnbounded() {
		checkUnbounded("phase tick", NumberBounds.resolve(new NumberProviders.PhaseTick()));
		checkUnbounded("total tick", NumberBounds.resolve(new NumberProviders.TotalTick()));
	}

	private static void testDistanceAndPositionsUnbounded() {
		checkUnbounded("distance", NumberBounds.resolve(new NumberProviders.Distance()));
		checkUnbounded("caster x", NumberBounds.resolve(new NumberProviders.CasterX()));
		checkUnbounded("target x", NumberBounds.resolve(new NumberProviders.TargetX()));
		checkUnbounded("target speed", NumberBounds.resolve(new NumberProviders.TargetSpeed()));
		checkUnbounded("target fly time", NumberBounds.resolve(new NumberProviders.TargetFlyTime()));
	}

	private static void testGaussianUnbounded() {
		checkUnbounded("gaussian", NumberBounds.resolve(new NumberProviders.GaussianRandom(0, 5)));
	}

	private static void testSinBoundedByAmplitude() {
		checkBounds("sin_deg bounded by amplitude",
				NumberBounds.resolve(new NumberProviders.SinDeg(new NumberProviders.PhaseTick(), 7, 0)), true, -7, 7);
		checkBounds("cos_rad bounded by amplitude",
				NumberBounds.resolve(new NumberProviders.CosRad(new NumberProviders.PhaseTick(), 3, 0)), true, -3, 3);
	}

	private static void testArithmeticCombined() {
		NumberProvider two = new NumberProviders.Constant(2);
		NumberProvider five = new NumberProviders.Constant(5);
		checkBounds("add", NumberBounds.resolve(new NumberProviders.Add(two, five)), true, 7, 7);
		checkBounds("mul", NumberBounds.resolve(new NumberProviders.Mul(two, five)), true, 10, 10);
		checkBounds("sub via add", NumberBounds.resolve(new NumberProviders.Add(five, new NumberProviders.Constant(-2))), true, 3, 3);
		checkBounds("min", NumberBounds.resolve(new NumberProviders.Min(two, five)), true, 2, 2);
		checkBounds("max", NumberBounds.resolve(new NumberProviders.Max(two, five)), true, 5, 5);
		checkBounds("sqrt", NumberBounds.resolve(new NumberProviders.Sqrt(new NumberProviders.Constant(16))), true, 0, 4);
		checkBounds("abs", NumberBounds.resolve(new NumberProviders.Abs(new NumberProviders.Constant(-9))), true, 0, 9);
		checkBounds("round", NumberBounds.resolve(new NumberProviders.Round(new NumberProviders.Constant(2.6))), true, 3, 3);
		// range arithmetic via corners
		checkBounds("mul of ranges",
				NumberBounds.resolve(new NumberProviders.Mul(new NumberProviders.RandomRange(-2, 3), new NumberProviders.RandomRange(4, 5))),
				true, -10, 15);
	}

	private static void testDivByZeroUnbounded() {
		checkUnbounded("div by range crossing zero",
				NumberBounds.resolve(new NumberProviders.Div(new NumberProviders.Constant(10), new NumberProviders.RandomRange(-1, 1))));
		checkBounds("div safe", NumberBounds.resolve(
				new NumberProviders.Div(new NumberProviders.Constant(10), new NumberProviders.RandomRange(2, 5))), true, 2, 5);
	}

	private static void testModByZeroUnbounded() {
		checkUnbounded("mod by range crossing zero",
				NumberBounds.resolve(new NumberProviders.Mod(new NumberProviders.Constant(10), new NumberProviders.RandomRange(-1, 1))));
		checkBounds("mod safe", NumberBounds.resolve(
				new NumberProviders.Mod(new NumberProviders.Constant(10), new NumberProviders.RandomRange(3, 5))), true, -5, 5);
	}

	private static void testClampCapsUnboundedValue() {
		checkBounds("clamp caps unbounded value",
				NumberBounds.resolve(new NumberProviders.Clamp(new NumberProviders.PhaseTick(),
						new NumberProviders.Constant(0), new NumberProviders.Constant(20))), true, 0, 20);
		checkUnbounded("clamp with unbounded max",
				NumberBounds.resolve(new NumberProviders.Clamp(new NumberProviders.Constant(5),
						new NumberProviders.Constant(0), new NumberProviders.PhaseTick())));
	}

	private static void testConditionalUnion() {
		checkBounds("conditional unions branches",
				NumberBounds.resolve(new NumberProviders.Conditional(null,
						new NumberProviders.Constant(1), new NumberProviders.Constant(5))), true, 1, 5);
		checkUnbounded("conditional with unbounded branch",
				NumberBounds.resolve(new NumberProviders.Conditional(null,
						new NumberProviders.Constant(1), new NumberProviders.PhaseTick())));
	}

	private static void testPolicyTable() {
		check("base_fire ALLOW", SpellCapabilityPolicies.defaultPolicy(SpellCapability.BASE_FIRE) == SpellCapabilityPolicy.ALLOW);
		check("hooks ALLOW", SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_EXPIRY) == SpellCapabilityPolicy.ALLOW
				&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_TRAIL) == SpellCapabilityPolicy.ALLOW
				&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_HIT) == SpellCapabilityPolicy.ALLOW);
		check("boss_on_damage EXPERIMENTAL", SpellCapabilityPolicies.defaultPolicy(SpellCapability.BOSS_ON_DAMAGE) == SpellCapabilityPolicy.EXPERIMENTAL);
		check("origin target/absolute EXPERIMENTAL",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.ORIGIN_TARGET) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.ORIGIN_ABSOLUTE) == SpellCapabilityPolicy.EXPERIMENTAL);
		check("confine/teleport/erase/clear/flag/force/fire EXPERIMENTAL",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.CONFINED_TARGET) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.TELEPORT) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.ERASE_ENEMY_DANMAKU) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.CLEAR_SCREEN) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.SET_ENTITY_FLAG) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.FORCE_SPELL) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.FIRE_SPELL) == SpellCapabilityPolicy.EXPERIMENTAL);
		check("legacy DENY", SpellCapabilityPolicies.defaultPolicy(SpellCapability.LEGACY_TICKER) == SpellCapabilityPolicy.DENY);
		check("run_command OP_ONLY", SpellCapabilityPolicies.defaultPolicy(SpellCapability.RUN_COMMAND) == SpellCapabilityPolicy.OP_ONLY);
		check("visual ALLOW",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.SET_SPELL_CIRCLE) == SpellCapabilityPolicy.ALLOW
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.SHOW_SPELL_TITLE) == SpellCapabilityPolicy.ALLOW
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.YSM_RENDER) == SpellCapabilityPolicy.ALLOW);
		check("certification allowed set", SpellCapabilityPolicies.defaultPolicy(SpellCapability.BASE_FIRE).allowsCertification()
				&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_HIT).allowsCertification()
				&& !SpellCapabilityPolicies.defaultPolicy(SpellCapability.TELEPORT).allowsCertification()
				&& !SpellCapabilityPolicies.defaultPolicy(SpellCapability.RUN_COMMAND).allowsCertification()
				&& !SpellCapabilityPolicies.defaultPolicy(SpellCapability.LEGACY_TICKER).allowsCertification());
	}

	private static void testPolicyIds() {
		check("stable ids unique", Set.of(SpellCapability.values()).stream().map(SpellCapability::id).distinct().count() == SpellCapability.values().length);
		check("byId roundtrip", SpellCapability.byId("teleport") == SpellCapability.TELEPORT);
		check("default for unknown is DENY", SpellCapabilityPolicies.defaultPolicy(null) == SpellCapabilityPolicy.DENY);
	}
}
