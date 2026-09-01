package gen;

import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.content.spell.analysis.NumberBounds;
import dev.xkmc.youkaishomecoming.content.capability.PlayerDanmakuPolicy;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicy;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellBudgetScaling;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.payment.CastCost;

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
		testHeadlessFlagParsing();
		testCastCostBuckets();
		testReplicaProgressMath();
		testBudgetScaling();
		testDanmakuPerTickFormula();
		testSelfCheckFixtureJsonValid();
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
		testCasterMaxHealthKeyword();
		testGaussianUnbounded();
		testPowRootUnbounded();
		testSinBoundedByAmplitude();
		testArithmeticCombined();
		testDivByZeroUnbounded();
		testModByZeroUnbounded();
		testClampCapsUnboundedValue();
		testConditionalUnion();
		testPolicyTable();
		testPolicyIds();
		testPlayerDanmakuPolicy();
		passed += dev.xkmc.youkaishomecoming.content.spell.preview.ConditionEditorDraftTest.runAllTests();
		passed += dev.xkmc.youkaishomecoming.content.spell.preview.ActionEditorValueUpdatesTest.runAllTests();
		BounceSurfaceResponseTest.runAllTests();
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

	private static void testCastCostBuckets() {
		check("cast cost 0t baseline", CastCost.unitsForDuration(0) == 100);
		check("cast cost 100t baseline", CastCost.unitsForDuration(100) == 100);
		check("cast cost 101t rounds to one bucket", CastCost.unitsForDuration(101) == 120);
		check("cast cost 120t stays in one bucket", CastCost.unitsForDuration(120) == 120);
		check("cast cost 121t rounds to two buckets", CastCost.unitsForDuration(121) == 140);
		check("cast cost 1200t", CastCost.unitsForDuration(1200) == 1200);
	}

	private static void testReplicaProgressMath() {
		check("replica single capture does not round up",
				dev.xkmc.youkaishomecoming.content.spell.replica.SpellReplicaService
						.progressPercent(1, 500) == 0);
		check("replica exact one percent",
				dev.xkmc.youkaishomecoming.content.spell.replica.SpellReplicaService
						.progressPercent(5, 500) == 1);
		check("replica progress caps at 100",
				dev.xkmc.youkaishomecoming.content.spell.replica.SpellReplicaService
						.progressPercent(900, 500) == 100);
	}

	private static void testBudgetScaling() {
		check("budget multiplier 1x preserves base", SpellBudgetScaling.scale(10_000L, 1.0) == 10_000L);
		check("budget multiplier halves base", SpellBudgetScaling.scale(10_001L, 0.5) == 5_001L);
		check("budget multiplier clamps non-positive", SpellBudgetScaling.scale(10_000L, 0.0) == 1L);
		check("budget multiplier saturates overflow", SpellBudgetScaling.scale(Long.MAX_VALUE, 2.0) == Long.MAX_VALUE);
		check("budget multiplier handles infinity", SpellBudgetScaling.scale(10_000L, Double.POSITIVE_INFINITY) == Long.MAX_VALUE);
	}

	private static void testDanmakuPerTickFormula() {
		check("tier 1 at zero power allows one projectile/tick",
				SpellCardRank.fromTier(1).danmakuPerTick(0) == 1);
		check("tier 4 at zero power allows two projectiles/tick",
				SpellCardRank.fromTier(4).danmakuPerTick(0) == 2);
		check("tier 12 at zero power allows four projectiles/tick",
				SpellCardRank.fromTier(12).danmakuPerTick(0) == 4);
		check("fractional power is floored before scaling",
				SpellCardRank.fromTier(12).danmakuPerTick(3.99) == 16);
		check("negative power keeps the base allowance",
				SpellCardRank.fromTier(8).danmakuPerTick(-1) == 3);
	}

	/**
	 * Headless flags must parse strictly: "false"/"0"/empty never enable the switch
	 * (review B). Only property path is testable in a pure JVM (env is not settable).
	 */
	private static void testHeadlessFlagParsing() {
		// property path (env is not settable in a pure JVM; code path is shared)
		System.setProperty("yhdev.selftest", "false");
		check("flag=false disabled", !dev.xkmc.youkaishomecoming.content.spell.analysis.SpellSelfTestFlags.enabled("yhdev.selftest", "YHDEV_SELFTEST"));
		System.setProperty("yhdev.selftest", "0");
		check("flag=0 disabled", !dev.xkmc.youkaishomecoming.content.spell.analysis.SpellSelfTestFlags.enabled("yhdev.selftest", "YHDEV_SELFTEST"));
		System.setProperty("yhdev.selftest", "true");
		check("flag=true enabled", dev.xkmc.youkaishomecoming.content.spell.analysis.SpellSelfTestFlags.enabled("yhdev.selftest", "YHDEV_SELFTEST"));
		System.setProperty("yhdev.selftest", "1");
		check("flag=1 enabled", dev.xkmc.youkaishomecoming.content.spell.analysis.SpellSelfTestFlags.enabled("yhdev.selftest", "YHDEV_SELFTEST"));
		System.setProperty("yhdev.selftest", "");
		check("flag empty disabled", !dev.xkmc.youkaishomecoming.content.spell.analysis.SpellSelfTestFlags.enabled("yhdev.selftest", "YHDEV_SELFTEST"));
		System.clearProperty("yhdev.selftest");
		check("flag absent disabled", !dev.xkmc.youkaishomecoming.content.spell.analysis.SpellSelfTestFlags.enabled("yhdev.selftest", "YHDEV_SELFTEST"));
	}

	/**
	 * Every JSON fixture of the server self-check must parse with plain Gson.
	 * Loading SpellAnalyzerSelfCheck only initializes String constants (no codec
	 * chain), so this runs in the pure-JVM harness and prevents fixture syntax
	 * regressions like the extra "}" that crashed /yhdev on a live server.
	 */
	private static void testSelfCheckFixtureJsonValid() throws Exception {
		Class<?> runner = Class.forName("dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzerSelfCheck$Runner");
		java.lang.reflect.Field[] fields = runner.getDeclaredFields();
		int validated = 0;
		for (java.lang.reflect.Field f : fields) {
			if (f.getType() != String.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
			f.setAccessible(true);
			String json = (String) f.get(null);
			if (json == null) continue;
			try {
				JsonParser.parseString(json);
				validated++;
			} catch (Exception e) {
				failed++;
				System.out.println("FAIL  self-check fixture JSON invalid: " + f.getName() + " -> " + e.getMessage());
			}
		}
		check("self-check fixtures parse (" + validated + " strings)", validated > 0);
	}

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
		checkUnbounded("caster max health", NumberBounds.resolve(new NumberProviders.CasterMaxHealth()));
		checkUnbounded("target x", NumberBounds.resolve(new NumberProviders.TargetX()));
		checkUnbounded("target speed", NumberBounds.resolve(new NumberProviders.TargetSpeed()));
		checkUnbounded("target fly time", NumberBounds.resolve(new NumberProviders.TargetFlyTime()));
	}

	private static void testCasterMaxHealthKeyword() {
		NumberProvider parsed = dev.xkmc.youkaishomecoming.content.spell.definition.NumberExprParser
				.parse("caster_max_health");
		check("caster max health keyword parses", parsed instanceof NumberProviders.CasterMaxHealth);
		check("caster max health keyword round-trips", "caster_max_health".equals(
				dev.xkmc.youkaishomecoming.content.spell.definition.NumberExprParser.unparse(parsed)));
	}

	private static void testGaussianUnbounded() {
		checkUnbounded("gaussian", NumberBounds.resolve(new NumberProviders.GaussianRandom(0, 5)));
	}

	private static void testPowRootUnbounded() {
		// corner sampling is not a valid interval operation for pow/root; the resolver
		// must fail open to UNBOUNDED rather than return a wrong lower bound
		checkUnbounded("pow with zero-crossing base", NumberBounds.resolve(
				new NumberProviders.Pow(new NumberProviders.RandomRange(-2, 2), new NumberProviders.Constant(2))));
		checkUnbounded("pow with plain constants", NumberBounds.resolve(
				new NumberProviders.Pow(new NumberProviders.Constant(2), new NumberProviders.Constant(3))));
		checkUnbounded("root", NumberBounds.resolve(
				new NumberProviders.Root(new NumberProviders.Constant(8), new NumberProviders.Constant(3))));
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
		check("experimental_fire EXPERIMENTAL",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.EXPERIMENTAL_FIRE) == SpellCapabilityPolicy.EXPERIMENTAL);
		check("hooks ALLOW", SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_EXPIRY) == SpellCapabilityPolicy.ALLOW
				&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_TRAIL) == SpellCapabilityPolicy.ALLOW
				&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_HIT) == SpellCapabilityPolicy.ALLOW);
		check("boss_on_damage EXPERIMENTAL", SpellCapabilityPolicies.defaultPolicy(SpellCapability.BOSS_ON_DAMAGE) == SpellCapabilityPolicy.EXPERIMENTAL);
		check("teleport EXPERIMENTAL, erase OP_ONLY, clear ALLOW",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.TELEPORT) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.ERASE_ENEMY_DANMAKU) == SpellCapabilityPolicy.OP_ONLY
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.CLEAR_SCREEN) == SpellCapabilityPolicy.ALLOW);
		check("origin target/absolute ALLOW",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.ORIGIN_TARGET) == SpellCapabilityPolicy.ALLOW
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.ORIGIN_ABSOLUTE) == SpellCapabilityPolicy.ALLOW);
		check("confine EXPERIMENTAL, flag/phase/force/fire OP_ONLY",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.CONFINED_TARGET) == SpellCapabilityPolicy.EXPERIMENTAL
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.SET_ENTITY_FLAG) == SpellCapabilityPolicy.OP_ONLY
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.FORCE_PHASE) == SpellCapabilityPolicy.OP_ONLY
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.FORCE_SPELL) == SpellCapabilityPolicy.OP_ONLY
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.FIRE_SPELL) == SpellCapabilityPolicy.OP_ONLY);
		check("legacy DENY", SpellCapabilityPolicies.defaultPolicy(SpellCapability.LEGACY_TICKER) == SpellCapabilityPolicy.DENY);
		check("run_command OP_ONLY", SpellCapabilityPolicies.defaultPolicy(SpellCapability.RUN_COMMAND) == SpellCapabilityPolicy.OP_ONLY);
		check("visual ALLOW",
				SpellCapabilityPolicies.defaultPolicy(SpellCapability.SET_SPELL_CIRCLE) == SpellCapabilityPolicy.ALLOW
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.SHOW_SPELL_TITLE) == SpellCapabilityPolicy.ALLOW
						&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.YSM_RENDER) == SpellCapabilityPolicy.ALLOW);
		check("certification allowed set", SpellCapabilityPolicies.defaultPolicy(SpellCapability.BASE_FIRE).allowsCertification()
				&& SpellCapabilityPolicies.defaultPolicy(SpellCapability.HOOK_ON_HIT).allowsCertification()
				&& !SpellCapabilityPolicies.defaultPolicy(SpellCapability.EXPERIMENTAL_FIRE).allowsCertification()
				&& !SpellCapabilityPolicies.defaultPolicy(SpellCapability.TELEPORT).allowsCertification()
				&& !SpellCapabilityPolicies.defaultPolicy(SpellCapability.RUN_COMMAND).allowsCertification()
				&& !SpellCapabilityPolicies.defaultPolicy(SpellCapability.LEGACY_TICKER).allowsCertification());
	}

	private static void testPolicyIds() {
		check("stable ids unique", Set.of(SpellCapability.values()).stream().map(SpellCapability::id).distinct().count() == SpellCapability.values().length);
		check("byId roundtrip", SpellCapability.byId("teleport") == SpellCapability.TELEPORT);
		check("default for unknown is DENY", SpellCapabilityPolicies.defaultPolicy(null) == SpellCapabilityPolicy.DENY);
	}

	private static void testPlayerDanmakuPolicy() {
		var friendly = PlayerDanmakuPolicy.classifyTarget(false, false, false, false, false, false);
		var neutralMob = PlayerDanmakuPolicy.classifyTarget(false, false, false, false, false, true);
		var enemyNeutralMob = PlayerDanmakuPolicy.classifyTarget(false, false, false, false, true, true);
		var youkai = PlayerDanmakuPolicy.classifyTarget(false, false, false, true, false, false);
		var smallFairy = PlayerDanmakuPolicy.classifyTarget(false, false, true, true, false, false);
		var unteamedPlayer = PlayerDanmakuPolicy.classifyTarget(true, false, false, false, false, false);
		var teamedPlayer = PlayerDanmakuPolicy.classifyTarget(true, true, false, false, false, false);

		check("passive mobs are friendly spell targets", friendly == PlayerDanmakuPolicy.TargetDisposition.FRIENDLY);
		check("neutral mobs stay neutral", neutralMob == PlayerDanmakuPolicy.TargetDisposition.NEUTRAL);
		check("Enemy wins over NeutralMob", enemyNeutralMob == PlayerDanmakuPolicy.TargetDisposition.HOSTILE);
		check("ordinary YH characters are neutral", youkai == PlayerDanmakuPolicy.TargetDisposition.NEUTRAL);
		check("marked small fairies are hostile", smallFairy == PlayerDanmakuPolicy.TargetDisposition.HOSTILE);
		check("unteamed players are neutral", unteamedPlayer == PlayerDanmakuPolicy.TargetDisposition.NEUTRAL);
		check("teamed players are hostile", teamedPlayer == PlayerDanmakuPolicy.TargetDisposition.HOSTILE);
		check("untargeted spells hit hostile categories",
				PlayerDanmakuPolicy.isUntargetedTarget(teamedPlayer, false));
		check("engagement promotes neutral targets",
				PlayerDanmakuPolicy.isUntargetedTarget(youkai, true));
		check("untargeted spells spare unengaged neutral targets",
				!PlayerDanmakuPolicy.isUntargetedTarget(youkai, false));
		check("active players may receive danmaku", PlayerDanmakuPolicy.canReceiveDanmaku(false));
		check("beaten players reject danmaku", !PlayerDanmakuPolicy.canReceiveDanmaku(true));
		var certificationId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
		var bossId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
		check("certification's own session is not another battle",
				!PlayerDanmakuPolicy.hasForeignSession(java.util.List.of(certificationId), certificationId));
		check("a different Youkai session is another battle",
				PlayerDanmakuPolicy.hasForeignSession(java.util.List.of(certificationId, bossId), certificationId));
		var firstSegment = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellProgressSnapshot(
				499, 500, 20, 400, 0, new int[]{500, 800});
		var secondSegment = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellProgressSnapshot(
				800, 800, 0, 800, 500, new int[]{500, 800});
		check("multi-stage progress preserves declared order",
				java.util.Arrays.equals(firstSegment.healthSegments(), new int[]{500, 800}));
		check("first-stage damage uses the fixed combined denominator",
				firstSegment.totalHealth() == 1300 && firstSegment.totalRemainingHealth() == 1299);
		check("entering the next stage preserves its full future arc",
				secondSegment.totalHealth() == 1300 && secondSegment.totalRemainingHealth() == 800
						&& secondSegment.elapsedTicks() == 0 && secondSegment.durationTicks() == 800);
		check("phase switch discards unused health from the completed stage",
				Math.min(1250, secondSegment.totalRemainingHealth()) == 800);
	}
}
