package dev.xkmc.youkaishomecoming.content.spell.analysis;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.action.RunCommandAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.spell.action.SetSpellHealthAction;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders;
import dev.xkmc.youkaishomecoming.content.spell.game.MigratedSpellCards;
import dev.xkmc.youkaishomecoming.content.spell.market.SpellMarketValidator;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.template.SpellTemplates;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.capability.PlayerDanmakuPolicy;
import dev.xkmc.youkaishomecoming.content.entity.fairy.SmallFairyEntity;
import dev.xkmc.youkaishomecoming.content.entity.rumia.RumiaEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side self-test for the Phase 0 analyzer contract (P0.5).
 * OP-only, no world side effects, code-internal fixtures only.
 * Runs under a fully initialized FML/ModLauncher environment, where the pure-JVM
 * harness cannot load the spell codec chain (see design doc D16).
 * <p>
 * Covers: codec/hash stability and round-trip rules, analyzer traversal and
 * amplification math, fail-closed unbounded values, saturation, phase-cycle
 * handling, MARKET vs CERTIFICATION profile differences, historical market
 * error texts, DisabledAction semantics and the market facade delegation.
 */
public final class SpellAnalyzerSelfCheck {

	/** Deterministic certification limits independent of server config (window 1000 ticks). */
	private static final SpellAnalysisLimits CERT = new SpellAnalysisLimits(64, 4096, 32, 256, 8192, 4096, 12000, 512,
			100_000, 10_000_000, 1_000_000_000L, 1_000_000_000L, 4, 1000);

	private SpellAnalyzerSelfCheck() {
	}

	public record Result(int total, int passed, String firstFailureDetail, List<String> failures) {
		public boolean allPassed() {
			return passed == total;
		}
	}

	public static Result run() {
		return new Runner().run(null);
	}

	/** Player-scoped run (command context); headless runs pass null and skip player-gated checks. */
	public static Result run(net.minecraft.server.level.ServerPlayer player) {
		return new Runner().run(player);
	}

	private static final class Runner {

		private int total;
		private int passed;
		private String firstFailure;
		private final List<String> failures = new ArrayList<>();

		private void check(String name, boolean ok, String detail) {
			total++;
			if (ok) {
				passed++;
			} else {
				String line = detail == null || detail.isEmpty() ? name : name + " | " + detail;
				if (firstFailure == null) firstFailure = line;
				failures.add(line);
			}
		}

		private void check(String name, boolean ok) {
			check(name, ok, "");
		}

		private boolean rejects(ThrowingRunnable r) {
			try {
				r.run();
				return false;
			} catch (SpellAnalysisException e) {
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		private String rejectMessage(ThrowingRunnable r) {
			try {
				r.run();
				return null;
			} catch (SpellAnalysisException e) {
				StringBuilder sb = new StringBuilder(e.getMessage());
				if (!e.diagnostics().isEmpty()) {
					sb.append(" [").append(e.diagnostics().get(0)).append("]");
				}
				return sb.toString();
			} catch (IllegalArgumentException e) {
				// HistoricalMarketJsonGuard throws plain IAE; its messages must be readable
				return e.getMessage();
			} catch (Exception e) {
				return "WRONG_EXCEPTION: " + e.getClass().getName();
			}
		}

		private SpellDefinition parse(String json) {
			JsonElement el = JsonParser.parseString(json);
			return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, el)
					.result()
					.orElseThrow(() -> new IllegalStateException("fixture parse failed"));
		}

		/** Analyze with diagnostics: on failure stores the real exception in lastError. */
		private String lastError;

		private SpellAnalysis analysis(String fixture, SpellAnalysisProfile profile, SpellAnalysisLimits limits) {
			lastError = null;
			try {
				return SpellAnalyzer.analyze(parse(fixture), profile, limits);
			} catch (Exception | AssertionError t) {
				// VirtualMachineError/ThreadDeath propagate (review B)
				lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
				return null;
			}
		}

		private static SpellAction firstTickAction(SpellDefinition def) {
			return def.phases.values().iterator().next().onTick.get(0);
		}

		// ------------------------------------------------------------ fixtures

		private static String spell(String onTick) {
			return "{\n" +
					"  \"id\": \"youkaishomecoming:analyzer_test\",\n" +
					"  \"display\": {\"name\": \"Analyzer Test\"},\n" +
					"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
					"  \"phases\": {\n" +
					"    \"youkaishomecoming:main\": {\n" +
					"      \"id\": \"youkaishomecoming:main\",\n" +
					"      \"on_tick\": [" + onTick + "]\n" +
					"    }\n" +
					"  }\n" +
					"}";
		}

		private static String fire(int count) {
			return "{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": " + count
					+ ", \"speed\": 0.5, \"lifetime\": 60}";
		}

		private static final String FIRE24 = spell(fire(24));
		private static final String FIRE25 = spell(fire(25));
		private static final String AMP = spell("{\"type\": \"repeat\", \"count\": 8, \"body\": [{\"type\": \"burst\", \"waves\": 4, \"body\": [" + fire(6) + "]}]}");
		private static final String OUTER = spell("{\"type\": \"repeat\", \"count\": 8, \"body\": [{\"type\": \"burst\", \"waves\": 4, \"body\": ["
				+ fire(6).replace("\"lifetime\": 60}", "\"lifetime\": 60, \"outer_count\": 3}") + "]}]}");
		private static final String SHOOTER = spell("{\"type\": \"spawn_shooter\", \"count\": 3, \"speed\": 0.5, \"lifetime\": 100, \"body\": [" + fire(4) + "]}");
		private static final String TRAIL = spell("{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 24, \"speed\": 0.5, \"lifetime\": 60,\n"
				+ "  \"trail_interval\": 5, \"on_trail\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 30}]}");
		private static final String ON_HIT = spell("{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 24, \"speed\": 0.5, \"lifetime\": 60,\n"
				+ "  \"hit_behavior_entity\": \"continue\", \"on_hit_entity\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 30}]}");
		private static final String NON_SPELL_SAFE = spell("{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 60, \"hit_behavior_block\": \"discard\"}");
		private static final String NON_SPELL_CONTINUE = NON_SPELL_SAFE.replace("\"hit_behavior_block\": \"discard\"", "\"hit_behavior_block\": \"continue\"");
		private static final String NON_SPELL_HOOK = NON_SPELL_SAFE.replace("\"hit_behavior_block\": \"discard\"}", "\"hit_behavior_block\": \"discard\", \"on_hit_entity\": [{\"type\": \"set_variable\", \"key\": \"x\", \"value\": 1}]}");
		private static final String NON_SPELL_FAST = NON_SPELL_SAFE.replace("\"speed\": 0.5", "\"speed\": 200");
		private static final String NON_SPELL_HOMING = NON_SPELL_SAFE.replace("\"lifetime\": 60", "\"lifetime\": 60, \"mover\": {\"type\": \"homing\", \"speed\": 0.45, \"delay\": 8}");
		private static final String NON_SPELL_UNBOUNDED_ACCELERATION = NON_SPELL_SAFE.replace("\"speed\": 0.5", "\"speed\": 0").replace("\"lifetime\": 60", "\"lifetime\": 60, \"mover\": {\"type\": \"acceleration\", \"y\": -0.05}");
		private static final String NON_SPELL_BOUNDED_ACCELERATION = NON_SPELL_UNBOUNDED_ACCELERATION.replace("\"y\": -0.05", "\"y\": -0.05, \"terminal_vy\": -1.0");
		private static final String NON_SPELL_LASER = spell("{\"type\": \"fire_laser\", \"laser\": \"laser\", \"color\": \"red\", \"lifetime\": 20, \"length\": 8}");
		private static final String PERIODIC = spell("{\"type\": \"conditional\", \"condition\": {\"type\": \"tick_interval\", \"interval\": 200}, \"if_true\": [" + fire(1) + "]}");
		private static final String EVENT_DRIVEN = spell(
				"{\"type\": \"conditional\", \"condition\": {\"type\": \"tick_interval\", \"interval\": 200}, \"if_true\": ["
						+ fire(1).replace("\"lifetime\": 60}", "\"lifetime\": 2000, \"on_hit_block\": [{\"type\": \"set_variable\", \"key\": \"var\", \"value\": 1}]}")
						+ "]}, {\"type\": \"conditional\", \"condition\": {\"type\": \"compare\", \"left\": {\"type\": \"variable\", \"key\": \"var\"}, \"op\": \"==\", \"right\": 1}, \"if_true\": [{\"type\": \"repeat\", \"count\": 80, \"body\": ["
						+ fire(1).replace("\"lifetime\": 60}", "\"lifetime\": 100}")
						+ "]}, {\"type\": \"set_variable\", \"key\": \"var\", \"value\": 0}], \"if_false\": []}");
		private static final String EVENT_WITHOUT_RESET = EVENT_DRIVEN.replace(
				", {\"type\": \"set_variable\", \"key\": \"var\", \"value\": 0}", "");
		private static final String BOUNCE_HIT = spell(
				"{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 60,"
						+ " \"on_hit_block\": [{\"type\": \"hold_source\", \"duration\": 20, \"on_release\": [{\"type\": \"bounce_source\", \"max_bounces\": 30}]}]}");
		private static final String LEGACY = spell("{\"type\": \"legacy_ticker\"}");
		private static final String LEGACY_IN_BURST = spell("{\"type\": \"burst\", \"waves\": 3, \"body\": [{\"type\": \"legacy_ticker\"}]}");
		private static final String LEGACY_DEEP = spell("{\"type\": \"delay\", \"delay_ticks\": 10, \"body\": [{\"type\": \"repeat\", \"count\": 2, \"body\": [{\"type\": \"spawn_shooter\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 20, \"body\": [{\"type\": \"legacy_ticker\"}]}]}]}");
		private static final String RUNCMD = spell("{\"type\": \"run_command\", \"command\": \"say hi\"}");
		private static final String RUNCMD_HIT = spell("{\"type\": \"run_command\","
				+ " \"hit_context\": \"at_entity_pos\", \"command\": \"say hit\"}");
		private static final String RUNCMD_DISABLED = spell("{\"type\": \"disabled\", \"inner\": {\"type\": \"run_command\", \"command\": \"say hi\"}}");
		private static final String SPELL_HEALTH = spell("{\"type\": \"set_spell_health\", \"health\": 100, \"duration\": 120}");
		private static final String SPELL_HEALTH_DEFAULTS = spell("{\"type\": \"set_spell_health\"}");
		private static final String SPELL_HEALTH_TARGETS = spell("{\"type\": \"set_spell_health\", \"health\": 100, \"duration\": 120,"
				+ " \"on_timeout\": {\"type\": \"force_phase\", \"phase_id\": \"youkaishomecoming:main\"},"
				+ " \"on_break\": {\"type\": \"force_spell\", \"spell_id\": \"youkaishomecoming:analyzer_test\"}}");
		private static final String SPELL_HEALTH_INVALID_TARGET = spell("{\"type\": \"set_spell_health\","
				+ " \"on_timeout\": {\"type\": \"clear_screen\"}}");
		private static final String TELEPORT = spell("{\"type\": \"teleport\", \"destination\": {\"mode\": \"caster\"}}");
		private static final String CASTER_MOVES = spell("{\"type\": \"caster_moves\", \"mode\": \"relative\", \"x\": \"tick * 0.01\", \"y\": 0, \"z\": 0.1}," + fire(1));
		private static final String UNBOUNDED_COUNT = spell(
				"{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": {\"type\": \"variable\", \"key\": \"x\"}, \"speed\": 0.5, \"lifetime\": 60}");
		private static final String RANDOM_COUNT = spell(
				"{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": {\"type\": \"random\", \"min\": 1, \"max\": 10}, \"speed\": 0.5, \"lifetime\": 60}");
		private static final String CYCLE = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cycle\",\n" +
				"  \"display\": {\"name\": \"Cycle\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\", \"on_tick\": [" + fire(6) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 100}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\", \"on_tick\": [" + fire(6) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 100}, \"target_phase\": \"youkaishomecoming:main\"}]}\n" +
				"  }\n" +
				"}";
		private static final String REORDERED = spell(fire(24))
				.replace("\"bullet\": \"ball\", \"color\": \"red\", \"count\": 24, \"speed\": 0.5, \"lifetime\": 60",
						"\"color\": \"red\", \"lifetime\": 60, \"speed\": 0.5, \"count\": 24, \"bullet\": \"ball\"");
		private static final String ALL_CAPS = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_caps\",\n" +
				"  \"display\": {\"name\": \"Caps\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\n" +
				"      \"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_tick\": [" +
				"{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 6, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_hit_entity\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 30}],\n" +
				"        \"origin\": {\"mode\": \"target\"}},\n" +
				"        {\"type\": \"teleport\", \"destination\": {\"mode\": \"caster\"}},\n" +
				"        {\"type\": \"set_entity_flag\", \"flag\": 1},\n" +
				"        {\"type\": \"ysm_render\", \"model\": \"x\"},\n" +
				"        {\"type\": \"erase_enemy_danmaku\"},\n" +
				"        {\"type\": \"clear_screen\"},\n" +
				"        {\"type\": \"confine_target\", \"max_distance\": 10},\n" +
				"        {\"type\": \"set_spell_circle\"},\n" +
				"        {\"type\": \"show_spell_title\", \"name\": \"t\"}],\n" +
				"      \"on_damage\": [" + fire(2) + "]\n" +
				"    }\n" +
				"  }\n" +
				"}";

		// local fixtures hoisted to static constants so the pure-JVM build-time
		// validation (gen.SpellAnalyzerSelfTest) covers their JSON syntax
		private static final String DISABLED_FIRE = spell("{\"type\": \"disabled\", \"inner\": " + fire(24) + "}");
		private static final String SAT = spell("{\"type\": \"repeat\", \"count\": 1000, \"body\": [{\"type\": \"burst\", \"waves\": 1000, \"body\": [" + fire(1000) + "]}]}");
		private static final String ONE_SHOT_BURST = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_burst\",\n" +
				"  \"display\": {\"name\": \"Burst\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [" + fire(1000) + "],\n" +
				"      \"on_tick\": [" + fire(6) + "]}\n" +
				"  }\n" +
				"}";
		private static final String ENTER_SHOOTER = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_shooter_enter\",\n" +
				"  \"display\": {\"name\": \"ShooterEnter\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"spawn_shooter\", \"count\": 3, \"speed\": 0.5, \"lifetime\": 100, \"body\": [" + fire(4) + "]}]}\n" +
				"  }\n" +
				"}";
		private static final String SAME_TICK = spell("{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 400, \"speed\": 0.5, \"lifetime\": 60},\n"
				+ "  {\"type\": \"spawn_shooter\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 100, \"body\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 4, \"speed\": 0.5, \"lifetime\": 60}]}");
		private static final String EXPIRY_BURST = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_expiry_burst\",\n" +
				"  \"display\": {\"name\": \"ExpiryBurst\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1000, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 10, \"speed\": 0.5, \"lifetime\": 30}]}]}\n" +
				"  }\n" +
				"}";
		private static final String HOOK_REPEAT = spell("{\"type\": \"repeat\", \"count\": 10, \"body\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 2, \"speed\": 0.5, \"lifetime\": 60,\n"
				+ "  \"on_hit_entity\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 30}]}]}");
		private static final String LONG_LIFETIME = FIRE24.replace("\"lifetime\": 60}", "\"lifetime\": 20000}");
		private static final String OBJ_LIFETIME = FIRE24.replace("\"lifetime\": 60}",
				"\"lifetime\": {\"type\": \"random\", \"min\": 1, \"max\": 20000}}");
		private static final String DISABLED_HOOK = spell("{\"type\": \"disabled\", \"inner\": {\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 60,\n"
				+ "  \"on_hit_entity\": [{\"type\": \"run_command\", \"command\": \"say hi\"}]}}");
		private static final String DEEP_NEST = deepNest();
		private static final String LONG_STR = longStr();
		private static final String EMPTY_PHASES = "{\"id\": \"youkaishomecoming:x\", \"display\": {\"name\": \"x\"}, \"entry_phase\": \"youkaishomecoming:main\", \"phases\": {}}";
		private static final String MANY_ACTIONS = manyActions();
		private static final String PARALLEL_SHOOTERS = spell(
				"{\"type\": \"spawn_shooter\", \"count\": 3, \"speed\": 0.5, \"lifetime\": 100, \"body\": [" + fire(4) + "]},\n"
						+ "  {\"type\": \"spawn_shooter\", \"count\": 3, \"speed\": 0.5, \"lifetime\": 100, \"body\": [" + fire(4) + "]}");
		private static final String PARALLEL_EXPIRY = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_parallel_expiry\",\n" +
				"  \"display\": {\"name\": \"ParallelExpiry\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1000, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 10, \"speed\": 0.5, \"lifetime\": 30}]},\n" +
				"        {\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"green\", \"count\": 1000, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"yellow\", \"count\": 10, \"speed\": 0.5, \"lifetime\": 30}]}]}\n" +
				"  }\n" +
				"}";
		/** Deferred one-shot batch overlapping an ordinary on_tick (review A1). */
		private static final String DEFERRED_ORDINARY = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_deferred_ordinary\",\n" +
				"  \"display\": {\"name\": \"DeferredOrdinary\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1000, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 10, \"speed\": 0.5, \"lifetime\": 30}]}],\n" +
				"      \"on_tick\": [" + fire(400) + "]}\n" +
				"  }\n" +
				"}";
		/** Immediate container must inherit the outer group: nested and sibling deferred
		 * batches share the expiry tick (review A2). */
		private static final String NESTED_EXPIRY = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_nested_expiry\",\n" +
				"  \"display\": {\"name\": \"NestedExpiry\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"sequence\", \"actions\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1000, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 10, \"speed\": 0.5, \"lifetime\": 30}]}]},\n" +
				"        {\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"green\", \"count\": 1000, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"yellow\", \"count\": 10, \"speed\": 0.5, \"lifetime\": 30}]}]}\n" +
				"  }\n" +
				"}";
		/** Equal delays share the same scheduled tick; their bursts must SUM (round 5 A1). */
		private static final String PARALLEL_DELAY = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_parallel_delay\",\n" +
				"  \"display\": {\"name\": \"ParallelDelay\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"delay\", \"delay_ticks\": 60, \"body\": [" + fire(400) + "]},\n" +
				"        {\"type\": \"delay\", \"delay_ticks\": 60, \"body\": [" + fire(400) + "]}]}\n" +
				"  }\n" +
				"}";
		/** Equal delays plus an ordinary on_tick in the same server tick (round 5 A1). */
		private static final String PARALLEL_DELAY_TICK = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_parallel_delay_tick\",\n" +
				"  \"display\": {\"name\": \"ParallelDelayTick\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"delay\", \"delay_ticks\": 60, \"body\": [" + fire(400) + "]},\n" +
				"        {\"type\": \"delay\", \"delay_ticks\": 60, \"body\": [" + fire(400) + "]}],\n" +
				"      \"on_tick\": [" + fire(100) + "]}\n" +
				"  }\n" +
				"}";
		/** on_exit of the old phase and on_enter of the new phase run in the same
		 * doTransition tick; their bursts must SUM (round 5 A2). */
		private static final String TRANSITION_BURST = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_transition_burst\",\n" +
				"  \"display\": {\"name\": \"TransitionBurst\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_exit\": [" + fire(500) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 100}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\", \"on_enter\": [" + fire(500) + "]}\n" +
				"  }\n" +
				"}";
		/** Transition tick also overlaps the old phase ordinary on_tick (round 5 A2). */
		private static final String TRANSITION_BURST_TICK = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_transition_burst_tick\",\n" +
				"  \"display\": {\"name\": \"TransitionBurstTick\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_tick\": [" + fire(100) + "], \"on_exit\": [" + fire(500) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 100}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\", \"on_enter\": [" + fire(500) + "]}\n" +
				"  }\n" +
				"}";
		/** delay <= 0 executes immediately in the current tick: its burst joins the
		 * direct entry burst (round 6, issue 1). */
		private static final String ZERO_DELAY_IMMEDIATE = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_zero_delay\",\n" +
				"  \"display\": {\"name\": \"ZeroDelay\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [" + fire(500) + ",\n" +
				"        {\"type\": \"delay\", \"delay_ticks\": 0, \"body\": [" + fire(500) + "]}]}\n" +
				"  }\n" +
				"}";
		private static final String ZERO_DELAY_IMMEDIATE_TICK = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_zero_delay_tick\",\n" +
				"  \"display\": {\"name\": \"ZeroDelayTick\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [" + fire(500) + ",\n" +
				"        {\"type\": \"delay\", \"delay_ticks\": 0, \"body\": [" + fire(500) + "]}],\n" +
				"      \"on_tick\": [" + fire(100) + "]}\n" +
				"  }\n" +
				"}";
		/** A positive Delay and an expiry batch can land on the same tick (round 6,
		 * issue 2): direct (1) + delayed (500) + deferred (10,000) all sum. */
		private static final String DELAY_DEFERRED_MERGE = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_delay_deferred_merge\",\n" +
				"  \"display\": {\"name\": \"DelayDeferredMerge\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:main\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:main\": {\"id\": \"youkaishomecoming:main\",\n" +
				"      \"on_enter\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 1, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"        \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 10000, \"speed\": 0.5, \"lifetime\": 30}]},\n" +
				"        {\"type\": \"delay\", \"delay_ticks\": 60, \"body\": [" + fire(500) + "]}]}\n" +
				"  }\n" +
				"}";
		/** Cyclic phase spawning from on_enter must be rejected (round 7, plan B). */
		private static final String CYCLIC_ENTER_DIRECT = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cyclic_enter\",\n" +
				"  \"display\": {\"name\": \"CyclicEnter\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_enter\": [" + fire(100) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\", \"on_enter\": [" + fire(100) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:a\"}]}\n" +
				"  }\n" +
				"}";
		private static final String CYCLIC_ENTER_HOOK = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cyclic_enter_hook\",\n" +
				"  \"display\": {\"name\": \"CyclicEnterHook\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_enter\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"red\", \"count\": 10, \"speed\": 0.5, \"lifetime\": 60,\n" +
				"      \"on_expiry\": [{\"type\": \"fire_danmaku\", \"bullet\": \"ball\", \"color\": \"blue\", \"count\": 2, \"speed\": 0.5, \"lifetime\": 30}]}],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\",\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:a\"}]}\n" +
				"  }\n" +
				"}";
		private static final String CYCLIC_ENTER_SHOOTER = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cyclic_enter_shooter\",\n" +
				"  \"display\": {\"name\": \"CyclicEnterShooter\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_enter\": [{\"type\": \"spawn_shooter\", \"count\": 3, \"speed\": 0.5, \"lifetime\": 100, \"body\": [" + fire(4) + "]}],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\",\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:a\"}]}\n" +
				"  }\n" +
				"}";
		/** Cyclic phase with spawn-free on_enter/on_exit still passes (round 7). */
		private static final String CYCLIC_ENTER_SAFE = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cyclic_enter_safe\",\n" +
				"  \"display\": {\"name\": \"CyclicEnterSafe\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_enter\": [{\"type\": \"play_sound\", \"sound\": \"minecraft:block.note_block.pling\", \"volume\": 1.0, \"pitch\": 1.0}],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\",\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:a\"}]}\n" +
				"  }\n" +
				"}";
		/** SCC member joined through a cross edge must also be detected (round 8). */
		private static final String CYCLIC_BRANCH_MEMBER = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cyclic_branch\",\n" +
				"  \"display\": {\"name\": \"CyclicBranch\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\",\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:b\"},\n" +
				"        {\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:d\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\",\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:c\"}]},\n" +
				"    \"youkaishomecoming:c\": {\"id\": \"youkaishomecoming:c\",\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:a\"}]},\n" +
				"    \"youkaishomecoming:d\": {\"id\": \"youkaishomecoming:d\", \"on_enter\": [" + fire(100) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:c\"}]}\n" +
				"  }\n" +
				"}";
		/** Self-loop is a cycle (round 8). */
		private static final String CYCLIC_SELF_LOOP = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cyclic_selfloop\",\n" +
				"  \"display\": {\"name\": \"CyclicSelfLoop\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_enter\": [" + fire(100) + "],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:a\"}]}\n" +
				"  }\n" +
				"}";
		/** Disabled spawn inside a cyclic on_enter never executes: still passes (round 8). */
		private static final String CYCLIC_ENTER_DISABLED = "{\n" +
				"  \"id\": \"youkaishomecoming:analyzer_cyclic_disabled\",\n" +
				"  \"display\": {\"name\": \"CyclicDisabled\"},\n" +
				"  \"entry_phase\": \"youkaishomecoming:a\",\n" +
				"  \"phases\": {\n" +
				"    \"youkaishomecoming:a\": {\"id\": \"youkaishomecoming:a\", \"on_enter\": [{\"type\": \"disabled\", \"inner\": " + fire(100) + "}],\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:b\"}]},\n" +
				"    \"youkaishomecoming:b\": {\"id\": \"youkaishomecoming:b\",\n" +
				"      \"transitions\": [{\"condition\": {\"type\": \"tick_elapsed\", \"ticks\": 1}, \"target_phase\": \"youkaishomecoming:a\"}]}\n" +
				"  }\n" +
				"}";

		private static String deepNest() {
			StringBuilder sb = new StringBuilder(fire(6));
			for (int i = 0; i < 40; i++) {
				sb.insert(0, "{\"type\": \"sequence\", \"actions\": [");
				sb.append("]}");
			}
			return spell(sb.toString());
		}

		private static String longStr() {
			String longValue = "x".repeat(600);
			return spell(fire(6)).replace("\"display\": {\"name\": \"Analyzer Test\"},",
					"\"display\": {\"name\": \"Analyzer Test\"}, \"custom_names\": {\"long\": \"" + longValue + "\"},");
		}

		private static String manyActions() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 500; i++) {
				if (i > 0) sb.append(',');
				sb.append(fire(6));
			}
			return spell(sb.toString());
		}

		Result run(@Nullable net.minecraft.server.level.ServerPlayer player) {
			try {
				codecAndHash();
				specialCardAndReplicaContracts();
				migratedBossHealth();
				playerSpellPvpPolicy();
				analyzerTraversal();
				projectionModel();
				oneShotBurst();
				shooterModel();
				sameTickSum();
				parallelShooters();
				deferredHookBurst();
				parallelExpiryBursts();
				deferredPlusOrdinary();
				nestedExpiry();
				parallelDelays();
				transitionBurst();
				zeroDelay();
				delayDeferredMerge();
				cyclicPhaseSpawns();
				hookMultiplier();
				profileDifferences();
				disabledSemantics();
				marketFacade();
				unknownAction();
				hashOrderIndependence();
				healthPlanValidation();
				if (player != null) {
					certificationQuoteSanity(player);
				}
			} catch (Exception | AssertionError t) {
				// never crash the command: surface the real error as a failure entry.
				// VirtualMachineError/ThreadDeath are intentionally not caught (review B)
				total++;
				if (firstFailure == null) {
					firstFailure = "CRASH: " + t.getClass().getName() + ": " + t.getMessage();
					for (StackTraceElement e : t.getStackTrace()) {
						if (e.getClassName().contains("youkaishomecoming")) {
							firstFailure += " | " + e;
						}
					}
				}
				failures.add("CRASH: " + t.getClass().getName() + ": " + t.getMessage());
			}
			return new Result(total, passed, firstFailure, List.copyOf(failures));
		}

		private void codecAndHash() {
			// 1. identical definitions hash identically
			check("hash stable for identical definitions",
					SpellHash.canonicalHash(parse(FIRE24)).equals(SpellHash.canonicalHash(parse(FIRE24))));
			// 2. canonical round-trip is stable: hash of a definition equals hash of its own re-parse
			SpellDefinition def = parse(FIRE24);
			JsonElement canonical = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).result().orElseThrow();
			check("hash stable across canonical re-encode/re-decode",
					SpellHash.canonicalHash(def).equals(SpellHash.canonicalHash(parse(canonical.toString()))));
			SetSpellHealthAction health = (SetSpellHealthAction) firstTickAction(parse(SPELL_HEALTH_TARGETS));
			check("spell health timeout target codec", health.onTimeout().orElse(null) instanceof SpellActions.ForcePhase);
			check("spell health break target codec", health.onBreak().orElse(null) instanceof SpellActions.ForceSpell);
			SetSpellHealthAction defaults = (SetSpellHealthAction) firstTickAction(parse(SPELL_HEALTH_DEFAULTS));
			check("spell health default is 50 HP / 100 ticks",
					defaults.health() instanceof NumberProviders.Constant hp && hp.value() == 50
							&& defaults.duration() instanceof NumberProviders.Constant duration
							&& duration.value() == 100);
			check("spell health rejects unrelated embedded action",
					rejectMessage(() -> parse(SPELL_HEALTH_INVALID_TARGET)) != null);
			SpellDefinition bossTemplate = SpellTemplates.create(
					new ResourceLocation("youkaishomecoming", "selftest_boss"), "boss");
			check("boss template has two phases", bossTemplate.phases.size() == 2);
			check("boss template declares spell health",
					bossTemplate.phases.values().stream().flatMap(p -> p.onEnter.stream())
							.anyMatch(action -> action instanceof SetSpellHealthAction));
			check("boss template scopes phase node names",
					bossTemplate.customNames.containsKey("intro/enter/0")
							&& bossTemplate.customNames.containsKey("final/enter/0"));
			SpellRuntime bossRuntime = new SpellRuntime(bossTemplate);
			check("boss health plan exposes all phase arcs before transition",
					java.util.Arrays.equals(bossRuntime.getSpellHealthSegments(), new int[]{500, 800}));
			check("boss plan retains the quote duration without rendering future timeouts",
					bossRuntime.getSpellPlanDurationTicks() == 1200
							&& bossRuntime.getSpellDurationTicks() == 0);
			bossRuntime.setSpellHealth(500, 400);
			check("entering first boss phase keeps planned arc proportions",
					java.util.Arrays.equals(bossRuntime.getSpellHealthSegments(), new int[]{500, 800})
							&& bossRuntime.getSpellHealthTotal() == 1300
							&& bossRuntime.getSpellDurationTicks() == 400);
			SpellRuntime commandDurationRuntime = new SpellRuntime(bossTemplate);
			commandDurationRuntime.setDurationOverride(2400);
			commandDurationRuntime.setSpellHealth(500, 400);
			check("command duration overrides set_spell_health timeout",
					commandDurationRuntime.getSpellDurationTicks() == 2400
							&& commandDurationRuntime.getSpellPlanDurationTicks() == 2400);
			SpellRuntime shorterCommandDurationRuntime = new SpellRuntime(bossTemplate);
			shorterCommandDurationRuntime.setDurationOverride(60);
			shorterCommandDurationRuntime.setSpellHealth(500, 400);
			check("shorter command duration also overrides set_spell_health projection",
					shorterCommandDurationRuntime.getSpellDurationTicks() == 60
							&& shorterCommandDurationRuntime.getSpellPlanDurationTicks() == 60);
			SpellRuntime restoredOverrideRuntime = new SpellRuntime(bossTemplate);
			restoredOverrideRuntime.loadFromTag(commandDurationRuntime.saveToTag());
			check("command duration override survives runtime projection persistence",
					restoredOverrideRuntime.getSpellDurationTicks() == 2400
							&& restoredOverrideRuntime.getSpellPlanDurationTicks() == 2400);
			SpellRuntime restoredBossRuntime = new SpellRuntime(bossTemplate);
			restoredBossRuntime.loadFromTag(bossRuntime.saveToTag());
			check("shared boss rings survive runtime persistence",
					java.util.Arrays.equals(restoredBossRuntime.getSpellHealthSegments(), new int[]{500, 800})
							&& restoredBossRuntime.getSpellPlanDurationTicks() == 1200
							&& restoredBossRuntime.getSpellDurationTicks() == 400);
			// 3. JSON object field order does not affect hash
			check("hash independent of JSON field order",
					SpellHash.canonicalHash(parse(REORDERED)).equals(SpellHash.canonicalHash(parse(FIRE24))));
			// 4. any runtime-relevant field change changes the hash
			check("hash changes on count change",
					!SpellHash.canonicalHash(parse(FIRE24)).equals(SpellHash.canonicalHash(parse(FIRE25))));
			check("hash changes on lifetime change",
					!SpellHash.canonicalHash(parse(FIRE24)).equals(SpellHash.canonicalHash(parse(FIRE24.replace("\"lifetime\": 60}", "\"lifetime\": 61}")))));
			check("hash changes on phase id change",
					!SpellHash.canonicalHash(parse(FIRE24)).equals(SpellHash.canonicalHash(parse(FIRE24.replace("youkaishomecoming:main", "youkaishomecoming:main2")))));
			// 5. disabled toggle changes hash
			check("hash changes when action toggles disabled",
					!SpellHash.canonicalHash(parse(FIRE24)).equals(SpellHash.canonicalHash(parse(DISABLED_FIRE))));
			// 6. legacy rejected before hash (D9)
			check("hash rejects legacy_ticker", rejects(() -> SpellHash.canonicalHash(parse(LEGACY))));
			// 7. non-round-trippable definition cannot produce a hash: legacy is the only
			// known non-round-trippable node; unknown action types fail at decode instead
			boolean unknownRejected;
			try {
				parse(spell("{\"type\": \"not_a_real_action\"}"));
				unknownRejected = false;
			} catch (Exception e) {
				unknownRejected = true;
			}
			check("unknown action type fails decode (no definition, no hash)", unknownRejected);
			SpellAction movement = firstTickAction(parse(CASTER_MOVES));
			check("caster_moves codec round-trip", movement instanceof dev.xkmc.youkaishomecoming.content.spell.action.CasterMovesAction action
					&& action.mode() == dev.xkmc.youkaishomecoming.content.spell.runtime.SpellMovementDirective.Mode.RELATIVE);
			RunCommandAction legacyCommand = (RunCommandAction) firstTickAction(parse(RUNCMD));
			check("run_command legacy JSON defaults hit context",
					legacyCommand.hitContext() == RunCommandAction.HitContext.DEFAULT);
			RunCommandAction hitCommand = (RunCommandAction) firstTickAction(parse(RUNCMD_HIT));
			check("run_command hit context codec round-trip",
					hitCommand.hitContext() == RunCommandAction.HitContext.AT_ENTITY_POS);
		}

		private void migratedBossHealth() {
			List<SpellDefinition> definitions = List.of(
					MigratedSpellCards.sunnyMilk(), MigratedSpellCards.lunaChild(),
					MigratedSpellCards.starSapphire(), MigratedSpellCards.cirno(),
					MigratedSpellCards.mystia(), MigratedSpellCards.youmu(),
					MigratedSpellCards.larva(), MigratedSpellCards.sanae(),
					MigratedSpellCards.clown(), MigratedSpellCards.sakuya(),
					MigratedSpellCards.kisin(), MigratedSpellCards.remilia(),
					MigratedSpellCards.doremi(), MigratedSpellCards.koishi(),
					MigratedSpellCards.reimu(), MigratedSpellCards.yukari());
			for (SpellDefinition definition : definitions) {
				String name = definition.id.toString();
				List<SetSpellHealthAction> healthActions = definition.phases.values().stream()
						.flatMap(phase -> phase.onEnter.stream())
						.filter(SetSpellHealthAction.class::isInstance)
						.map(SetSpellHealthAction.class::cast)
						.toList();
				SetSpellHealthAction health = healthActions.size() == 1 ? healthActions.get(0) : null;
				PhaseDefinition entry = definition.getPhase(definition.entryPhase);
				check(name + " has exactly one entry health declaration",
						health != null && entry != null && entry.onEnter.contains(health));
				check(name + " uses caster_max_health",
						health != null && health.health() instanceof NumberProviders.CasterMaxHealth);
				check(name + " has no timeout for legacy compatibility",
						health != null && health.duration() instanceof NumberProviders.Constant duration
								&& duration.value() == 0);
				check(name + " exposes runtime duration to complete spell items",
						SpellHealthPlan.singleSegmentDuration(definition).orElse(-1) == 0);

				JsonElement encoded = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
						.result().orElseThrow();
				SpellDefinition decoded = parse(encoded.toString());
				PhaseDefinition decodedEntry = decoded.getPhase(decoded.entryPhase);
				SetSpellHealthAction decodedHealth = decodedEntry == null ? null : decodedEntry.onEnter.stream()
						.filter(SetSpellHealthAction.class::isInstance)
						.map(SetSpellHealthAction.class::cast)
						.findFirst().orElse(null);
				check(name + " health declaration survives codec round-trip",
						decodedHealth != null
								&& decodedHealth.health() instanceof NumberProviders.CasterMaxHealth
								&& decodedHealth.duration() instanceof NumberProviders.Constant duration
								&& duration.value() == 0);
			}
		}

		private void playerSpellPvpPolicy() {
			check("unteamed players are neutral spell targets",
					GrazeHelper.classifyPlayerSpellTarget(Player.class, false)
							== PlayerDanmakuPolicy.TargetDisposition.NEUTRAL);
			check("teamed players are hostile spell targets",
					GrazeHelper.classifyPlayerSpellTarget(Player.class, true)
							== PlayerDanmakuPolicy.TargetDisposition.HOSTILE);
			check("marked small fairies are hostile spell targets",
					GrazeHelper.classifyPlayerSpellTarget(SmallFairyEntity.class, false)
							== PlayerDanmakuPolicy.TargetDisposition.HOSTILE);
			check("other YH characters are neutral spell targets",
					GrazeHelper.classifyPlayerSpellTarget(RumiaEntity.class, false)
							== PlayerDanmakuPolicy.TargetDisposition.NEUTRAL);
			check("Endermen are hostile spell targets",
					GrazeHelper.classifyPlayerSpellTarget(EnderMan.class, false)
							== PlayerDanmakuPolicy.TargetDisposition.HOSTILE);
			check("zombified piglins are hostile spell targets",
					GrazeHelper.classifyPlayerSpellTarget(ZombifiedPiglin.class, false)
							== PlayerDanmakuPolicy.TargetDisposition.HOSTILE);
			check("empty harmful snapshot blocks an unteamed bystander",
					!IYHDanmaku.canHitPlayerFromSnapshot(true, false));
			check("harmful snapshot allows a teamed opponent",
					IYHDanmaku.canHitPlayerFromSnapshot(true, true));
			check("missing snapshot fails closed for players",
					!IYHDanmaku.canHitPlayerFromSnapshot(false, true));
		}

		private void analyzerTraversal() {
			// repeat x burst x count traversal
			SpellAnalysis amp = SpellAnalyzer.analyze(parse(AMP), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cert repeat x burst x count = 192 per tick", amp.maxSpawnPerTick() == 192);
			check("market repeat x burst x count = 192", SpellAnalyzer.analyze(parse(AMP), SpellAnalysisProfile.MARKET).totalSpawnUpperBound() == 192);
			// outer_count amplification (cert only)
			check("cert amplifies outer_count (576)", SpellAnalyzer.analyze(parse(OUTER), SpellAnalysisProfile.CERTIFICATION, CERT).maxSpawnPerTick() == 576);
			check("market ignores outer_count (192)", SpellAnalyzer.analyze(parse(OUTER), SpellAnalysisProfile.MARKET).totalSpawnUpperBound() == 192);
			// hook recursion: on_trail child actions analyzed, executions counted
			SpellAnalysis trail = SpellAnalyzer.analyze(parse(TRAIL), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cert trail hooks 24 x 12 = 288 per tick", trail.hookExecutionUpperBound() == 288L * 1000);
			check("cert trail hook capability", trail.requiredCapabilities().contains(SpellCapability.HOOK_ON_TRAIL));
			// hook recursion: on_hit_entity with CONTINUE
			SpellAnalysis onHit = SpellAnalyzer.analyze(parse(ON_HIT), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cert CONTINUE on-hit 24 x 4 = 96 per tick", onHit.hookExecutionUpperBound() == 96L * 1000);
			check("cert on-hit capability", onHit.requiredCapabilities().contains(SpellCapability.HOOK_ON_HIT));
			// legacy discovery inside Burst and deep containers (beyond original hasLegacyTicker)
			check("cert rejects legacy inside burst", rejects(() -> SpellAnalyzer.analyze(parse(LEGACY_IN_BURST), SpellAnalysisProfile.CERTIFICATION, CERT)));
			check("cert rejects legacy inside delay>repeat>shooter", rejects(() -> SpellAnalyzer.analyze(parse(LEGACY_DEEP), SpellAnalysisProfile.CERTIFICATION, CERT)));
			// the shared eligibility scan also guards the hash path (SpellHash must reject
			// legacy inside Burst/SpawnShooter containers, not just top-level actions)
			check("hash rejects legacy inside burst", rejects(() -> SpellHash.canonicalHash(parse(LEGACY_IN_BURST))));
			check("hash rejects legacy inside delay>repeat>shooter", rejects(() -> SpellHash.canonicalHash(parse(LEGACY_DEEP))));
			// fail-closed unbounded NumberProvider
			String marketMsg = rejectMessage(() -> SpellAnalyzer.analyze(parse(UNBOUNDED_COUNT), SpellAnalysisProfile.MARKET));
			check("market rejects variable count (literal rule)", marketMsg != null && marketMsg.contains("must be a bounded numeric literal"));
			String certMsg = rejectMessage(() -> SpellAnalyzer.analyze(parse(UNBOUNDED_COUNT), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cert rejects variable count (fail-closed)", certMsg != null && certMsg.contains("cannot be bounded"));
			check("market rejects random count", rejects(() -> SpellAnalyzer.analyze(parse(RANDOM_COUNT), SpellAnalysisProfile.MARKET)));
			check("cert accepts bounded random count (10)",
					SpellAnalyzer.analyze(parse(RANDOM_COUNT), SpellAnalysisProfile.CERTIFICATION, CERT).maxSpawnPerTick() == 10);
			SpellAnalysis movement = SpellAnalyzer.analyze(parse(CASTER_MOVES), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cert accepts caster_moves as a regular action",
					movement.maxSpawnPerTick() == 1 && !movement.requiredCapabilities().contains(SpellCapability.TELEPORT));
			// saturation: no overflow, no negative values, clean rejection
			String satMsg = rejectMessage(() -> SpellAnalyzer.analyze(parse(SAT), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("saturated over-limit rejected cleanly", satMsg != null && satMsg.contains("maxSpawnPerTick"));
			// phase cycle: INFO only, totals bounded by the certification window
			SpellAnalysis cycle = SpellAnalyzer.analyze(parse(CYCLE), SpellAnalysisProfile.CERTIFICATION, CERT);
			boolean hasCycleInfo = cycle.diagnostics().stream()
					.anyMatch(d -> d.severity() == SpellDiagnostic.Severity.INFO && d.code().equals("phase_cycle"));
			check("phase cycle accepted with INFO diagnostic", hasCycleInfo);
			check("cycle totals bounded by window (12 x 1000 = 12000)", cycle.totalSpawnUpperBound() == 12000);
		}

		private void specialCardAndReplicaContracts() {
			var tier1 = SpellCardRank.LESSER_WISDOM;
			check("non-spell accepts bounded discard projectile", !rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_SAFE), tier1)));
			check("non-spell rejects continuing block collision", rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_CONTINUE), tier1)));
			check("non-spell rejects projectile hooks", rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_HOOK), tier1)));
			check("non-spell rejects excessive initial speed", rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_FAST), tier1)));
			check("non-spell accepts bounded homing speed", !rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_HOMING), tier1)));
			check("non-spell rejects acceleration without terminal speed", rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_UNBOUNDED_ACCELERATION), tier1)));
			check("non-spell accepts acceleration with bounded terminal speed", !rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_BOUNDED_ACCELERATION), tier1)));
			check("non-spell rejects laser nodes", rejects(() ->
					NonSpellValidator.validate(parse(NON_SPELL_LASER), tier1)));

			SpellDefinition source = parse(FIRE24);
			source.itemForm = source.itemForm
					.withCardType(dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType.LAST_SPELL)
					.withExSpell(true);
			ResourceLocation copyId = new ResourceLocation("replica_test", "copied");
			SpellDefinition copy = dev.xkmc.youkaishomecoming.content.spell.replica.SpellReplicaService
					.copyDefinition(source, copyId);
			check("replica rewrites root id", copy.id.equals(copyId));
			check("replica rewrites entry and phase ids",
					copy.entryPhase.getNamespace().equals(copyId.getNamespace())
							&& copy.phases.containsKey(copy.entryPhase));
			check("replica output is a normal draft definition",
					copy.itemForm.cardType() == dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType.NORMAL
							&& !copy.itemForm.exSpell());
			check("replica deep copy keeps source identity unchanged",
					source.id.equals(new ResourceLocation("youkaishomecoming", "analyzer_test")));
		}

		private void projectionModel() {
			SpellAnalysis periodic = analysis(PERIODIC, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("tick_interval uses five matching ticks in a 1000-tick window",
					periodic != null && periodic.totalSpawnUpperBound() == 5,
					lastError != null ? lastError : "actual total=" + (periodic == null ? "null" : periodic.totalSpawnUpperBound()));
			check("periodic peak uses the action lifetime and cadence",
					periodic != null && periodic.peakAliveUpperBound() == 1,
					lastError != null ? lastError : "actual peak=" + (periodic == null ? "null" : periodic.peakAliveUpperBound()));
			check("periodic projectile ticks use five spawns x sixty ticks",
					periodic != null && periodic.projectileTicks() == 300,
					lastError != null ? lastError : "actual ticks=" + (periodic == null ? "null" : periodic.projectileTicks()));

			SpellAnalysis eventDriven = analysis(EVENT_DRIVEN, SpellAnalysisProfile.CERTIFICATION,
					CERT.withCertificationWindow(500));
			check("event-driven variable hook is projected over callback executions",
					eventDriven != null && eventDriven.hookExecutionUpperBound() == 12,
					lastError != null ? lastError : "actual hooks=" + (eventDriven == null ? "null" : eventDriven.hookExecutionUpperBound()));
			check("event-driven repeat is not charged once per phase tick",
					eventDriven != null && eventDriven.totalSpawnUpperBound() == 963,
					lastError != null ? lastError : "actual total=" + (eventDriven == null ? "null" : eventDriven.totalSpawnUpperBound()));
			SpellAnalysis withoutReset = analysis(EVENT_WITHOUT_RESET, SpellAnalysisProfile.CERTIFICATION,
					CERT.withCertificationWindow(500));
			check("event variable without a consuming reset remains conservative",
					withoutReset != null && withoutReset.totalSpawnUpperBound() == 40_003,
					lastError != null ? lastError : "actual total=" + (withoutReset == null ? "null" : withoutReset.totalSpawnUpperBound()));

			SpellAnalysis bounce = analysis(BOUNCE_HIT, SpellAnalysisProfile.CERTIFICATION,
					CERT.withCertificationWindow(1));
			check("default CONTINUE does not hide a max_bounces callback bound",
					bounce != null && bounce.hookExecutionUpperBound() == 31,
					lastError != null ? lastError : "actual hooks=" + (bounce == null ? "null" : bounce.hookExecutionUpperBound()));
		}

		/** One-shot spawn bursts (on_enter/on_exit) must count toward maxSpawnPerTick (issue 2),
		 * and on_enter + on_tick share the phase-entry tick, so they are summed (issue 3). */
		private void oneShotBurst() {
			SpellAnalysis analysis = SpellAnalyzer.analyze(parse(ONE_SHOT_BURST), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("phase-entry tick sums on_enter + on_tick (1000 + 6 = 1006)",
					analysis.maxSpawnPerTick() == 1006);
			check("one-shot burst total = 1000 + 6 x 1000", analysis.totalSpawnUpperBound() == 7000);
			check("one-shot burst peak = 1000 + 6 x min(60, window) = 1360", analysis.peakAliveUpperBound() == 1360);
		}

		/** Shooter independent model: recurring shooters scale with alive cohort × window
		 * and never underestimate; one-shot shooters scale with lifetime only (issue 2). */
		private void shooterModel() {
			// on_tick: 3 shooters/tick, lifetime 100, body fire 4/tick
			//   shooterPeak = 3 x 100 = 300; bodyPerGlobalTick = 300 x 4 = 1200
			//   bodyTotal = 1200 x window 1000 = 1,200,000
			//   maxSpawnPerTick = 1200 body + 3 recurring entities = 1203
			//   peakAlive = 300 shooters + 1200 x min(60, window) = 72,300 (no window total)
			SpellAnalysis shooter = analysis(SHOOTER, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("recurring shooter maxSpawnPerTick = 1200 body + 3 entities = 1203",
					shooter != null && shooter.maxSpawnPerTick() == 1203,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + shooter.maxSpawnPerTick());
			check("recurring shooter total = 1200 x 1000 = 1,200,000",
					shooter != null && shooter.totalSpawnUpperBound() == 1_200_000,
					lastError != null ? lastError : "actual total=" + shooter.totalSpawnUpperBound());
			check("recurring shooter peak = 300 + 72,000 = 72,300 (no window total in peak)",
					shooter != null && shooter.peakAliveUpperBound() == 72_300,
					lastError != null ? lastError : "actual peak=" + shooter.peakAliveUpperBound());
			// on_enter: same shooter fired once — body scales with lifetime only
			//   maxSpawnPerTick = 3 entities + 12 body = 15 (conservative same-tick)
			//   peakAlive = 3 + 12 x min(60, window) = 723 (no lifetime total in peak)
			SpellAnalysis enter = analysis(ENTER_SHOOTER, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("one-shot shooter maxSpawnPerTick = 3 entities + 12 body = 15",
					enter != null && enter.maxSpawnPerTick() == 15,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (enter == null ? "null" : enter.maxSpawnPerTick()));
			check("one-shot shooter total = 3 x 4 x 100 = 1200",
					enter != null && enter.totalSpawnUpperBound() == 1200,
					lastError != null ? lastError : "actual total=" + (enter == null ? "null" : enter.totalSpawnUpperBound()));
			check("one-shot shooter peak = 3 + 720 = 723 (no lifetime total in peak)",
					enter != null && enter.peakAliveUpperBound() == 723,
					lastError != null ? lastError : "actual peak=" + (enter == null ? "null" : enter.peakAliveUpperBound()));
			SpellAnalysis market = analysis(SHOOTER, SpellAnalysisProfile.MARKET, SpellAnalysisLimits.market());
			check("market shooter: 3 shooters, 12 projectiles",
					market != null && market.totalSpawnUpperBound() == 12,
					lastError != null ? lastError : "actual total=" + (market == null ? "null" : market.totalSpawnUpperBound()));
		}

		/** Top-level on_tick spawns and alive shooter bodies fire in the same tick:
		 * they must be summed for maxSpawnPerTick (issue 3). */
		private void sameTickSum() {
			// on_tick: fire 400 + spawn_shooter 1 (lifetime 100, body fire 4)
			//   bodyPerGlobalTick = 1 x 100 x 4 = 400; ordinary = 400 + 400 + 1 entity = 801
			SpellAnalysis analysis = this.analysis(SAME_TICK, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("top-level + shooter body + entity same tick summed (400 + 400 + 1 = 801)",
					analysis != null && analysis.maxSpawnPerTick() == 801,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (analysis == null ? "null" : analysis.maxSpawnPerTick()));
		}

		/** Two parallel recurring shooters must sum their concurrent peaks (issue 4). */
		private void parallelShooters() {
			// on_tick: shooter A (3/tick, lifetime 100, body 4) + shooter B (same)
			//   certPeakShooters = 300 + 300 = 600
			//   bodyPerGlobalTick = 1200 + 1200 = 2400
			//   peakAlive = 600 shooters + 2400 x min(60, window) = 600 + 144,000 = 144,600
			SpellAnalysis analysis = this.analysis(PARALLEL_SHOOTERS, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("parallel shooters peak sums concurrency (600 + 144,000 = 144,600)",
					analysis != null && analysis.peakAliveUpperBound() == 144_600,
					lastError != null ? lastError : "actual peak=" + (analysis == null ? "null" : analysis.peakAliveUpperBound()));
		}

		/** Two identical-lifetime expiry batches in one group expire together; their
		 * deferred bursts must sum (issue 5). Round-6 full-sum model: direct root
		 * (2 x 1000) + deferred (20,000) are all added. */
		private void parallelExpiryBursts() {
			// on_enter: fire A (1000, on_expiry 10) + fire B (1000, on_expiry 10)
			//   direct = 2000; deferred = 20,000; full sum = 22,000
			SpellAnalysis analysis = this.analysis(PARALLEL_EXPIRY, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("parallel expiry full sum (direct 2000 + deferred 20000 = 22000)",
					analysis != null && analysis.maxSpawnPerTick() == 22_000,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (analysis == null ? "null" : analysis.maxSpawnPerTick()));
		}

		/** A deferred one-shot batch overlaps the ordinary on_tick burst; round-6 model
		 * also adds the direct root burst (review A1). */
		private void deferredPlusOrdinary() {
			// on_enter: 1000 direct + 10,000 deferred; on_tick: 400 ordinary
			//   full sum = 400 + 1000 + 0 + 10,000 = 11,400
			SpellAnalysis analysis = this.analysis(DEFERRED_ORDINARY, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("deferred + direct + ordinary full sum (400 + 1000 + 10000 = 11400)",
					analysis != null && analysis.maxSpawnPerTick() == 11_400,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (analysis == null ? "null" : analysis.maxSpawnPerTick()));
		}

		/** Immediate containers inherit the outer execution group; round-6 full sum
		 * includes the direct root burst (review A2). */
		private void nestedExpiry() {
			// on_enter: Sequence[Fire A 1000 expiry 10] + Fire B 1000 expiry 10
			//   direct = 2000; deferred = 20,000; full sum = 22,000
			SpellAnalysis analysis = this.analysis(NESTED_EXPIRY, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("nested expiry full sum (direct 2000 + deferred 20000 = 22000)",
					analysis != null && analysis.maxSpawnPerTick() == 22_000,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (analysis == null ? "null" : analysis.maxSpawnPerTick()));
		}

		/** delay <= 0 executes immediately (round 6, issue 1). */
		private void zeroDelay() {
			SpellAnalysis plain = this.analysis(ZERO_DELAY_IMMEDIATE, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("delay 0 joins entry burst (500 + 500 = 1000)",
					plain != null && plain.maxSpawnPerTick() == 1000,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (plain == null ? "null" : plain.maxSpawnPerTick()));
			SpellAnalysis withTick = this.analysis(ZERO_DELAY_IMMEDIATE_TICK, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("delay 0 + ordinary on_tick (1000 + 100 = 1100)",
					withTick != null && withTick.maxSpawnPerTick() == 1100,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (withTick == null ? "null" : withTick.maxSpawnPerTick()));
		}

		/** Positive delay and expiry batch can share a tick (round 6, issue 2). */
		private void delayDeferredMerge() {
			// on_enter: fire 1 (expiry 10,000) + delay 60 -> fire 500
			//   full sum = 0 ordinary + 1 direct + 500 delayed + 10,000 deferred = 10,501
			SpellAnalysis analysis = this.analysis(DELAY_DEFERRED_MERGE, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("delay + deferred full sum (1 + 500 + 10000 = 10501)",
					analysis != null && analysis.maxSpawnPerTick() == 10_501,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (analysis == null ? "null" : analysis.maxSpawnPerTick()));
		}

		/** Cyclic phases re-run on_enter/on_exit every transition tick; certification
		 * fails closed when they spawn (round 7, plan B). */
		private void cyclicPhaseSpawns() {
			String direct = rejectMessage(() -> SpellAnalyzer.analyze(parse(CYCLIC_ENTER_DIRECT), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cyclic on_enter direct spawn rejected", direct != null && direct.contains("cyclic_phase_spawn"));
			check("cyclic on_enter spawn rejected also for MARKET profile",
					!rejects(() -> SpellAnalyzer.analyze(parse(CYCLIC_ENTER_DIRECT), SpellAnalysisProfile.MARKET)));
			String hook = rejectMessage(() -> SpellAnalyzer.analyze(parse(CYCLIC_ENTER_HOOK), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cyclic on_enter hook spawn rejected", hook != null && hook.contains("cyclic_phase_spawn"));
			String shooter = rejectMessage(() -> SpellAnalyzer.analyze(parse(CYCLIC_ENTER_SHOOTER), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cyclic on_enter shooter rejected", shooter != null && shooter.contains("cyclic_phase_spawn"));
			// spawn-free cyclic enter/exit still passes (on_tick cycles unchanged)
			SpellAnalysis safe = SpellAnalyzer.analyze(parse(CYCLIC_ENTER_SAFE), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cyclic spawn-free on_enter passes", safe != null);
			// cross-edge SCC member must be detected (round 8)
			String branch = rejectMessage(() -> SpellAnalyzer.analyze(parse(CYCLIC_BRANCH_MEMBER), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cross-edge SCC member rejected", branch != null && branch.contains("cyclic_phase_spawn"));
			check("cross-edge SCC member accepted by MARKET (historical)",
					!rejects(() -> SpellAnalyzer.analyze(parse(CYCLIC_BRANCH_MEMBER), SpellAnalysisProfile.MARKET)));
			// self-loop is a cycle (round 8)
			String selfLoop = rejectMessage(() -> SpellAnalyzer.analyze(parse(CYCLIC_SELF_LOOP), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("self-loop spawn rejected", selfLoop != null && selfLoop.contains("cyclic_phase_spawn"));
			// disabled spawn inside a cycle never executes: still passes (round 8)
			SpellAnalysis disabled = SpellAnalyzer.analyze(parse(CYCLIC_ENTER_DISABLED), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cyclic disabled spawn passes (never executes)", disabled != null);
		}

		/** Equal delays run in the same scheduled tick; bursts must sum (round 5 A1). */
		private void parallelDelays() {
			SpellAnalysis plain = this.analysis(PARALLEL_DELAY, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("equal delays sum (400 + 400 = 800)",
					plain != null && plain.maxSpawnPerTick() == 800,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (plain == null ? "null" : plain.maxSpawnPerTick()));
			SpellAnalysis withTick = this.analysis(PARALLEL_DELAY_TICK, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("equal delays + ordinary on_tick (800 + 100 = 900)",
					withTick != null && withTick.maxSpawnPerTick() == 900,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (withTick == null ? "null" : withTick.maxSpawnPerTick()));
		}

		/** on_exit + on_enter run in the same transition tick; bursts must sum (round 5 A2). */
		private void transitionBurst() {
			SpellAnalysis plain = this.analysis(TRANSITION_BURST, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("transition tick sums on_exit + on_enter (500 + 500 = 1000)",
					plain != null && plain.maxSpawnPerTick() == 1000,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (plain == null ? "null" : plain.maxSpawnPerTick()));
			SpellAnalysis withTick = this.analysis(TRANSITION_BURST_TICK, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("transition tick + ordinary on_tick (1000 + 100 = 1100)",
					withTick != null && withTick.maxSpawnPerTick() == 1100,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (withTick == null ? "null" : withTick.maxSpawnPerTick()));
		}

		/** A batch of identical-lifetime one-shot projectiles can expire together; the
		 * whole on_expiry batch fires in one tick and must be tracked (issue 4).
		 * Round-6 full sum: direct root (1000) + deferred (10,000). */
		private void deferredHookBurst() {
			// on_enter: fire 1000 (lifetime 60) with on_expiry [fire 10]
			//   executions = 1000; child spawns = 1000 x 10 = 10,000 in one tick
			//   full sum = 0 ordinary + 1000 direct + 0 delay + 10,000 deferred = 11,000
			SpellAnalysis analysis = this.analysis(EXPIRY_BURST, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("deferred hook full sum (direct 1000 + deferred 10000 = 11000)",
					analysis != null && analysis.maxSpawnPerTick() == 11_000,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (analysis == null ? "null" : analysis.maxSpawnPerTick()));
			check("deferred hook executions = 1000 (once)",
					analysis != null && analysis.hookExecutionUpperBound() == 1000,
					lastError != null ? lastError : "actual hooks=" + (analysis == null ? "null" : analysis.hookExecutionUpperBound()));
		}

		/** Hook child multiplier must not square the parent factor (issue 4). */
		private void hookMultiplier() {
			// repeat 10 -> fire count 2 (contrib 20/tick) -> on_hit_entity fire count 1
			SpellAnalysis analysis = this.analysis(HOOK_REPEAT, SpellAnalysisProfile.CERTIFICATION, CERT);
			check("hook executions = 20/tick (no squaring)",
					analysis != null && analysis.hookExecutionUpperBound() == 20L * 1000,
					lastError != null ? lastError : "actual hooks=" + (analysis == null ? "null" : analysis.hookExecutionUpperBound()));
			check("hook child spawns = 20/tick, total = (20+20) x 1000 = 40000",
					analysis != null && analysis.totalSpawnUpperBound() == 40000,
					lastError != null ? lastError : "actual total=" + (analysis == null ? "null" : analysis.totalSpawnUpperBound()));
			check("hook multiplier maxSpawnPerTick = 40",
					analysis != null && analysis.maxSpawnPerTick() == 40,
					lastError != null ? lastError : "actual maxSpawnPerTick=" + (analysis == null ? "null" : analysis.maxSpawnPerTick()));
		}

		private void profileDifferences() {
			// MARKET keeps historical disabled penetration scan
			String marketMsg = rejectMessage(() -> SpellAnalyzer.analyze(parse(RUNCMD_DISABLED), SpellAnalysisProfile.MARKET));
			check("market penetrates disabled for banned actions (historical)", marketMsg != null
					&& marketMsg.equals("Automatic market imports may not use action: run_command"));
			// CERTIFICATION fully skips permanent disabled nodes
			SpellAnalysis certDisabled = SpellAnalyzer.analyze(parse(RUNCMD_DISABLED), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cert skips disabled subtree", certDisabled != null && !certDisabled.requiredCapabilities().contains(SpellCapability.RUN_COMMAND));
			// historical market banned text verbatim
			String banned = rejectMessage(() -> SpellAnalyzer.analyze(parse(RUNCMD), SpellAnalysisProfile.MARKET));
			check("market banned text verbatim", banned != null && banned.equals("Automatic market imports may not use action: run_command"));
			SpellAnalysis certHealth = SpellAnalyzer.analyze(
					parse(SPELL_HEALTH), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("cert analyzer permits health declaration after plan validation", certHealth != null);
			SpellAnalysis opHealth = SpellAnalyzer.analyzeOperatorTest(parse(SPELL_HEALTH), CERT);
			check("operator test permits spell health", opHealth != null);
			String forcePhaseSpell = spell("{\"type\": \"force_phase\", "
					+ "\"phase_id\": \"youkaishomecoming:main\"}");
			String certForcePhase = rejectMessage(() -> SpellAnalyzer.analyze(
					parse(forcePhaseSpell), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cert rejects operator-only force_phase",
					certForcePhase != null && certForcePhase.contains("capability force_phase"));
			SpellAnalysis opForcePhase = SpellAnalyzer.analyzeOperatorTest(parse(forcePhaseSpell), CERT);
			check("operator test permits force_phase",
					opForcePhase.requiredCapabilities().contains(SpellCapability.FORCE_PHASE));
			String marketForcePhase = rejectMessage(() -> SpellAnalyzer.analyze(
					parse(forcePhaseSpell), SpellAnalysisProfile.MARKET));
			check("market rejects force_phase",
					"Automatic market imports may not use action: force_phase".equals(marketForcePhase));
			// 20 stable capability IDs
			check("20 stable capability IDs", SpellCapability.values().length == 20
					&& Set.of(SpellCapability.values()).stream().map(SpellCapability::id).distinct().count() == 20);
			// certification extracts the expected capability set from a comprehensive fixture
			SpellAnalysis caps = SpellAnalyzer.analyze(parse(ALL_CAPS), SpellAnalysisProfile.MARKET);
			var capsSet = caps.requiredCapabilities();
			check("capability extraction (all-caps fixture)", capsSet.contains(SpellCapability.BASE_FIRE)
					&& capsSet.contains(SpellCapability.HOOK_ON_HIT)
					&& capsSet.contains(SpellCapability.ORIGIN_TARGET)
					&& capsSet.contains(SpellCapability.TELEPORT)
					&& capsSet.contains(SpellCapability.SET_ENTITY_FLAG)
					&& capsSet.contains(SpellCapability.YSM_RENDER)
					&& capsSet.contains(SpellCapability.ERASE_ENEMY_DANMAKU)
					&& capsSet.contains(SpellCapability.CLEAR_SCREEN)
					&& capsSet.contains(SpellCapability.CONFINED_TARGET)
					&& capsSet.contains(SpellCapability.SET_SPELL_CIRCLE)
					&& capsSet.contains(SpellCapability.SHOW_SPELL_TITLE)
					&& capsSet.contains(SpellCapability.BOSS_ON_DAMAGE));
			// force_spell / fire_spell are market-banned: they must be rejected with the
			// verbatim historical message, not extracted as capabilities
			String forceMsg = rejectMessage(() -> SpellAnalyzer.analyze(parse(
					spell("{\"type\": \"force_spell\", \"spell_id\": \"youkaishomecoming:x\"}")), SpellAnalysisProfile.MARKET));
			check("market rejects force_spell verbatim", forceMsg != null
					&& forceMsg.equals("Automatic market imports may not use action: force_spell"));
			String fireSpellMsg = rejectMessage(() -> SpellAnalyzer.analyze(parse(
					spell("{\"type\": \"fire_spell\", \"spell_id\": \"youkaishomecoming:x\"}")), SpellAnalysisProfile.MARKET));
			check("market rejects fire_spell verbatim", fireSpellMsg != null
					&& fireSpellMsg.equals("Automatic market imports may not use action: fire_spell"));
			// same definition across profiles: only expected differences
			SpellAnalysis marketTeleport = SpellAnalyzer.analyze(parse(TELEPORT), SpellAnalysisProfile.MARKET);
			String certTeleport = rejectMessage(() -> SpellAnalyzer.analyze(parse(TELEPORT), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("teleport: market passes with capability, cert rejects on policy",
					marketTeleport.requiredCapabilities().contains(SpellCapability.TELEPORT)
							&& certTeleport != null && certTeleport.contains("capability teleport"));
			SpellAnalysis marketHook = SpellAnalyzer.analyze(parse(TRAIL), SpellAnalysisProfile.MARKET);
			SpellAnalysis certHook = SpellAnalyzer.analyze(parse(TRAIL), SpellAnalysisProfile.CERTIFICATION, CERT);
			check("hook spell: market flat (24 + 1 trail child) vs cert amplified",
					marketHook.totalSpawnUpperBound() == 25 && certHook.totalSpawnUpperBound() > 25
							&& marketHook.hookExecutionUpperBound() == 0 && certHook.hookExecutionUpperBound() > 0);
		}

		private void disabledSemantics() {
			// DisabledAction cannot be revived at runtime: execute() is a structural no-op.
			// Constructed from a parsed definition and executed with a null context.
			boolean noop = true;
			try {
				SpellAction inner = firstTickAction(parse(FIRE24));
				new SpellActions.DisabledAction(inner).execute(null);
			} catch (Exception e) {
				noop = false;
			}
			check("DisabledAction.execute is a runtime no-op", noop);
		}

		private void marketFacade() {
			String json = FIRE24;
			SpellMarketValidator.validate(json, JsonParser.parseString(json), parse(json));
			check("market facade accepts valid definition", true);
			String banned = RUNCMD;
			String msg = rejectMessage(() -> SpellMarketValidator.validate(banned, JsonParser.parseString(banned), parse(banned)));
			check("market facade rejects banned action verbatim", msg != null
					&& msg.equals("Automatic market imports may not use action: run_command"));
			// historical raw-JSON guard: bare numeric lifetime above the cap is rejected
			// verbatim even though it decodes to a NumberProvider object (issue 1a)
			String lifetimeMsg = rejectMessage(() -> SpellMarketValidator.validate(LONG_LIFETIME, JsonParser.parseString(LONG_LIFETIME), parse(LONG_LIFETIME)));
			check("market rejects bare lifetime 20000 verbatim", lifetimeMsg != null
					&& lifetimeMsg.equals("lifetime exceeds 12000"));
			// object-form lifetime passes market historically (guard checks only raw numbers)
			SpellMarketValidator.validate(OBJ_LIFETIME, JsonParser.parseString(OBJ_LIFETIME), parse(OBJ_LIFETIME));
			check("market accepts object-form lifetime (historical behavior)", true);
			// certification still rejects it: bounded but above the hard cap
			String certLifetime = rejectMessage(() -> SpellAnalyzer.analyze(parse(OBJ_LIFETIME), SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cert rejects object-form lifetime above cap", certLifetime != null
					&& certLifetime.contains("lifetime exceeds 12000"));
			// banned action inside disabled > fire > on_hit cannot hide (issue 1d)
			String hiddenMsg = rejectMessage(() -> SpellMarketValidator.validate(DISABLED_HOOK, JsonParser.parseString(DISABLED_HOOK), parse(DISABLED_HOOK)));
			check("market rejects banned action inside disabled>on_hit", hiddenMsg != null
					&& hiddenMsg.contains("may not use action: run_command"));
			// golden historical messages (raw-JSON guard)
			String nestMsg = rejectMessage(() -> SpellMarketValidator.validate(DEEP_NEST, JsonParser.parseString(DEEP_NEST), parse(DEEP_NEST)));
			check("market nesting message verbatim", nestMsg != null && nestMsg.equals("Spell nesting exceeds 32"));
			String strMsg = rejectMessage(() -> SpellMarketValidator.validate(LONG_STR, JsonParser.parseString(LONG_STR), parse(LONG_STR)));
			check("market string length message verbatim", strMsg != null && strMsg.equals("Spell string/expression exceeds 512 characters"));
			// analyzer structural messages preserved through the facade
			String phaseMsg = rejectMessage(() -> SpellMarketValidator.validate(EMPTY_PHASES, JsonParser.parseString(EMPTY_PHASES), parse(EMPTY_PHASES)));
			check("market phase count message verbatim", phaseMsg != null && phaseMsg.equals("Spell phase count must be between 1 and 64"));
			// action budget parity: many bare numeric fields must not inflate the count (issue 1b)
			SpellMarketValidator.validate(MANY_ACTIONS, JsonParser.parseString(MANY_ACTIONS), parse(MANY_ACTIONS));
			check("market accepts 500 actions with many bare numbers (action budget parity)", true);
		}

		/** Unknown actions fail closed in certification (issue 6). */
		private void unknownAction() {
			SpellDefinition def = parse(FIRE24);
			PhaseDefinition phase = def.phases.values().iterator().next();
			phase.onTick.add(new UnknownAction());
			String msg = rejectMessage(() -> SpellAnalyzer.analyze(def, SpellAnalysisProfile.CERTIFICATION, CERT));
			check("cert rejects unknown action", msg != null && msg.contains("Unsupported action in certification"));
		}

		/** Map insertion order (phases, custom names) must not affect the hash (issue 8). */
		private void hashOrderIndependence() {
			SpellDefinition def = parse(CYCLE);
			Map<ResourceLocation, PhaseDefinition> reversed = new LinkedHashMap<>();
			List<ResourceLocation> ids = new ArrayList<>(def.phases.keySet());
			java.util.Collections.reverse(ids);
			for (ResourceLocation id : ids) {
				reversed.put(id, def.phases.get(id));
			}
			SpellDefinition reordered = new SpellDefinition(def.id, def.display, def.itemForm,
					def.entryPhase, reversed, def.difficulty, def.customNames);
			check("hash independent of phase map insertion order",
					SpellHash.canonicalHash(def).equals(SpellHash.canonicalHash(reordered)));
			Map<String, String> namesA = new LinkedHashMap<>();
			namesA.put("x", "1");
			namesA.put("y", "2");
			Map<String, String> namesB = new LinkedHashMap<>();
			namesB.put("y", "2");
			namesB.put("x", "1");
			SpellDefinition a = new SpellDefinition(def.id, def.display, def.itemForm,
					def.entryPhase, def.phases, def.difficulty, namesA);
			SpellDefinition b = new SpellDefinition(def.id, def.display, def.itemForm,
					def.entryPhase, def.phases, def.difficulty, namesB);
			check("hash independent of custom_names insertion order",
					SpellHash.canonicalHash(a).equals(SpellHash.canonicalHash(b)));
		}

		/** Test-only action with no analyzer support; certification must reject it. */
		private static final class UnknownAction implements SpellAction {
			@Override
			public void execute(SpellContext ctx) {
			}
		}

		private void healthPlanValidation() {
			SpellDefinition differentTargets = SpellTemplates.create(
					new ResourceLocation("youkaishomecoming", "health_different_targets"), "boss");
			PhaseDefinition intro = differentTargets.getPhase(differentTargets.entryPhase);
			SetSpellHealthAction introHealth = (SetSpellHealthAction) intro.onEnter.get(0);
			intro.onEnter.set(0, new SetSpellHealthAction(introHealth.mode(), introHealth.health(),
					introHealth.duration(), introHealth.onTimeout(), java.util.Optional.empty()));
			SpellHealthPlan differentPlan = SpellHealthPlan.analyze(differentTargets, id -> null);
			check("health plan allows timeout and break targets to differ",
					differentPlan.breakChain().size() == 1
							&& differentPlan.totalHealth() == 500
							&& differentPlan.totalDurationTicks() == 400);

			SpellDefinition timeoutCycle = SpellTemplates.create(
					new ResourceLocation("youkaishomecoming", "health_timeout_cycle"), "boss");
			PhaseDefinition timeoutIntro = timeoutCycle.getPhase(timeoutCycle.entryPhase);
			SetSpellHealthAction timeoutHealth = (SetSpellHealthAction) timeoutIntro.onEnter.get(0);
			timeoutIntro.onEnter.set(0, new SetSpellHealthAction(timeoutHealth.mode(), timeoutHealth.health(),
					timeoutHealth.duration(), java.util.Optional.of(new SpellActions.ForcePhase(
						timeoutCycle.entryPhase, true)), timeoutHealth.onBreak()));
			String timeoutCycleMessage = rejectMessage(() -> SpellHealthPlan.analyze(timeoutCycle, id -> null));
			check("health plan rejects timeout cycles",
					timeoutCycleMessage != null && timeoutCycleMessage.contains("cycle"));

			SpellDefinition breakCycle = SpellTemplates.create(
					new ResourceLocation("youkaishomecoming", "health_break_cycle"), "boss");
			PhaseDefinition breakIntro = breakCycle.getPhase(breakCycle.entryPhase);
			SetSpellHealthAction breakHealth = (SetSpellHealthAction) breakIntro.onEnter.get(0);
			breakIntro.onEnter.set(0, new SetSpellHealthAction(breakHealth.mode(), breakHealth.health(),
					breakHealth.duration(), breakHealth.onTimeout(), java.util.Optional.of(
						new SpellActions.ForcePhase(breakCycle.entryPhase, true))));
			String breakCycleMessage = rejectMessage(() -> SpellHealthPlan.analyze(breakCycle, id -> null));
			check("health plan rejects break cycles",
					breakCycleMessage != null && breakCycleMessage.contains("cycle"));

			SpellDefinition crossA = crossHealthSpell("youkaishomecoming:health_cross_a",
					"youkaishomecoming:health_cross_b");
			SpellDefinition crossB = crossHealthSpell("youkaishomecoming:health_cross_b",
					"youkaishomecoming:health_cross_a");
			Map<ResourceLocation, SpellDefinition> crossDefinitions = Map.of(crossA.id, crossA, crossB.id, crossB);
			String crossCycleMessage = rejectMessage(() -> SpellHealthPlan.analyze(crossA, crossDefinitions::get));
			check("health plan rejects cross-spell cycles",
					crossCycleMessage != null && crossCycleMessage.contains("cycle"));

			SpellDefinition relayA = crossHealthSpell("youkaishomecoming:health_relay_a",
					"youkaishomecoming:health_relay_b");
			SpellDefinition relayB = terminalHealthSpell("youkaishomecoming:health_relay_b");
			Map<ResourceLocation, SpellDefinition> relayDefinitions = Map.of(
					relayA.id, relayA, relayB.id, relayB);
			SpellHealthPlan relayPlan = SpellHealthPlan.analyze(relayA, relayDefinitions::get);
			SpellRuntime relayRuntime = new SpellRuntime(relayA, relayPlan::resolve, relayPlan);
			relayRuntime.setSpellHealth(10, 100);
			SpellRuntime relayedRuntime = relayRuntime.continueWith(relayB);
			relayedRuntime.setSpellHealth(20, 200);
			check("force_spell preserves ordered health progress",
					java.util.Arrays.equals(relayedRuntime.getSpellHealthSegments(), new int[]{10, 20})
							&& relayedRuntime.getSpellHealthCompleted() == 10
							&& relayedRuntime.getSpellDurationTicks() == 200);

			SpellDefinition dynamic = SpellTemplates.create(
					new ResourceLocation("youkaishomecoming", "health_dynamic"), "boss");
			PhaseDefinition dynamicIntro = dynamic.getPhase(dynamic.entryPhase);
			SetSpellHealthAction dynamicHealth = (SetSpellHealthAction) dynamicIntro.onEnter.get(0);
			dynamicIntro.onEnter.set(0, new SetSpellHealthAction(dynamicHealth.mode(),
					new dev.xkmc.youkaishomecoming.content.spell.definition.NumberProviders.Variable("hp"),
					dynamicHealth.duration(), dynamicHealth.onTimeout(), dynamicHealth.onBreak()));
			String dynamicMessage = rejectMessage(() -> SpellHealthPlan.analyze(dynamic, id -> null));
			check("health plan rejects dynamic health",
					dynamicMessage != null && dynamicMessage.contains("must be constants"));
		}

		private SpellDefinition crossHealthSpell(String id, String targetSpell) {
			ResourceLocation spellId = new ResourceLocation(id);
			String phaseId = spellId + "/main";
			return parse("{\"id\":\"" + spellId + "\",\"display\":{\"name\":\"Cross\"},"
					+ "\"entry_phase\":\"" + phaseId + "\",\"phases\":{\"" + phaseId + "\":{"
					+ "\"id\":\"" + phaseId + "\",\"on_enter\":[{\"type\":\"set_spell_health\","
					+ "\"health\":10,\"duration\":100,\"on_break\":{\"type\":\"force_spell\","
					+ "\"spell_id\":\"" + targetSpell + "\"}}]}}}");
		}

		private SpellDefinition terminalHealthSpell(String id) {
			ResourceLocation spellId = new ResourceLocation(id);
			String phaseId = spellId + "/main";
			return parse("{\"id\":\"" + spellId + "\",\"display\":{\"name\":\"Relay\"},"
					+ "\"entry_phase\":\"" + phaseId + "\",\"phases\":{\"" + phaseId + "\":{"
					+ "\"id\":\"" + phaseId + "\",\"on_enter\":[{\"type\":\"set_spell_health\","
					+ "\"health\":20,\"duration\":200}]}}}");
		}

		/** Phase 7 sanity: the quote pipeline produces a positive, clamped cost for a
		 * certification-eligible definition. Requires a player (command context). */
		private void certificationQuoteSanity(net.minecraft.server.level.ServerPlayer player) {
			SpellDefinition def = SpellTemplates.create(
					new ResourceLocation("youkaishomecoming", "quote_health_plan"), "boss");
			dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote q =
					dev.xkmc.youkaishomecoming.content.spell.certification.CertificationService.quote(player, def);
			check("quote reads total break-chain duration", q.durationTicks() == 1200);
			check("quote reads total break-chain health", q.spellHp() == 1300);
			check("quote keeps reward duration at 1:1", q.rewardDurationTicks() == 1200);
			check("quote uses configured fixed arena",
					q.arenaHalfSize() == dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON.certificationFixedArenaHalfSize.get());
			check("quote uses configured start fee",
					q.startCostUnits() == dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON.certificationStartCostUnits.get());
			check("quote bundle hash stable", q.definitionHash().equals(
					SpellHash.canonicalBundleHash(q.healthPlan().definitions())));
		}

		@FunctionalInterface
		private interface ThrowingRunnable {
			void run() throws Exception;
		}
	}
}
